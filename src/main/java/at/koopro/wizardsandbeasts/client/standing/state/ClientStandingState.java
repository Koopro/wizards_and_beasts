package at.koopro.wizardsandbeasts.client.standing.state;

import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingBand;
import at.koopro.wizardsandbeasts.standing.StandingBands;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client cache of the local player's standing, filled by {@code StandingSyncS2CPayload}.
 *
 * <p>Read-only and value-only. It holds resolved numbers and bands rather than the inputs they were
 * computed from, so there is nothing here a client could recompute differently from the server — the
 * sheet draws what the authority decided.
 *
 * <p>{@code received} distinguishes "this world has no standing to report" from "nothing has arrived
 * yet". A wizard genuinely at zero on every axis and a server that never sent a packet look identical
 * in the numbers, and the sheet has to say different things about them.
 */
@NullMarked
public final class ClientStandingState {

    private static final Map<StandingAxis, Float> VALUES = new EnumMap<>(StandingAxis.class);
    private static final Map<StandingAxis, StandingBand> BANDS = new EnumMap<>(StandingAxis.class);

    private static float bound = StandingBands.DEFAULT_BOUND;
    private static boolean received;

    private ClientStandingState() {}

    public static void set(Map<StandingAxis, Float> values,
                           Map<StandingAxis, StandingBand> bands,
                           float incomingBound) {
        VALUES.clear();
        VALUES.putAll(values);
        BANDS.clear();
        BANDS.putAll(bands);
        // A zero or negative bound would make every meter divide by zero when drawn to scale.
        bound = incomingBound > 0.0f ? incomingBound : StandingBands.DEFAULT_BOUND;
        received = true;
    }

    public static float valueOf(StandingAxis axis) {
        return VALUES.getOrDefault(axis, 0.0f);
    }

    public static StandingBand bandOf(StandingAxis axis) {
        return BANDS.getOrDefault(axis, StandingBand.NEUTRAL);
    }

    /** Signed fraction of the axis, {@code −1 … +1}, for drawing a meter from its centre. */
    public static float fractionOf(StandingAxis axis) {
        float fraction = valueOf(axis) / bound;
        return Math.max(-1.0f, Math.min(1.0f, fraction));
    }

    public static float bound() {
        return bound;
    }

    /** True once the server has sent standing at least once this session. */
    public static boolean hasData() {
        return received;
    }

    /**
     * Drops the cache on disconnect. Standing is per-world; without this the sheet would keep showing
     * the last server's alignment on a world — or a server without this mod — that never sends one.
     */
    public static void clear() {
        VALUES.clear();
        BANDS.clear();
        bound = StandingBands.DEFAULT_BOUND;
        received = false;
    }
}
