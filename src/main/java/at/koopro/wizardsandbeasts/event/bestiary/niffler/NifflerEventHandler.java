package at.koopro.wizardsandbeasts.event.bestiary.niffler;

import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class NifflerEventHandler {

    private static final Map<UUID, Long> PROX_COOLDOWNS = new HashMap<>();

    private NifflerEventHandler() {}

    // ─── Bestiary: proximity (SIGHTED) ───────────────────────────────────────
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.CREATURES)) return;
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        if (event.getLevel().isClientSide()) return;

        long gameTime = event.getLevel().getGameTime();
        if (gameTime % 100 != 0) return; // check every 5 seconds

        for (Player player : event.getLevel().players()) {
            var nearby = event.getLevel().getEntitiesOfClass(NifflerEntity.class,
                    player.getBoundingBox().inflate(12), EntitySelector.ENTITY_STILL_ALIVE);
            if (nearby.isEmpty()) continue;

            long last = PROX_COOLDOWNS.getOrDefault(player.getUUID(), -200L);
            if (gameTime - last < 100) continue;
            PROX_COOLDOWNS.put(player.getUUID(), gameTime);
            BestiaryDataHelper.setTier(player, NifflerEntity.BESTIARY_ID, DiscoveryTier.SIGHTED);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PROX_COOLDOWNS.remove(event.getEntity().getUUID());
    }

    // ─── Bestiary: kill (ENCOUNTERED) ────────────────────────────────────────
    @SubscribeEvent
    public static void onNifflerDeath(LivingDeathEvent event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        if (!(event.getEntity() instanceof NifflerEntity)) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        BestiaryDataHelper.setTier(player, NifflerEntity.BESTIARY_ID, DiscoveryTier.ENCOUNTERED);
    }

    // ─── Bestiary: pouch open (STUDIED) ──────────────────────────────────────
    @SubscribeEvent
    public static void onPouchOpen(NifflerPouchOpenEvent event) {
        if (!ModuleManager.isEnabled(Module.BESTIARY)) return;
        BestiaryDataHelper.setTier(event.getPlayer(), NifflerEntity.BESTIARY_ID, DiscoveryTier.STUDIED);
    }

    // ─── Baby Niffler 30% co-spawn chance ────────────────────────────────────
    @SubscribeEvent
    public static void onNifflerJoinLevel(EntityJoinLevelEvent event) {
        if (!ModuleManager.isEnabled(Module.CREATURES)) return;
        if (!(event.getEntity() instanceof NifflerEntity niffler)) return;
        if (niffler.isBaby()) return;
        if (event.getLevel().isClientSide()) return;
        if (niffler.getRandom().nextFloat() >= 0.30f) return;

        // Spawn 1–3 baby Nifflers nearby
        var sl = (net.minecraft.server.level.ServerLevel) event.getLevel();
        int count = 1 + niffler.getRandom().nextInt(3);
        for (int i = 0; i < count; i++) {
            at.koopro.wizardsandbeasts.entity.niffler.BabyNifflerEntity baby =
                    new at.koopro.wizardsandbeasts.entity.niffler.BabyNifflerEntity(
                            ModEntities.BABY_NIFFLER.get(), sl);
            double ox = (niffler.getRandom().nextDouble() - 0.5) * 4;
            double oz = (niffler.getRandom().nextDouble() - 0.5) * 4;
            baby.setPos(niffler.getX() + ox, niffler.getY(), niffler.getZ() + oz);
            sl.addFreshEntity(baby);
        }
    }
}
