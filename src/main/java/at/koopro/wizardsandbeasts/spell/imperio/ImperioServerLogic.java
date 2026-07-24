package at.koopro.wizardsandbeasts.spell.imperio;

import at.koopro.wizardsandbeasts.spell.imperio.ImperioControlState;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.spell.CrucioIntentFeedbackS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioControlS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioResistS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioVictimBoundS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ImperioServerLogic {

    private static final Map<UUID, Long> LAST_RESIST_PACKET_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> CASTER_TO_VICTIM = new ConcurrentHashMap<>();

    private ImperioServerLogic() {}

    public static void beginControl(ServerLevel level, ServerPlayer caster, LivingEntity target, int durationTicks) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return;
        }
        // If the target is already under another wizard's Imperius, wrest control cleanly: detach and
        // notify the displaced caster rather than leaving them issuing commands that silently no-op
        // (their stale CASTER_TO_VICTIM entry would point at a victim that no longer answers to them).
        ImperioControlState prior = target.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (prior.isControlled() && !prior.controllerUUID().equals(caster.getUUID())) {
            detachController(level, prior.controllerUUID(), target.getUUID());
        }
        ImperioControlState state = new ImperioControlState(true, caster.getUUID(), durationTicks, 0f,
                ImperioCommand.FOLLOW_CASTER.ordinal(), false);
        target.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), state);
        target.addEffect(new MobEffectInstance(ModEffects.IMPERIO_EUPHORIA, durationTicks, 0, false, true, true));
        if (target instanceof ServerPlayer victim) {
            PacketDistributor.sendToPlayer(victim, new ImperioControlS2CPayload(true, caster.getUUID()));
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        float corruption = caster.getData(ModAttachments.DARK_CORRUPTION.get());
        caster.setData(ModAttachments.DARK_CORRUPTION.get(), Math.min(100f,
                corruption + at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks.scaleCorruptionGain(caster, 8f)));
        level.playSound(null, target.blockPosition(), ModSounds.SPELL_CAST_DARK.get(), SoundSource.PLAYERS, 0.75f, 1.1f);
        Vec3 p = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 1.0, p.z, 20, 0.35, 0.2, 0.35, 0.02);
        CASTER_TO_VICTIM.put(caster.getUUID(), target.getUUID());
        PacketDistributor.sendToPlayer(caster, new ImperioVictimBoundS2CPayload(target.getUUID()));
    }

    public static void applyCommand(ServerPlayer caster, ImperioCommand cmd) {
        UUID victimId = CASTER_TO_VICTIM.get(caster.getUUID());
        if (victimId == null || !(caster.level() instanceof ServerLevel sl)) {
            return;
        }
        LivingEntity victim = findLiving(sl, victimId);
        if (victim == null) {
            return;
        }
        ImperioControlState st = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (!st.isControlled() || !st.controllerUUID().equals(caster.getUUID())) {
            return;
        }
        victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(),
                new ImperioControlState(true, caster.getUUID(), st.remainingTicks(), st.resistanceProgress(),
                        cmd.ordinal(), st.sneakAttemptSinceLastResistTick()));
    }

    /**
     * Drives controlled MOBS to act on their current command (players are driven by {@link #tickVictim}).
     * Runs every few ticks: ATTACK_NEAREST turns the puppet on the nearest living thing — including its
     * own former allies — which is the point of the curse.
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return;
        }
        if (event.getServer().getTickCount() % 5 != 0) {
            return;
        }
        for (Map.Entry<UUID, UUID> e : CASTER_TO_VICTIM.entrySet()) {
            ServerPlayer controller = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (controller == null || !(controller.level() instanceof ServerLevel sl)) {
                continue;
            }
            LivingEntity victim = findLiving(sl, e.getValue());
            if (!(victim instanceof Mob mob)) {
                continue; // player victims are handled by tickVictim
            }
            ImperioControlState st = mob.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
            if (!st.isControlled() || !st.controllerUUID().equals(controller.getUUID())) {
                continue;
            }
            executeMobCommand(sl, mob, controller, ImperioCommand.byOrdinal(st.imperioCommandOrdinal()));
        }
    }

    private static void executeMobCommand(ServerLevel level, Mob mob, ServerPlayer controller, ImperioCommand cmd) {
        switch (cmd) {
            case ATTACK_NEAREST -> {
                LivingEntity nearest = level.getEntitiesOfClass(LivingEntity.class,
                                mob.getBoundingBox().inflate(16.0),
                                other -> other.isAlive() && other != mob && other != controller)
                        .stream()
                        .min(java.util.Comparator.comparingDouble(mob::distanceToSqr))
                        .orElse(null);
                if (nearest != null) {
                    mob.setTarget(nearest);
                }
            }
            case STAND_STILL -> {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            case FOLLOW_CASTER -> {
                mob.setTarget(null);
                if (mob.distanceToSqr(controller) > 9.0) {
                    mob.getNavigation().moveTo(controller, 1.1);
                }
            }
            case RETREAT -> {
                mob.setTarget(null);
                mob.getNavigation().moveTo(controller, 1.3); // regroup on the caster
            }
            case DROP_HELD_ITEM -> {
                net.minecraft.world.item.ItemStack held = mob.getMainHandItem();
                if (!held.isEmpty()) {
                    net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                            level, mob.getX(), mob.getY() + 0.5, mob.getZ(), held.copy());
                    level.addFreshEntity(drop);
                    mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                            net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
    }

    public static void onSneakResistAttempt(ServerPlayer victim) {
        if (!(victim.level() instanceof ServerLevel sl)) {
            return;
        }
        long now = sl.getGameTime();
        Long last = LAST_RESIST_PACKET_TICK.get(victim.getUUID());
        if (last != null && now - last < 20) {
            return;
        }
        LAST_RESIST_PACKET_TICK.put(victim.getUUID(), now);
        ImperioControlState st = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (!st.isControlled()) {
            return;
        }
        victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), st.withSneakAttempt(true));
    }

    public static void tickVictim(ServerPlayer victim) {
        if (!(victim.level() instanceof ServerLevel sl)) {
            return;
        }
        ImperioControlState st = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (!st.isControlled()) {
            return;
        }
        if (victim.tickCount % 20 == 0 && st.sneakAttemptSinceLastResistTick()) {
            float will = victim.getData(ModAttachments.WILLPOWER.get());
            ServerPlayer controller = sl.getServer().getPlayerList().getPlayer(st.controllerUUID());
            if (will <= 0f) {
                victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), st.withSneakAttempt(false));
            } else {
                float imperioProf = Spells.IMPERIO.getProficiencyScalar(victim);
                float resistChance = (will / 100f) * (imperioProf * 0.5f + 0.5f);
                boolean success = sl.random.nextFloat() < resistChance;
                if (success) {
                    clearControl(sl, victim, st.controllerUUID());
                    victim.setData(ModAttachments.WILLPOWER.get(), Math.max(0f, will - 30f));
                    victim.displayClientMessage(
                            Component.literal("You throw off the Imperius Curse!").withStyle(ChatFormatting.GOLD), true);
                    PacketDistributor.sendToPlayer(victim, new ImperioResistS2CPayload(true, 1f));
                    at.koopro.wizardsandbeasts.stats.StatMilestones.onMilestoneTriggered(
                            victim, at.koopro.wizardsandbeasts.stats.MilestoneType.FIRST_IMPERIUS_RESISTED);
                } else {
                    victim.setData(ModAttachments.WILLPOWER.get(), Math.max(0f, will - 15f));
                    victim.addEffect(new MobEffectInstance(ModEffects.IMPERIO_RESISTING, 20, 0, false, true, true));
                    float prog = Math.min(1f, st.resistanceProgress() + 0.1f);
                    victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(),
                            new ImperioControlState(true, st.controllerUUID(), st.remainingTicks(), prog,
                                    st.imperioCommandOrdinal(), false));
                    PacketDistributor.sendToPlayer(victim, new ImperioResistS2CPayload(false, prog));
                }
                if (controller != null) {
                    PacketDistributor.sendToPlayer(controller, new CrucioIntentFeedbackS2CPayload(1.0f));
                }
            }
        }
        ImperioControlState st2 = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (!st2.isControlled()) {
            return;
        }
        ImperioControlState next = st2.tickDown();
        if (!next.isControlled()) {
            clearControl(sl, victim, st2.controllerUUID());
        } else {
            victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), next);
        }
    }

    private static void clearControl(ServerLevel level, LivingEntity victim, UUID controllerUuid) {
        victim.removeEffect(ModEffects.IMPERIO_EUPHORIA);
        victim.removeEffect(ModEffects.IMPERIO_RESISTING);
        victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), ImperioControlState.DEFAULT);
        if (victim instanceof ServerPlayer pl) {
            PacketDistributor.sendToPlayer(pl, new ImperioControlS2CPayload(false, pl.getUUID()));
        }
        CASTER_TO_VICTIM.remove(controllerUuid, victim.getUUID());
        ServerPlayer controller = level.getServer().getPlayerList().getPlayer(controllerUuid);
        if (controller != null) {
            PacketDistributor.sendToPlayer(controller, new ImperioVictimBoundS2CPayload(new UUID(0L, 0L)));
        }
    }

    /**
     * Detaches a displaced caster's Imperius bookkeeping when their victim is taken over by another
     * caster, so the old controller stops "owning" a victim that no longer obeys them and their HUD
     * clears. Does not touch the victim's control state — the new caster overwrites that immediately.
     */
    private static void detachController(ServerLevel level, UUID controllerUuid, UUID victimUuid) {
        CASTER_TO_VICTIM.remove(controllerUuid, victimUuid);
        ServerPlayer controller = level.getServer().getPlayerList().getPlayer(controllerUuid);
        if (controller != null) {
            PacketDistributor.sendToPlayer(controller, new ImperioVictimBoundS2CPayload(new UUID(0L, 0L)));
            controller.displayClientMessage(
                    Component.literal("Your hold on the Imperius Curse is broken.").withStyle(ChatFormatting.DARK_RED),
                    true);
        }
    }

    /** Drops transient per-player state when a player disconnects, both as caster and as victim. */
    public static void onPlayerDisconnect(UUID playerId) {
        CASTER_TO_VICTIM.remove(playerId);
        CASTER_TO_VICTIM.values().removeIf(playerId::equals);
        LAST_RESIST_PACKET_TICK.remove(playerId);
    }

    private static @Nullable LivingEntity findLiving(ServerLevel sl, UUID id) {
        net.minecraft.world.entity.Entity e = sl.getEntity(id);
        return e instanceof LivingEntity ? (LivingEntity) e : null;
    }

    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class LogoutCleanup {
        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            onPlayerDisconnect(event.getEntity().getUUID());
        }
    }
}
