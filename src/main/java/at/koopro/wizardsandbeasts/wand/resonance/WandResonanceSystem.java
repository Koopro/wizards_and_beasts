package at.koopro.wizardsandbeasts.wand.resonance;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.item.wand.WandModuleHooks;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.wand.WandAttachments;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure logic for wand–wizard resonance. Invoked from {@link at.koopro.wizardsandbeasts.item.wand.WandItem}
 * and Ollivander trial GUI — no automatic event subscriptions.
 */
public final class WandResonanceSystem {

    private WandResonanceSystem() {
    }

    public record ResonanceResult(boolean matched, float score, Identifier woodKey, Identifier coreKey) {
    }

    /**
     * Bumped whenever the scoring changes, so a saved cache from an older formula is ignored rather
     * than answering with a score the current rules would never produce.
     */
    private static final int SCORING_VERSION = 2;

    /**
     * Two of the four factors move with the player (core temperament with experience level, length
     * affinity with the wand's own length), so neither may be left out of the key: keyed on
     * wood|core|flexibility alone, the first score a wizard ever saw was the only one they could get,
     * and levelling up never improved a thing.
     */
    private static String cacheKey(Identifier wood, Identifier core, WandFlexibility flex, int level, float lengthInches) {
        return SCORING_VERSION + "|" + wood + "|" + core + "|" + flex.getSerializedName()
                + "|" + level + "|" + Math.round(lengthInches * 10.0f);
    }

    public static float computeResonance(Player player, ItemStack wandStack, RegistryAccess registryAccess) {
        Identifier woodKey = WandComponents.getWood(wandStack);
        Identifier coreKey = WandComponents.getCore(wandStack);
        WandFlexibility flex = WandComponents.getFlexibility(wandStack);
        if (woodKey == null || coreKey == null || flex == null) {
            return 0.0f;
        }
        Float lengthInches = WandComponents.getLength(wandStack);
        float len = lengthInches == null ? 11.0f : lengthInches;
        String cacheKey = cacheKey(woodKey, coreKey, flex, player.experienceLevel, len);
        Map<String, Float> stored = player.getData(WandAttachments.WAND_RESONANCE_CACHE.get());
        Float cached = stored.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        WandResonanceConfig cfg = WandResonanceConfigLoader.getConfig(registryAccess);
        Optional<Holder.Reference<WandWoodDefinition>> woodHolder =
                registryAccess.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).get(woodKey);
        Optional<Holder.Reference<WandCoreDefinition>> coreHolder =
                registryAccess.lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY).get(coreKey);
        if (woodHolder.isEmpty() || coreHolder.isEmpty()) {
            return 0.0f;
        }
        WandWoodDefinition wood = woodHolder.get().value();
        WandCoreDefinition core = coreHolder.get().value();

        float woodScore = scoreWoodPersonality(player, wood);
        float coreScore = scoreCoreTemperament(player, core);
        float lengthScore = scoreLengthAffinity(player, len);
        float flexScore = scoreFlexibilityMatch(player, flex);

        float sum = cfg.woodWeight() * woodScore
                + cfg.coreWeight() * coreScore
                + cfg.lengthWeight() * lengthScore
                + cfg.flexibilityWeight() * flexScore;
        float result = Math.max(0.0f, Math.min(1.0f, sum));
        // Deserialized attachments come back as an immutable map, so never mutate `stored` in place.
        Map<String, Float> updated = new HashMap<>(stored);
        updated.put(cacheKey, result);
        player.setData(WandAttachments.WAND_RESONANCE_CACHE.get(), updated);
        return result;
    }

    /**
     * Wood affinity for a wizard whose heritage variant is not yet known. A hard {@code 0} here caps the
     * weighted total at {@code 1 - woodWeight} (0.60 with the shipped weights), which sits below the 0.65
     * match threshold — no wand could ever bond, and casting requires a bond. A neutral score keeps the
     * variant meaningful (a matching variant still scores 1.0) while leaving the ceiling reachable.
     */
    static final float NEUTRAL_WOOD_AFFINITY = 0.5f;

    /**
     * Affinity for a known wizard the wood does not favour. Also non-zero, and for the same reason: a
     * wizard whose three offered wands all happen to miss their traits must still be able to leave
     * Ollivander's with a wand, just a worse-matched one.
     */
    static final float UNFAVOURED_WOOD_AFFINITY = 0.35f;

    private static float scoreWoodPersonality(Player player, WandWoodDefinition wood) {
        PlayerHeritageData typeData = player.getData(ModAttachments.HERITAGE_DATA.get());
        HeritageVariant sub = typeData.getSelectedHeritageVariant();
        if (sub == null) {
            return NEUTRAL_WOOD_AFFINITY;
        }
        return woodAffinityScore(traitsOf(sub), wood.personalityAffinity());
    }

    /**
     * Everything about a wizard a wood may name: their personality traits (the vocabulary the shipped
     * woods use), plus their variant id and capability tags so datapack woods can key off those too.
     */
    static Set<String> traitsOf(HeritageVariant variant) {
        Set<String> traits = new HashSet<>(WizardPersonality.of(variant));
        traits.add(variant.getId());
        traits.addAll(variant.getTags());
        return traits;
    }

    /** Graded, and never zero: one shared trait is a good match, two or more is the wand choosing you. */
    static float woodAffinityScore(Set<String> wizardTraits, List<String> personalityAffinity) {
        int matches = 0;
        for (String affinity : personalityAffinity) {
            if (wizardTraits.contains(affinity)) {
                matches++;
            }
        }
        return switch (matches) {
            case 0 -> UNFAVOURED_WOOD_AFFINITY;
            case 1 -> 0.8f;
            default -> 1.0f;
        };
    }

    /** Weakest and strongest catalogued core power, used to read {@code raw_power} as a 0–1 position. */
    static final float CORE_POWER_FLOOR = 1.0f;
    static final float CORE_POWER_CEILING = 2.0f;

    /** Width of the core preference curve. Narrow enough to rank cores, wide enough that one can fit. */
    private static final float CORE_TOLERANCE = 0.35f;

    /** Experience level at which a wizard is judged to want the most volatile core available. */
    private static final float CORE_IDEAL_FULL_LEVEL = 30.0f;

    private static float scoreCoreTemperament(Player player, WandCoreDefinition core) {
        return coreTemperamentScore(core.rawPower(), player.experienceLevel);
    }

    /**
     * Gaussian preference over core power, in the wizard's favour as they grow: a novice resonates with
     * a steady unicorn hair, a seasoned duellist with a dragon heartstring.
     *
     * <p>This used to compare {@code raw_power} (an absolute 1.15–1.8 scale) against an ideal normalised
     * to 0–1 through a curve of width 0.2, so the closest any core could come was ≈0.055 — the whole 0.3
     * core weight was dead arithmetic. Both sides are now on the same 0–1 scale. The old ideal also read
     * {@code BLOCK_INTERACTION_RANGE}, which is a constant 4.5 for every player who is not mid-Animagus
     * transformation, so it only ever contributed the same offset to everyone; progression carries it now.
     */
    static float coreTemperamentScore(float rawPower, int experienceLevel) {
        float ideal = clamp01(experienceLevel / CORE_IDEAL_FULL_LEVEL);
        float normalisedPower = clamp01((rawPower - CORE_POWER_FLOOR) / (CORE_POWER_CEILING - CORE_POWER_FLOOR));
        float delta = (normalisedPower - ideal) / CORE_TOLERANCE;
        return (float) Math.exp(-0.5 * delta * delta);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float scoreLengthAffinity(Player player, float wandLengthInches) {
        return lengthAffinityScore(wandLengthInches, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
    }

    /** Longer wands suit wizards who fight at range; {@code entityReach} is the vanilla 3.0 by default. */
    static float lengthAffinityScore(float wandLengthInches, double entityReach) {
        float combatLean = (float) Math.min(1.0, Math.max(0.0, (entityReach - 2.5) / 2.5));
        float idealLength = 8.0f + combatLean * 6.0f;
        return 1.0f - Math.min(1.0f, Math.abs(wandLengthInches - idealLength) / 8.0f);
    }

    private static float scoreFlexibilityMatch(Player player, WandFlexibility wandFlex) {
        PlayerHeritageData td = player.getData(ModAttachments.HERITAGE_DATA.get());
        HeritageVariant sub = td.getSelectedHeritageVariant();
        boolean shapeshifter = sub != null && sub.hasTag("obscurus_form") && sub.hasTag("transformation");
        return flexibilityScore(wandFlex, player.experienceLevel, shapeshifter);
    }

    /** A wizard grows into springier wands as they learn to bend; an Obscurial starts part-way there. */
    static float flexibilityScore(WandFlexibility wandFlex, int experienceLevel, boolean shapeshifter) {
        float adaptability = Math.min(1.0f, experienceLevel / 30.0f);
        if (shapeshifter) {
            adaptability = Math.min(1.0f, adaptability + 0.25f);
        }
        int idealOrdinal = Math.round(adaptability * (WandFlexibility.values().length - 1));
        int distance = Math.abs(wandFlex.ordinal() - idealOrdinal);
        return 1.0f - distance / (float) (WandFlexibility.values().length - 1);
    }

    public static ResonanceResult applyResonance(Player player, ItemStack wandStack, float score, RegistryAccess registryAccess) {
        Identifier woodKey = WandComponents.getWood(wandStack);
        Identifier coreKey = WandComponents.getCore(wandStack);
        WandFlexibility flex = WandComponents.getFlexibility(wandStack);
        if (woodKey == null || coreKey == null) {
            return new ResonanceResult(false, score, woodKey, coreKey);
        }

        WandResonanceConfig cfg = WandResonanceConfigLoader.getConfig(registryAccess);
        float refuseFloor = cfg.refuseThreshold();
        Optional<Holder.Reference<WandWoodDefinition>> woodOpt =
                registryAccess.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).get(woodKey);
        if (woodOpt.isPresent()) {
            refuseFloor = Math.max(refuseFloor, woodOpt.get().value().refuseThreshold());
        }

        if (score >= cfg.matchThreshold()) {
            wandStack.set(WandComponents.WAND_MASTER.get(), Optional.of(player.getUUID()));
            wandStack.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 1.0f);
            if (flex != null) {
                float allegiance = WandComponents.getAllegianceScore(wandStack);
                player.setData(WandAttachments.BONDED_WAND.get(), Optional.of(new WandAttachments.BondedWandRecord(
                        woodKey, coreKey, flex, allegiance)));
            }
            if (player instanceof ServerPlayer sp) {
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
                sp.level().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getEyeY(), sp.getZ(), 24, 0.35, 0.25, 0.35, 0.02);
                Component woodName = woodOpt.map(h -> h.value().displayName()).orElse(Component.literal(woodKey.toString()));
                Component coreName = registryAccess.lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY)
                        .get(coreKey).map(h -> h.value().displayName()).orElse(Component.literal(coreKey.toString()));
                sp.displayClientMessage(prefixPreview(Component.translatable("wandcraft.resonance.matched", woodName, coreName)), false);
            }
            return new ResonanceResult(true, score, woodKey, coreKey);
        }

        if (score >= refuseFloor) {
            if (player instanceof ServerPlayer sp) {
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.2f, 1.2f);
                sp.displayClientMessage(prefixPreview(Component.translatable("wandcraft.resonance.mismatch")), false);
            }
            return new ResonanceResult(false, score, woodKey, coreKey);
        }

        if (player instanceof ServerPlayer sp) {
            sp.hurt(sp.damageSources().magic(), 4.0f);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.15f, 1.5f);
            sp.level().sendParticles(ParticleTypes.LARGE_SMOKE, sp.getX(), sp.getEyeY(), sp.getZ(), 12, 0.2, 0.2, 0.2, 0.01);
            sp.displayClientMessage(prefixPreview(Component.translatable("wandcraft.resonance.refused")), false);
        }
        return new ResonanceResult(false, score, woodKey, coreKey);
    }

    private static Component prefixPreview(Component base) {
        if (WandModuleHooks.isWandsPreview()) {
            return Component.literal("[Preview] ").append(base);
        }
        return base;
    }
}
