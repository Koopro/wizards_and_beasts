package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.charge.ApparitionChargeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The one door into Apparition. Every way a player can begin a jump comes through here, so the behaviour
 * cannot fork between them.
 *
 * <p>Two ways in exist today — the ability keybind and the memorised-destination selector — and a spell entry
 * would be a third. Before this class they each called {@code ApparitionServerLogic} directly and learned
 * only a boolean, or nothing at all; anything wanting to know <i>why</i> a jump was refused had to re-derive
 * the gate and risk disagreeing with it.
 *
 * <h2>No destination parameter, deliberately</h2>
 *
 * <p>A blink takes none. The server re-runs its own raycast every tick of the charge and resolves it at
 * release, precisely so a client cannot nominate where it lands, and threading a destination through this
 * signature would put back the thing that design exists to prevent. What a caller <i>can</i> supply is an
 * {@link ApparitionPoint} — a destination the wizard has already memorised, which is a different claim: it
 * says which of their own anchors to travel to, not which coordinates to appear at.
 */
@NullMarked
public final class ApparitionService {

    private ApparitionService() {}

    /**
     * Begins an aimed, line-of-sight jump.
     *
     * @return {@link ApparitionStartResult#STARTED} when a charge is now in flight; otherwise the reason it
     *         is not. The player has already been told, where the reason is one worth telling them.
     */
    public static ApparitionStartResult tryStart(ServerPlayer player) {
        return begin(player, ApparitionTier.BLINK, null);
    }

    /**
     * Begins a jump to a memorised destination.
     *
     * <p>Refuses across dimensions before the gate is even consulted: no wizard Apparates between worlds, and
     * that is a property of the destination rather than of the wizard, so it is answered here.
     */
    public static ApparitionStartResult tryStart(ServerPlayer player, ApparitionPoint destination) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(destination.dimension())) {
            ApparitionStartResult result = ApparitionStartResult.REJECTED_OTHER_DIMENSION;
            ApparitionServerLogic.announce(player, result);
            return result;
        }
        return begin(player, ApparitionTier.ANCHORED, destination);
    }

    /**
     * Hands off to the charge manager, which owns the gate and the message.
     *
     * <p>Not re-checked here first. Evaluating the gate in two places is how the two places start
     * disagreeing, and running it twice would announce every refusal twice over.
     */
    private static ApparitionStartResult begin(ServerPlayer player, ApparitionTier tier,
                                               @org.jspecify.annotations.Nullable ApparitionPoint anchor) {
        return ApparitionChargeManager.begin(player, tier, anchor);
    }
}
