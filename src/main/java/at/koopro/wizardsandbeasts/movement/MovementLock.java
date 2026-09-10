package at.koopro.wizardsandbeasts.movement;

import at.koopro.wizardsandbeasts.client.petrify.state.ClientPetrifyState;
import at.koopro.wizardsandbeasts.event.spell.SpellCombatControlHandler;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The single answer to "is this player allowed to move right now, and how completely not".
 *
 * <h2>Why this exists</h2>
 *
 * <p>Petrification and a Stupefy stun both used to hold a player still by writing over the
 * result of their movement every server tick — {@code setDeltaMovement(ZERO)}, {@code
 * hurtMarked}, and for petrification a {@code teleportTo} back to a stored position. That
 * loses to the client, which is authoritative for its own player: it predicts a step, the
 * server undoes it, and the player watches themselves stutter against a wall they are not
 * touching, at the cost of a position packet per player per tick.
 *
 * <p>The fix is not a better correction, it is not needing one. {@code
 * PlayerMovementLockMixin} feeds this class into {@code LivingEntity.isImmobile()}, which
 * <em>both sides</em> consult at the same point in the tick, so client prediction and
 * server simulation reach the same answer and there is nothing left to correct.
 *
 * <h2>Both sides, from different places</h2>
 *
 * <p>This class must not touch {@code Minecraft} or any other client-only type — it is
 * called from a common mixin. It follows the contract {@code ClientPoseState} and {@code
 * ClientFormDataState} already hold: the server reads the attachment, the client reads the
 * synced mirror, and neither one imports the other's world.
 *
 * <p>A stun needs no such split. It is a {@code MobEffect}, and vanilla syncs those to the
 * client for free.
 */
@NullMarked
public final class MovementLock {

    private MovementLock() {}

    /**
     * The strongest lock currently on this player, or {@code null} when they may move.
     *
     * <p>Petrification outranks a stun: a wizard who is both stone and stunned is stone.
     */
    @Nullable
    public static MovementLockKind kind(Player player) {
        if (isPetrified(player)) {
            return MovementLockKind.PINNED;
        }
        if (SpellCombatControlHandler.isStunned(player)) {
            return MovementLockKind.ROOTED;
        }
        return null;
    }

    /** True while anything at all is holding this player still. */
    public static boolean isLocked(Player player) {
        return kind(player) != null;
    }

    /** True only for a lock that also takes gravity and knockback — see {@link MovementLockKind#PINNED}. */
    public static boolean isPinned(Player player) {
        return kind(player) == MovementLockKind.PINNED;
    }

    /**
     * Petrification, from whichever side is asking.
     *
     * <p>The client mirror is broadcast to every tracking player rather than only the
     * victim, because a statue has to be a statue to the room. That means this answers
     * correctly for other players too, which is what keeps their predicted movement on
     * this client from disagreeing with the server's.
     */
    private static boolean isPetrified(Player player) {
        return player.level().isClientSide()
                ? ClientPetrifyState.isPetrified(player.getUUID())
                : player.getData(ModAttachments.PETRIFIED_STATE.get()).isPetrified();
    }
}
