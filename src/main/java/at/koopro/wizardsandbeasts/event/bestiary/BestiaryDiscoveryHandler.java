package at.koopro.wizardsandbeasts.event.bestiary;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.bestiary.CreatureProfile;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.EncounterRule;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns what happens in the world into bestiary progress. The <em>rule</em> lives in {@link EncounterRule}; this
 * class supplies the events, and is the one door species code uses to say "this player studied it" or "this player
 * saw it do the thing".
 *
 * <p>Everything it stores is on the player's bestiary attachment (tiers and watching time), so nothing here is lost
 * to a logout, a death or a restart, and nothing is held in memory per player.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class BestiaryDiscoveryHandler {

    /** Ticks between sighting and watching scans per player. */
    private static final int SCAN_INTERVAL_TICKS = 20;

    /**
     * Entries indexed by the entity type they describe, rebuilt on datapack reload rather than on every scan.
     * Volatile-swapped immutable map, the same discipline {@code BestiaryEntryRegistry} uses.
     */
    private static volatile Map<Identifier, List<BestiaryEntry>> byEntityType = Map.of();

    private BestiaryDiscoveryHandler() {}

    // -- index ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEntriesLoaded(BestiaryEntriesLoadedEvent event) {
        rebuildIndex();
    }

    /** Package-private so a reload that lands before this handler is wired can still prime it. */
    static void rebuildIndex() {
        Map<Identifier, List<BestiaryEntry>> index = new HashMap<>();
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            entry.entityType().ifPresent(type ->
                    index.computeIfAbsent(type, k -> new ArrayList<>(1)).add(entry));
        }
        byEntityType = Map.copyOf(index);
    }

    public static List<BestiaryEntry> entriesFor(EntityType<?> type) {
        Map<Identifier, List<BestiaryEntry>> index = byEntityType;
        if (index.isEmpty() && !BestiaryEntryRegistry.getAll().isEmpty()) {
            rebuildIndex();
            index = byEntityType;
        }
        return index.getOrDefault(type.builtInRegistryHolder().key().identifier(), List.of());
    }

    // -- the doors species code uses ------------------------------------------------------------

    /** A player met this creature. */
    public static void encountered(ServerPlayer player, Entity creature) {
        advance(player, creature.getType(), EncounterRule::onEncountered);
    }

    /** A player did what a naturalist does with this creature: fed it, handled it, took what it shed. */
    public static void studied(ServerPlayer player, Entity creature) {
        advance(player, creature.getType(), EncounterRule::onStudied);
    }

    /** {@link #studied(ServerPlayer, Entity)} for a species with no creature at hand — a shed hair picked up. */
    public static void studied(ServerPlayer player, EntityType<?> species) {
        advance(player, species, EncounterRule::onStudied);
    }

    /** A player saw this creature's signature behaviour, or won its trust. */
    public static void witnessedSignature(ServerPlayer player, Entity creature) {
        advance(player, creature.getType(), EncounterRule::onSignature);
    }

    /** Every player who can see {@code creature} from within {@code radius} witnessed its signature behaviour. */
    public static void signatureSeenByNearby(Entity creature, double radius) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(creature) <= radius * radius
                    && player.hasLineOfSight(creature) && !creature.isInvisibleTo(player)) {
                witnessedSignature(player, creature);
            }
        }
    }

    private static void advance(ServerPlayer player, EntityType<?> species,
                                java.util.function.UnaryOperator<DiscoveryTier> rule) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        for (BestiaryEntry entry : entriesFor(species)) {
            if (!EncounterRule.automatic(entry.encounterTrigger())) continue;
            DiscoveryTier tier = BestiaryDataHelper.getTier(player, entry.id());
            apply(player, entry, tier, rule.apply(tier));
        }
    }

    // -- events ---------------------------------------------------------------------------------

    /** A kill is an encounter, and nothing more. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            encountered(player, event.getEntity());
        }
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() != null) {
            encountered(victim, event.getSource().getEntity());
        }
    }

    /** Being attacked by a creature is meeting it. */
    @SubscribeEvent
    public static void onAttacked(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof LivingEntity attacker
                && !(attacker instanceof ServerPlayer)) {
            encountered(player, attacker);
        }
    }

    /**
     * Sighting and watching. Every automatic entry takes part. Watching time is added once per entry per scan,
     * however many of that creature are in view, and scaled by the player's Magizoology bonus for its category.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % SCAN_INTERVAL_TICKS == 0) {
            scan(player);
        }
    }

    /** One scan's worth of sighting and watching for {@code player} — what a second of the player's time earns. */
    public static void scan(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.BESTIARY) || player.isSpectator()) return;

        double reach = Math.max(EncounterRule.SIGHTING_RANGE, EncounterRule.OBSERVATION_RANGE);
        List<Entity> nearby = player.level().getEntities(player, player.getBoundingBox().inflate(reach),
                e -> e instanceof LivingEntity living && living.isAlive() && !(e instanceof ServerPlayer));
        if (nearby.isEmpty()) return;

        Map<?, Float> multipliers = PlayerSkillBonusData.forPlayer(player).bestiaryXpMultipliers();
        Set<Identifier> watchedThisScan = new HashSet<>();
        for (Entity entity : nearby) {
            LivingEntity creature = (LivingEntity) entity;
            List<BestiaryEntry> entries = entriesFor(creature.getType());
            if (entries.isEmpty()) continue;
            double distanceSq = player.distanceToSqr(creature);
            // A creature that is invisible to this player — a Demiguise being looked at, a burrowed Mooncalf — is
            // not seen, however close it stands.
            boolean inSight = !creature.isInvisibleTo(player) && player.hasLineOfSight(creature);
            for (BestiaryEntry entry : entries) {
                if (!EncounterRule.automatic(entry.encounterTrigger())) continue;
                DiscoveryTier tier = BestiaryDataHelper.getTier(player, entry.id());
                if (inSight && distanceSq <= EncounterRule.SIGHTING_RANGE * EncounterRule.SIGHTING_RANGE) {
                    apply(player, entry, tier, EncounterRule.onEncountered(tier));
                    tier = BestiaryDataHelper.getTier(player, entry.id());
                }
                if (!inSight || tier == DiscoveryTier.UNKNOWN
                        || distanceSq > EncounterRule.OBSERVATION_RANGE * EncounterRule.OBSERVATION_RANGE
                        || !calm(player, creature) || !watchedThisScan.add(entry.id())) {
                    continue;
                }
                float multiplier = multipliers.getOrDefault(entry.category(), 1.0f);
                int gained = Math.max(1, Math.round(SCAN_INTERVAL_TICKS * Math.max(0.1f, multiplier)));
                int total = BestiaryDataHelper.addObservedTicks(player, entry.id(), gained);
                CreatureProfile profile = entry.profile().orElse(null);
                boolean byHand = profile != null && profile.studiedByHand();
                boolean signature = profile != null && profile.hasSignature();
                apply(player, entry, tier, EncounterRule.afterWatching(tier, total, byHand, signature));
            }
        }
    }

    /**
     * Watching only counts while the moment is calm: the player has not hurt this creature lately, and it is not
     * hunting them.
     */
    static boolean calm(ServerPlayer player, LivingEntity creature) {
        if (creature.getLastHurtByMob() == player
                && creature.tickCount - creature.getLastHurtByMobTimestamp() < EncounterRule.CALM_AFTER_HARM_TICKS) {
            return false;
        }
        return !(creature instanceof Mob mob && mob.getTarget() == player);
    }

    // -- write + notify -------------------------------------------------------------------------

    /** Store the new tier and, when it actually rose, tell the player which page grew and how far. */
    private static void apply(ServerPlayer player, BestiaryEntry entry,
                              DiscoveryTier oldTier, DiscoveryTier newTier) {
        if (newTier.ordinal() <= oldTier.ordinal()) return;
        BestiaryDataHelper.setTier(player, entry.id(), newTier);
        player.displayClientMessage(
                Component.translatable("bestiary.wizards_and_beasts.progress", entry.displayName(),
                                newTier.displayName())
                        .withStyle(ChatFormatting.GREEN),
                true);
    }
}
