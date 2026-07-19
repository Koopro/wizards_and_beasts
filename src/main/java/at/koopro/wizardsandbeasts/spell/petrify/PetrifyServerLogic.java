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
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class PetrifyServerLogic {

    /** ~100 in-game days, per canon's "petrification wears off eventually" if never cured. */
    private static final long NATURAL_CURE_TICKS = 2_400_000L;
    /** Each Bezoar dose shortens the remaining natural-cure window by ~10 in-game days. */
    private static final long BEZOAR_TIME_REDUCTION_TICKS = 240_000L;

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
     * strip (pure attachment + tick-pinning), unlike the folk-remedy "cures most poisons" framing —
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
            player.teleportTo(state.x(), state.y(), state.z());
            player.setYRot(state.yRot());
            player.setXRot(state.xRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            player.stopUsingItem();
        }
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
