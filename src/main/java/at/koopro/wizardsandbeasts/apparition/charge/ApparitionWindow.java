package at.koopro.wizardsandbeasts.apparition.charge;

import at.koopro.wizardsandbeasts.apparition.ApparitionTier;
import org.jspecify.annotations.NullMarked;

/**
 * Deliberation window maths — the "release at the right moment" half of the Three Ds. Pure and static so the
 * whole timing model is testable without a server.
 *
 * <pre>
 * windowTicks = floorTicks + floor(proficiency * PROFICIENCY_TICKS)
 * windowOpen  = chargeDuration
 * windowClose = chargeDuration + windowTicks
 *
 * missTicks   = 0                        released inside the window
 *             = windowOpen - releaseTick released early
 *             = FORCED_DISCHARGE         never released before windowClose
 * </pre>
 *
 * <p>A novice gets five ticks to hit; mastery widens it to twelve. Releasing early misses by exactly how
 * early you were, so impatience is punished proportionally rather than categorically.
 */
@NullMarked
public final class ApparitionWindow {

    /** Window width at proficiency 0, before any skill node raises it. */
    public static final int BASE_FLOOR_TICKS = 5;
    /** Extra window width earned across the whole proficiency curve. */
    public static final int PROFICIENCY_TICKS = 7;

    /**
     * Sentinel {@code missTicks} for an attempt that was never released. Deliberately {@code MAX_VALUE} so
     * that any accidental comparison against a real miss sorts it worst; callers must short-circuit on it
     * rather than inflate it, or the arithmetic overflows.
     */
    public static final int FORCED_DISCHARGE = Integer.MAX_VALUE;

    private ApparitionWindow() {}

    /**
     * Width of the window in ticks.
     *
     * @param proficiency ability proficiency, clamped to {@code [0, 1]}
     * @param floorTicks  the floor, normally {@link #BASE_FLOOR_TICKS}; a skill node may raise it
     */
    public static int windowTicks(float proficiency, int floorTicks) {
        float clamped = Math.max(0.0f, Math.min(1.0f, proficiency));
        return Math.max(1, floorTicks) + (int) Math.floor(clamped * PROFICIENCY_TICKS);
    }

    public static int windowTicks(float proficiency) {
        return windowTicks(proficiency, BASE_FLOOR_TICKS);
    }

    /** The tick at which the window opens — charge completion. */
    public static int windowOpen(ApparitionTier tier) {
        return tier.chargeDurationTicks();
    }

    /** The last tick on which a release still counts; passing it discharges the attempt. */
    public static int windowClose(ApparitionTier tier, int windowTicks) {
        return tier.chargeDurationTicks() + windowTicks;
    }

    /**
     * Raw miss for a release at {@code releaseTick}, before destabilization inflates it.
     *
     * @param releaseTick ticks elapsed since the charge began
     */
    public static int missTicks(int releaseTick, int windowOpen, int windowClose) {
        if (releaseTick < windowOpen) {
            return windowOpen - releaseTick;
        }
        if (releaseTick > windowClose) {
            return FORCED_DISCHARGE;
        }
        return 0;
    }

    public static boolean isForcedDischarge(int missTicks) {
        return missTicks == FORCED_DISCHARGE;
    }
}
