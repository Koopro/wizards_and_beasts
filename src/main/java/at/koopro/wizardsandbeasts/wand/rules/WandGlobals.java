package at.koopro.wizardsandbeasts.wand.rules;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The server-wide wand rules the stat resolver and the allegiance rules read, with shipped defaults. Pure.
 *
 * <p>Two layers, like {@code SpellTuning}: the local layer is the owning side's (the server, from {@code Config});
 * the remote layer is what a remote server synced, and wins on that client while set, so a wand's tooltip shows the
 * server's numbers. The integrated client never adopts a synced copy — its local layer already is the server's.
 *
 * @param affinityStrength  how much a wood's and core's cast modifiers count: 1 as authored, 0 none, 2 twice
 * @param bondGrowth        multiplies how fast a wand's bond deepens with use
 * @param defeatsToWin      defeats of its master that win an ordinary wand, before its wood's temperament
 * @param neglectLoss       multiplies how fast an unused wand's bond cools
 * @param foreignBackfire   whether a wand that turns on a stranger (hawthorn) backfires in foreign hands
 */
@NullMarked
public final class WandGlobals {

    public record Values(float affinityStrength, float bondGrowth, int defeatsToWin, float neglectLoss,
                         boolean foreignBackfire) {
        public static final Values SHIPPED = new Values(1.0f, 1.0f, 1, 1.0f, true);
    }

    private static volatile Values local = Values.SHIPPED;
    private static volatile @Nullable Values remote;

    private WandGlobals() {}

    public static Values current() {
        Values synced = remote;
        return synced != null ? synced : local;
    }

    public static Values local() {
        return local;
    }

    public static synchronized void publishLocal(Values values) {
        local = values;
    }

    public static synchronized void acceptRemote(Values values) {
        remote = values;
    }

    public static synchronized void clearRemote() {
        remote = null;
    }

    /** A cast modifier (neutral at {@code neutral}) moved toward or away from neutral by the affinity strength. */
    public static float scaled(float value, float neutral) {
        return neutral + (value - neutral) * current().affinityStrength();
    }
}
