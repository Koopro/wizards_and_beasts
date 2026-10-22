package at.koopro.wizardsandbeasts.artefact.erised;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.LocationText;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.loot.SpellSites;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningEligibility;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the Mirror could point a player toward: things they have not found that they could reach today.
 *
 * <p>"Not found" is always the difference between everything there is and what the player's own records hold --
 * no system keeps that list for us. "Reachable" is checked here, at the moment of the gaze, so a clue is never a
 * dead end:
 * <ul>
 *   <li><b>creature</b> -- a bestiary page below Observed, for a species that spawns on its own in a biome some
 *       loaded dimension can generate (the clue names that biome);</li>
 *   <li><b>spell</b> -- one the player does not know, may learn, and that a spellbook loot pool holds (the clue
 *       names the kind of place, from {@link SpellSites});</li>
 *   <li><b>floo</b> -- a fireplace on the network the player may travel to but never has;</li>
 *   <li><b>place</b> -- an open location the player has never dealt with.</li>
 * </ul>
 */
@NullMarked
public final class ErisedClues {

    private static final String KEY = "erised." + WizardsAndBeastsMod.MODID + ".";

    private ErisedClues() {}

    public static List<ClueCandidate> candidates(ServerPlayer player) {
        List<ClueCandidate> out = new ArrayList<>();
        creatures(player, out);
        spells(player, out);
        floo(player, out);
        places(player, out);
        return out;
    }

    static void creatures(ServerPlayer player, List<ClueCandidate> out) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) {
            return;
        }
        Map<EntityType<?>, Identifier> habitat = null;
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            if (entry.entityType().isEmpty()
                    || BestiaryDataHelper.getTier(player, entry.id()).atLeast(DiscoveryTier.OBSERVED)) {
                continue;
            }
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.entityType().get());
            if (type.isEmpty() || (isOurs(entry.entityType().get()) && !ModuleManager.isEnabled(Module.CREATURES))) {
                continue;
            }
            if (habitat == null) {
                habitat = habitats(player.level());
            }
            Identifier biome = habitat.get(type.get());
            if (biome != null) {
                out.add(new ClueCandidate(ClueCandidate.Kind.CREATURE, entry.id().toString(),
                        Component.translatable(KEY + "clue.creature", Component.translatable(entry.displayNameKey()),
                                Component.translatable(biome.toLanguageKey("biome")))));
            }
        }
    }

    /**
     * For every entity type that spawns naturally, the first (by id) biome it spawns in, over the biomes that the
     * server's dimensions -- the pocket realm aside -- can actually generate. Biome modifiers are already applied
     * to {@link Biome#getMobSettings()} by the time a player can gaze.
     */
    static Map<EntityType<?>, Identifier> habitats(ServerLevel anyLevel) {
        Map<EntityType<?>, Identifier> out = new HashMap<>();
        for (ServerLevel level : anyLevel.getServer().getAllLevels()) {
            if (level.dimension().equals(ModDimensions.EXTENSION_REALM)) {
                continue;
            }
            for (Holder<Biome> biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                Identifier biomeId = biome.unwrapKey().map(k -> k.identifier()).orElse(null);
                if (biomeId == null) {
                    continue;
                }
                MobSpawnSettings settings = biome.value().getMobSettings();
                for (MobCategory category : MobCategory.values()) {
                    for (Weighted<MobSpawnSettings.SpawnerData> spawn : settings.getMobs(category).unwrap()) {
                        out.merge(spawn.value().type(), biomeId,
                                (a, b) -> a.toString().compareTo(b.toString()) <= 0 ? a : b);
                    }
                }
            }
        }
        return out;
    }

    static void spells(ServerPlayer player, List<ClueCandidate> out) {
        if (!ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            return;
        }
        PlayerSpellData known = player.getData(ModAttachments.SPELL_DATA.get());
        for (Spell spell : Spells.all()) {
            if (!spell.isImplemented() || ObscurialRules.isObscurialAbility(spell) || known.knowsSpell(spell.getId())) {
                continue;
            }
            List<String> places = SpellSites.placesOf(spell.getId());
            if (places.isEmpty() || !SpellLearningEligibility.evaluate(player, spell).learnable()) {
                continue;
            }
            out.add(new ClueCandidate(ClueCandidate.Kind.SPELL, spell.getId(),
                    Component.translatable(KEY + "clue.spell", Component.literal(spell.getDisplayName()),
                            Component.translatable(KEY + "place." + places.get(0)))));
        }
    }

    static void floo(ServerPlayer player, List<ClueCandidate> out) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            return;
        }
        for (FlooRegistryEntry entry : FlooAccess.reachableBy(player, FlooNetworkManager.get(player.level()))) {
            if (!FlooAccess.hasVisited(player, entry.networkAddress())) {
                out.add(new ClueCandidate(ClueCandidate.Kind.FLOO, entry.networkAddress(),
                        Component.translatable(KEY + "clue.floo", Component.literal(entry.networkAddress()))));
            }
        }
    }

    static void places(ServerPlayer player, List<ClueCandidate> out) {
        for (Identifier id : Locations.all().keySet()) {
            if (Locations.open(id) == null) {
                continue;
            }
            PlayerCareerProgress.TrackRecord record = LocationProgression.recordOf(player, id);
            if (record.xp() == 0 && record.rank() == 0) {
                out.add(new ClueCandidate(ClueCandidate.Kind.PLACE, id.toString(),
                        Component.translatable(KEY + "clue.place", LocationText.name(id))));
            }
        }
    }

    private static boolean isOurs(Identifier id) {
        return WizardsAndBeastsMod.MODID.equals(id.getNamespace());
    }
}
