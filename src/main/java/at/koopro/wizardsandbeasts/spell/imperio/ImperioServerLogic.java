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

/**
 * The Imperius Curse's server side: who controls whom, what a controlled mob does, and when control ends.
 *
 * <p>Registered on the game bus. It used to carry {@code @SubscribeEvent} with no subscriber annotation, so
 * {@link #onServerTick} never ran: a controlled mob never acted on a command after the first tick and its
 * control state never counted down, and that state is saved with the mob (KNOWN_ISSUES 8c.3). Control now
 * also ends with either party's death or change of dimension, the caster's logout, and a mob loaded with a
 * control state no live caster holds.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
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
        // The whole price of an Imperius Curse, charged here rather than at cast: the weight of it is
        // in seizing another will, and billing at cast charged for one that missed. Through the toll
        // so the vocation scaling, the clamp and the character sheet's mirrored attribute all move
        // together — writing the attachment raw left the sheet stale until the next login.
        at.koopro.wizardsandbeasts.corruption.UnforgivableToll.charge(caster, 8f);
        at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace.onUnforgivableUse(caster, "wizards_and_beasts:imperio",
                at.koopro.wizardsandbeasts.spell.core.SpellCategory.DARK_ARTS, target);
        level.playSound(null, target.blockPosition(), ModSounds.SPELL_CAST_DARK.get(), SoundSource.PLAYERS, 0.75f, 1.1f);
        Vec3 p = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 1.0, p.z, 20, 0.35, 0.2, 0.35, 0.02);
        CASTER_TO_VICTIM.put(caster.getUUID(), target.getUUID());
        PacketDistributor.sendToPlayer(caster, new ImperioVictimBoundS2CPayload(target.getUUID()));
    }

    public static void applyCommand(ServerPlayer caster, ImperioCommand cmd) {
        UUID victimId = CASTER_TO_VICTIM.get(caster.getUUID());
        // A dead caster commands nothing; the command menu's packet can still arrive after the death.
        if (victimId == null || !caster.isAlive() || !(caster.level() instanceof ServerLevel sl)) {
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
        boolean act = ModuleManager.isEnabled(Module.DARK_ARTS) && event.getServer().getTickCount() % 5 == 0;
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
            // A mob's control counts down here, as a player's does in tickVictim. Every tick, whether or not
            // the Dark Arts are switched on, so a curse cast before the switch still runs out.
            ImperioControlState next = st.tickDown();
            if (!next.isControlled()) {
                clearControl(sl, mob, controller.getUUID());
                continue;
            }
            mob.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), next);
            if (act) {
                executeMobCommand(sl, mob, controller, ImperioCommand.byOrdinal(next.imperioCommandOrdinal()));
            }
        }
    }

    /** Ends whatever control {@code casterId} holds over a victim in {@code level}. */
    public static void releaseVictimOf(ServerLevel level, UUID casterId) {
        UUID victimId = CASTER_TO_VICTIM.get(casterId);
        if (victimId == null) {
            return;
        }
        LivingEntity victim = findLiving(level, victimId);
        if (victim != null) {
            ImperioControlState st = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
            if (st.isControlled() && casterId.equals(st.controllerUUID())) {
                clearControl(level, victim, casterId);
                return;
            }
        }
        CASTER_TO_VICTIM.remove(casterId, victimId);
    }

    /** Frees {@code victim} from any Imperius control, and tells its caster. */
    public static void releaseVictim(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        ImperioControlState st = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (st.isControlled()) {
            clearControl(level, victim, st.controllerUUID());
        }
    }

    /**
     * Death ends the curse from either side: a dead caster holds no one, and a dead victim (whose state is
     * copied to the respawned player) must not wake up still bound.
     */
    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        releaseVictim(dead);
        if (dead instanceof ServerPlayer caster && caster.level() instanceof ServerLevel level) {
            releaseVictimOf(level, caster.getUUID());
        }
    }

    /** Neither side can reach the other across dimensions, so control ends with the crossing. */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        releaseVictim(player);
        if (!CASTER_TO_VICTIM.containsKey(player.getUUID())) {
            return;
        }
        // The victim stayed behind in the dimension the caster left.
        ServerLevel old = player.level().getServer() == null ? null : player.level().getServer().getLevel(event.getFrom());
        if (old != null) {
            releaseVictimOf(old, player.getUUID());
        } else {
            CASTER_TO_VICTIM.remove(player.getUUID());
        }
    }

    /**
     * A mob saved under control is loaded with that state, but the caster mapping lives only in memory. A mob
     * no live caster holds is free: otherwise it stayed controlled for good after a restart or its caster's
     * logout, with nothing ever counting it down.
     */
    @SubscribeEvent
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)
                || !mob.hasData(ModAttachments.IMPERIO_CONTROL_STATE.get())) {
            return;
        }
        ImperioControlState st = mob.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (st.isControlled() && !mob.getUUID().equals(CASTER_TO_VICTIM.get(st.controllerUUID()))) {
            mob.removeEffect(ModEffects.IMPERIO_EUPHORIA);
            mob.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), ImperioControlState.DEFAULT);
        }
    }

    /** Whether {@code casterId} holds anyone under the curse. */
    public static boolean holdsVictim(UUID casterId) {
        return CASTER_TO_VICTIM.containsKey(casterId);
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
            float resolve = victim.getData(ModAttachments.RESOLVE.get());
            ServerPlayer controller = sl.getServer().getPlayerList().getPlayer(st.controllerUUID());
            if (resolve <= 0f) {
                victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), st.withSneakAttempt(false));
            } else {
                // Two factors, deliberately separate: how much fight is left in the pool right now,
                // and how much fight the player is capable of at all. Draining the pool is how the
                // curse wears someone down; the trait is why a trained mind wears down slower.
                float charge = at.koopro.wizardsandbeasts.stats.StatResistModifiers
                        .resolveCharge(victim, resolve);
                float imperioProf = Spells.IMPERIO.getProficiencyScalar(victim);
                float resistChance = charge
                        * (imperioProf * 0.5f + 0.5f)
                        * at.koopro.wizardsandbeasts.stats.StatResistModifiers.resistScalar(victim);
                boolean success = sl.random.nextFloat() < resistChance;
                if (success) {
                    clearControl(sl, victim, st.controllerUUID());
                    victim.setData(ModAttachments.RESOLVE.get(), Math.max(0f,
                            resolve - at.koopro.wizardsandbeasts.stats.StatResistModifiers
                                    .resolveCostToBreakFree(victim)));
                    victim.displayClientMessage(
                            Component.literal("You throw off the Imperius Curse!").withStyle(ChatFormatting.GOLD), true);
                    PacketDistributor.sendToPlayer(victim, new ImperioResistS2CPayload(true, 1f));
                    at.koopro.wizardsandbeasts.stats.StatMilestones.onMilestoneTriggered(
                            victim, at.koopro.wizardsandbeasts.stats.MilestoneType.FIRST_IMPERIUS_RESISTED);
                } else {
                    victim.setData(ModAttachments.RESOLVE.get(), Math.max(0f,
                            resolve - at.koopro.wizardsandbeasts.stats.StatResistModifiers
                                    .resolveCostOfFailedAttempt(victim)));
                    // Enduring the curse is how Willpower is trained — the attempt that fails still
                    // teaches, which is the only reason a low-Willpower player can ever climb out.
                    at.koopro.wizardsandbeasts.stats.StatTraining.onImperiusEndured(victim);
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
            // Free the caster's victim, and the player's own bonds, while both are still reachable; dropping
            // only the mapping left a controlled mob controlled for good.
            if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
                releaseVictimOf(level, player.getUUID());
                releaseVictim(player);
            }
            onPlayerDisconnect(event.getEntity().getUUID());
        }
    }
}
