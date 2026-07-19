package at.koopro.wizardsandbeasts.event.bestiary;

import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.*;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class BestiaryDiscoveryHandler {
    /** Proximity-sighting cooldowns, evicted on logout. */
    private static final Map<ProximityCooldownKey, Long> PROX_COOLDOWNS = new HashMap<>();
    /** Ticks between proximity scans per player. */
    private static final int PROXIMITY_SCAN_INTERVAL_TICKS = 20;
    /** Scan radius (blocks) for proximity sightings. */
    private static final double PROXIMITY_SCAN_RADIUS = 12;

    private record ProximityCooldownKey(UUID playerId, Identifier entryId) {}

    private BestiaryDiscoveryHandler() {}

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player)) return;
        Identifier killedType = event.getEntity().getType().builtInRegistryHolder().key().identifier();
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            if (entry.encounterTrigger() != EncounterTrigger.KILL) continue;
            if (entry.entityType().isPresent() && entry.entityType().get().equals(killedType)) {
                DiscoveryTier tier = BestiaryDataHelper.getTier(player, entry.id());
                BestiaryDataHelper.setTier(player, entry.id(), tier == DiscoveryTier.SIGHTED ? DiscoveryTier.ENCOUNTERED : DiscoveryTier.SIGHTED);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % PROXIMITY_SCAN_INTERVAL_TICKS != 0) return;

        var nearby = player.level().getEntities(player,
                player.getBoundingBox().inflate(PROXIMITY_SCAN_RADIUS), e -> e instanceof LivingEntity);
        if (nearby.isEmpty()) return;

        // Index PROXIMITY entries by entity type once per scan (registry can change on datapack reload).
        Map<Identifier, List<BestiaryEntry>> proximityByType = new HashMap<>();
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            if (entry.encounterTrigger() != EncounterTrigger.PROXIMITY || entry.entityType().isEmpty()) continue;
            proximityByType.computeIfAbsent(entry.entityType().get(), k -> new ArrayList<>(1)).add(entry);
        }
        if (proximityByType.isEmpty()) return;

        long now = player.level().getGameTime();
        var bestiaryXpMultipliers = PlayerSkillBonusData.forPlayer(player).bestiaryXpMultipliers();

        for (Entity entity : nearby) {
            List<BestiaryEntry> entries = proximityByType.get(entity.getType().builtInRegistryHolder().key().identifier());
            if (entries == null) continue;
            for (BestiaryEntry entry : entries) {
                ProximityCooldownKey cooldownKey = new ProximityCooldownKey(player.getUUID(), entry.id());
                long last = PROX_COOLDOWNS.getOrDefault(cooldownKey, -1200L);
                float multiplier = bestiaryXpMultipliers.getOrDefault(entry.category(), 1.0f);
                long requiredTicks = Math.max(1L, Math.round(20.0f / Math.max(0.1f, multiplier)));
                if (now - last < requiredTicks) continue;
                PROX_COOLDOWNS.put(cooldownKey, now);
                if (BestiaryDataHelper.getTier(player, entry.id()) == DiscoveryTier.UNDISCOVERED) {
                    BestiaryDataHelper.setTier(player, entry.id(), DiscoveryTier.SIGHTED);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        PROX_COOLDOWNS.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        Identifier droppedType = event.getEntity().getType().builtInRegistryHolder().key().identifier();
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            if (entry.encounterTrigger() != EncounterTrigger.LOOT) continue;
            if (entry.entityType().isPresent() && entry.entityType().get().equals(droppedType)) {
                BestiaryDataHelper.setTier(player, entry.id(), DiscoveryTier.ENCOUNTERED);
            }
        }
    }
}
