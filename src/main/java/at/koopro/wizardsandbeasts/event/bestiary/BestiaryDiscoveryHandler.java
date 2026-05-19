package at.koopro.wizardsandbeasts.event.bestiary;

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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BestiaryDiscoveryHandler {
    private static final Map<String, Long> PROX_COOLDOWNS = new HashMap<>();

    private BestiaryDiscoveryHandler() {}

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player)) return;
        String key = event.getEntity().getType().builtInRegistryHolder().key().identifier().toString();
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            if (entry.encounterTrigger() != EncounterTrigger.KILL) continue;
            if (entry.entityType().isPresent() && entry.entityType().get().toString().equals(key)) {
                DiscoveryTier tier = BestiaryDataHelper.getTier(player, entry.id());
                BestiaryDataHelper.setTier(player, entry.id(), tier == DiscoveryTier.SIGHTED ? DiscoveryTier.ENCOUNTERED : DiscoveryTier.SIGHTED);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;
        var nearby = player.level().getEntities(player, player.getBoundingBox().inflate(12), e -> e instanceof LivingEntity);
        for (Entity entity : nearby) {
            Identifier entityId = entity.getType().builtInRegistryHolder().key().identifier();
            for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
                if (entry.encounterTrigger() != EncounterTrigger.PROXIMITY || entry.entityType().isEmpty()) continue;
                if (!entry.entityType().get().equals(entityId)) continue;
                String cooldownKey = player.getUUID() + "|" + entry.id();
                long now = player.level().getGameTime();
                long last = PROX_COOLDOWNS.getOrDefault(cooldownKey, -1200L);
                float multiplier = PlayerSkillBonusData.forPlayer(player)
                        .bestiaryXpMultipliers()
                        .getOrDefault(entry.category(), 1.0f);
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
    public static void onDrops(LivingDropsEvent event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            if (entry.encounterTrigger() == EncounterTrigger.LOOT) {
                BestiaryDataHelper.setTier(player, entry.id(), DiscoveryTier.ENCOUNTERED);
            }
        }
    }
}
