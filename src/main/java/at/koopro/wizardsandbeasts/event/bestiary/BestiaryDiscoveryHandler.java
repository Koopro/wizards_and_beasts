package at.koopro.wizardsandbeasts.event.bestiary;

import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.*;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns what happens in the world into bestiary progress. The <em>rule</em> lives in
 * {@link EncounterRule}; this class only supplies the events and the player.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class BestiaryDiscoveryHandler {

    /** Proximity-sighting cooldowns, evicted on logout. */
    private static final Map<ProximityCooldownKey, Long> PROX_COOLDOWNS = new HashMap<>();

    /** Ticks between proximity scans per player. */
    private static final int PROXIMITY_SCAN_INTERVAL_TICKS = 20;

    /**
     * Entries indexed by the entity type they describe, rebuilt on datapack reload rather than on
     * every scan.
     *
     * <p>The previous shape rebuilt this map inside the per-player tick handler: 107 entries walked
     * once a second per online player, to produce an answer that only changes on a datapack reload.
     * Volatile-swapped immutable map, the same discipline {@code BestiaryEntryRegistry} uses, so a
     * scan running during a reload sees one whole generation or the other.
     */
    private static volatile Map<Identifier, List<BestiaryEntry>> byEntityType = Map.of();

    private record ProximityCooldownKey(UUID playerId, Identifier entryId) {}

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

    private static List<BestiaryEntry> entriesFor(EntityType<?> type) {
        Map<Identifier, List<BestiaryEntry>> index = byEntityType;
        if (index.isEmpty() && !BestiaryEntryRegistry.getAll().isEmpty()) {
            // Entries finished loading before this handler saw the event. Build the index now
            // rather than answering "nothing matches" for the rest of the session.
            rebuildIndex();
            index = byEntityType;
        }
        return index.getOrDefault(type.builtInRegistryHolder().key().identifier(), List.of());
    }

    // -- events ---------------------------------------------------------------------------------

    /**
     * A kill both proves a sighting and fires the {@link EncounterTrigger#KILL} channel, so a
     * KILL-triggered entry advances two steps on the first kill (unopened to
     * {@link DiscoveryTier#ENCOUNTERED}) and one step on each kill after.
     */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        for (BestiaryEntry entry : entriesFor(event.getEntity().getType())) {
            DiscoveryTier tier = BestiaryDataHelper.getTier(player, entry.id());
            DiscoveryTier next = EncounterRule.onSighted(tier);
            if (EncounterRule.deepensOn(entry.encounterTrigger(), EncounterTrigger.KILL)) {
                next = EncounterRule.onTriggered(next);
            }
            apply(player, entry, tier, next);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        for (BestiaryEntry entry : entriesFor(event.getEntity().getType())) {
            if (!EncounterRule.deepensOn(entry.encounterTrigger(), EncounterTrigger.LOOT)) continue;
            DiscoveryTier tier = BestiaryDataHelper.getTier(player, entry.id());
            apply(player, entry, tier, EncounterRule.onTriggered(EncounterRule.onSighted(tier)));
        }
    }

    /**
     * Sighting scan. Every entry that names an entity type takes part, not only the ones declaring
     * {@link EncounterTrigger#PROXIMITY} - see {@link EncounterRule} for why.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % PROXIMITY_SCAN_INTERVAL_TICKS != 0) return;

        var nearby = player.level().getEntities(player,
                player.getBoundingBox().inflate(EncounterRule.SIGHTING_RANGE),
                e -> e instanceof LivingEntity);
        if (nearby.isEmpty()) return;

        long now = player.level().getGameTime();
        var bestiaryXpMultipliers = PlayerSkillBonusData.forPlayer(player).bestiaryXpMultipliers();

        for (Entity entity : nearby) {
            for (BestiaryEntry entry : entriesFor(entity.getType())) {
                ProximityCooldownKey cooldownKey = new ProximityCooldownKey(player.getUUID(), entry.id());
                long last = PROX_COOLDOWNS.getOrDefault(cooldownKey, -1200L);
                float multiplier = bestiaryXpMultipliers.getOrDefault(entry.category(), 1.0f);
                long requiredTicks = Math.max(1L, Math.round(20.0f / Math.max(0.1f, multiplier)));
                if (now - last < requiredTicks) continue;
                PROX_COOLDOWNS.put(cooldownKey, now);
                DiscoveryTier tier = BestiaryDataHelper.getTier(player, entry.id());
                apply(player, entry, tier, EncounterRule.onSighted(tier));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        PROX_COOLDOWNS.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    // -- write + notify -------------------------------------------------------------------------

    /** Store the new tier and, when it actually rose, tell the player. */
    private static void apply(ServerPlayer player, BestiaryEntry entry,
                              DiscoveryTier oldTier, DiscoveryTier newTier) {
        if (newTier.ordinal() <= oldTier.ordinal()) return;
        BestiaryDataHelper.setTier(player, entry.id(), newTier);
        player.displayClientMessage(
                Component.translatable("bestiary.wizards_and_beasts.discovered", entry.displayName())
                        .withStyle(ChatFormatting.GREEN),
                true);
    }
}
