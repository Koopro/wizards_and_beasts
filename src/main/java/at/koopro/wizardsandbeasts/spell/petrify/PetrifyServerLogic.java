package at.koopro.wizardsandbeasts.spell.petrify;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.petrify.PetrifiedStateSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Basilisk-gaze petrification: a player caught by a glancing gaze (see {@code DeathGazeGoal}) is
 * frozen solid — pinned in place, unable to act — until cured with a Mandrake Restoration Draught.
 * No tick-down, unlike {@link at.koopro.wizardsandbeasts.spell.imperio.ImperioControlState}: canon
 * petrification lasts until cured, not for a fixed duration.
 *
 * <p>Input-blocking mirrors {@link at.koopro.wizardsandbeasts.event.spell.SpellCombatControlHandler}'s
 * stun-gate exactly (same event set, same cancel style) rather than adding per-call-site guards to
 * spellcasting/combat code — cancelling the input events upstream is the established pattern here.
 *
 * <h2>Where the freeze lives</h2>
 *
 * <p>Not here. {@link at.koopro.wizardsandbeasts.movement.MovementLock} reports petrification as
 * {@code PINNED} and {@code PlayerMovementLockMixin} acts on it inside
 * {@code LivingEntity.isImmobile()} and {@code Player.travel} — on both sides, at the same point in
 * the tick, so the client never predicts a step the server has to undo. What is left in this class
 * is the state, the cure paths, the action gates, and {@link #correctDrift} as the fallback against
 * a client that ignores all of it.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class PetrifyServerLogic {

    /** ~100 in-game days, per canon's "petrification wears off eventually" if never cured. */
    private static final long NATURAL_CURE_TICKS = 2_400_000L;
    /** Each Bezoar dose shortens the remaining natural-cure window by ~10 in-game days. */
    private static final long BEZOAR_TIME_REDUCTION_TICKS = 240_000L;

    /**
     * How far a petrified player may be from where they were turned to stone before the server
     * puts them back.
     *
     * <p>Wide enough that ordinary float error, a shove resolved on the client, or a block placed
     * under them never trips it; narrow enough that a client which ignores
     * {@code PlayerMovementLockMixin} outright gets nowhere. The correction is a fallback for a
     * client that is lying, not the mechanism — see the class javadoc.
     */
    private static final double DRIFT_TOLERANCE = 0.5;

    private PetrifyServerLogic() {}

    public static void beginPetrify(ServerLevel level, LivingEntity target) {
        if (!(target instanceof ServerPlayer player) || isPetrified(player)) {
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setData(ModAttachments.PETRIFIED_STATE.get(),
                new PetrifiedState(true, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                        level.getGameTime()));
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.6f);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
                player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        PetrifiedStateSyncS2CPayload.syncToTracking(player, true);
    }

    public static void cure(ServerLevel level, LivingEntity target) {
        if (!(target instanceof ServerPlayer player) || !isPetrified(player)) {
            return;
        }
        player.setData(ModAttachments.PETRIFIED_STATE.get(), PetrifiedState.DEFAULT);
        level.playSound(null, player.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
                player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.8, 0.4, 0.08);
        PetrifiedStateSyncS2CPayload.syncToTracking(player, false);
    }

    /**
     * Bezoar dose: shortens the remaining natural-cure window rather than lifting the stone state
     * outright. This mod's petrification has no MobEffect/slowness component on the lethal path to
     * strip (a pure attachment, enforced through {@code MovementLock}), unlike the folk-remedy "cures
     * most poisons" framing —
     * accelerating the clock is the closest equivalent that doesn't trivialise the full Mandrake cure.
     */
    public static void partialCure(ServerLevel level, LivingEntity target) {
        if (!(target instanceof ServerPlayer player) || !isPetrified(player)) {
            return;
        }
        PetrifiedState state = player.getData(ModAttachments.PETRIFIED_STATE.get());
        long shifted = state.petrifiedAtGameTime() - BEZOAR_TIME_REDUCTION_TICKS;
        if (level.getGameTime() - shifted >= NATURAL_CURE_TICKS) {
            cure(level, player);
            return;
        }
        player.setData(ModAttachments.PETRIFIED_STATE.get(), state.withPetrifiedAtGameTime(shifted));
        level.playSound(null, player.blockPosition(), SoundEvents.STONE_STEP, SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    private static void naturalCure(ServerLevel level, ServerPlayer player) {
        player.setData(ModAttachments.PETRIFIED_STATE.get(), PetrifiedState.DEFAULT);
        level.playSound(null, player.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
                player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wizards_and_beasts.basilisk.petrification_faded"), true);
        PetrifiedStateSyncS2CPayload.syncToTracking(player, false);
    }

    public static boolean isPetrified(Player player) {
        return player.getData(ModAttachments.PETRIFIED_STATE.get()).isPetrified();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PetrifiedState state = player.getData(ModAttachments.PETRIFIED_STATE.get());
            if (!state.isPetrified()) {
                continue;
            }
            ServerLevel playerLevel = (ServerLevel) player.level();
            if (playerLevel.getGameTime() - state.petrifiedAtGameTime() >= NATURAL_CURE_TICKS) {
                naturalCure(playerLevel, player);
                continue;
            }
            // Stone does not draw a bow. The movement half is gone from here entirely; this is the
            // one thing left that a client could still have started before the stone set in.
            player.stopUsingItem();
            correctDrift(player, state);
        }
    }

    /**
     * Puts a petrified player back only if they have actually left.
     *
     * <p>This used to run unconditionally — {@code teleportTo} plus a zeroed velocity, every tick,
     * for every statue. That is a position packet per player per tick, and it loses the argument it
     * is having: the client is authoritative for its own player, so it predicted a step, the server
     * undid it, and the player watched themselves stutter. {@code PlayerMovementLockMixin} now stops
     * the step from being taken on <em>both</em> sides, so in the normal case there is no drift and
     * this sends nothing at all.
     *
     * <p>It stays as the enforcement half. A client that does not run the mixin — or one that has
     * been told not to — still cannot walk a statue away.
     */
    private static void correctDrift(ServerPlayer player, PetrifiedState state) {
        if (player.distanceToSqr(state.x(), state.y(), state.z()) <= DRIFT_TOLERANCE * DRIFT_TOLERANCE) {
            return;
        }
        player.teleportTo(state.x(), state.y(), state.z());
        player.setYRot(state.yRot());
        player.setXRot(state.xRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && isPetrified(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof ServerPlayer player && isPetrified(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && isPetrified(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isPetrified(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && isPetrified(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isPetrified(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPetrified(player)) {
            event.setCanceled(true);
        }
    }

    /** Re-broadcast on (re)join so trackers who weren't around at petrify-time still see the statue. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPetrified(player)) {
            PetrifiedStateSyncS2CPayload.syncToTracking(player, true);
        }
    }
}
