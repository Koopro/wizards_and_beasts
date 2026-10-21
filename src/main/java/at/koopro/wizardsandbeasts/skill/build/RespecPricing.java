package at.koopro.wizardsandbeasts.skill.build;

import org.jspecify.annotations.NullMarked;

/**
 * What changing your mind costs. Two doors, one rule each:
 *
 * <ul>
 *   <li><b>Unlearn</b> — one level of one node, any time the web stays connected. Priced per point refunded
 *       (default one Sickle a point), and never more than starting over would cost. This is the experimentation
 *       door: trying a node and taking it back costs a few Sickles, not the whole web.</li>
 *   <li><b>Start over</b> — every allocation refunded at once for a flat fee (default one Galleon). The first one a
 *       character makes is free, because the first web anyone builds is the one they built before they knew what
 *       the nodes did.</li>
 * </ul>
 *
 * <p>Points are always refunded in full. A percentage loss compounds across respecs and can strand a player under
 * the 60-point cap for good; coins can be earned back, points cannot. With Gringotts off both doors are free —
 * there is no vault to bill, and a fee nobody can pay would make respec unreachable rather than costly.
 *
 * <p>Pure arithmetic; the command and the GUI both quote from here, so the number the player is shown is the
 * number they are charged.
 */
@NullMarked
public final class RespecPricing {

    private RespecPricing() {}

    /** Fee in Knuts to unlearn {@code points} worth of node levels. */
    public static long unlearnFee(int points, int perPointKnuts, int fullRespecKnuts, boolean billingOn) {
        if (!billingOn || points <= 0 || perPointKnuts <= 0) {
            return 0L;
        }
        long perPoint = (long) points * perPointKnuts;
        return fullRespecKnuts > 0 ? Math.min(perPoint, fullRespecKnuts) : perPoint;
    }

    /** Fee in Knuts to refund the whole web. */
    public static long fullRespecFee(boolean freeRespecAvailable, int fullRespecKnuts, boolean billingOn) {
        if (!billingOn || freeRespecAvailable || fullRespecKnuts <= 0) {
            return 0L;
        }
        return fullRespecKnuts;
    }
}
