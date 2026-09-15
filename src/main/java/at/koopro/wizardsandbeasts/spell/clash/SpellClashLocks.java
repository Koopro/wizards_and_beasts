package at.koopro.wizardsandbeasts.spell.clash;

import at.koopro.wizardsandbeasts.entity.spell.SpellClashEntity;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Who is locked in a spell clash right now, and whose hold an admin has pinned.
 *
 * <p>A lock is held with the wand: after the bolts meet, each caster presses and holds again, and that hold
 * feeds the lock and nothing else — no channel beam starts, and letting go casts nothing (see
 * {@code CastReleaseGate.CLASH_HOLD}). This class is where the rest of the wand pipeline asks whether a
 * player is in one.
 *
 * <p>Server-side, and keyed by player through {@link PlayerScopedState}, so a caster who logs out mid-lock
 * leaves nothing behind. Non-player casters are never entered: nothing presses a button for them, so they
 * count as holding for as long as they are alive.
 */
@NullMarked
public final class SpellClashLocks {

    private static final PlayerScopedState<SpellClashEntity> LOCKS = PlayerScopedState.create("spell-clash-locks");

    /**
     * Players whose hold is pinned on by {@code /wandb magic spell clash hold}. One person cannot hold
     * right-click in two game windows at once, so testing a lock alone needs one side held for them.
     */
    private static final PlayerScopedState<Boolean> PINNED = PlayerScopedState.create("spell-clash-pinned-holds");

    private SpellClashLocks() {}

    public static boolean isLocked(UUID playerId) {
        SpellClashEntity clash = LOCKS.get(playerId);
        return clash != null && !clash.isRemoved();
    }

    public static boolean isLocked(Player player) {
        return isLocked(player.getUUID());
    }

    /**
     * Enters a caster into a lock.
     *
     * <p>A player who is already holding the wand again when the bolts meet keeps that hold, and it now
     * feeds the lock: its release is marked so it casts nothing, and any channel it was driving ends.
     */
    public static void enter(LivingEntity caster, SpellClashEntity clash) {
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        LOCKS.put(player.getUUID(), clash);
        if (isHoldingWand(player)) {
            WandCastSessions.markClashHold(player);
            WandBeamChannelLogic.endChannel(player);
        }
    }

    /** Takes a player out of this lock. A no-op if they have since been entered into another. */
    public static void leave(UUID playerId, SpellClashEntity clash) {
        if (LOCKS.get(playerId) == clash) {
            LOCKS.remove(playerId);
        }
    }

    public static void pinHold(UUID playerId, boolean pinned) {
        if (pinned) {
            PINNED.put(playerId, Boolean.TRUE);
        } else {
            PINNED.remove(playerId);
        }
    }

    public static boolean isPinned(UUID playerId) {
        return PINNED.contains(playerId);
    }

    /** Whether this caster is holding their side of a lock this tick. */
    public static boolean isHolding(LivingEntity caster) {
        if (!caster.isAlive()) {
            return false;
        }
        if (!(caster instanceof Player player)) {
            return true;
        }
        return isPinned(player.getUUID()) || isHoldingWand(player);
    }

    private static boolean isHoldingWand(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof WandItem;
    }
}
