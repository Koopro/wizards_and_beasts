package at.koopro.wizardsandbeasts.standing;

import org.jspecify.annotations.NullMarked;

/**
 * The arithmetic of standing: clamping to bounds, banding a value, and composing the derived axes.
 *
 * <p>Pure by design — no {@code Config}, no attachment, no Minecraft. Every threshold and bound is a
 * parameter, so the numbers a player experiences can be pinned by unit tests without a game, and a
 * server can retune them without this class knowing that servers exist. (The standing rule in this
 * repository: a pure class that reads {@code Config} takes the test JVM down with it.)
 */
@NullMarked
public final class StandingBands {

    /** The shipped bound. A server may narrow or widen it; nothing here assumes this value. */
    public static final float DEFAULT_BOUND = 100.0f;
    /** Percent of the bound at which an axis starts leaning. */
    public static final int DEFAULT_LEAN_PERCENT = 25;
    /** Percent of the bound at which an axis becomes a fact about the character. */
    public static final int DEFAULT_STRONG_PERCENT = 60;

    private StandingBands() {}

    /** Clamps {@code value} to {@code [-bound, +bound]}, mapping NaN to neutral rather than poisoning the save. */
    public static float clamp(float value, float bound) {
        if (Float.isNaN(value)) {
            // A NaN reaching the attachment would survive every later comparison as false and render as
            // "NaN" on the sheet. Datapack floats and command arguments both reach this, so it is a real
            // input, not a defensive nicety.
            return 0.0f;
        }
        float limit = Math.abs(bound);
        return Math.max(-limit, Math.min(limit, value));
    }

    /** Clamps a one-sided meter (the light pole, the corruption pole) to {@code [0, bound]}. */
    public static float clampUnipolar(float value, float bound) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(Math.abs(bound), value));
    }

    /**
     * Which band {@code value} falls in.
     *
     * @param bound        axis magnitude, {@code > 0}
     * @param leanPercent  percent of bound at which the axis leans
     * @param strongPercent percent of bound at which the axis is strong; must exceed {@code leanPercent}
     *                      or the leaning band would be unreachable
     */
    public static StandingBand bandFor(float value, float bound, int leanPercent, int strongPercent) {
        float limit = Math.abs(bound);
        if (limit <= 0.0f || Float.isNaN(value)) {
            return StandingBand.NEUTRAL;
        }
        // Order the two thresholds rather than trusting the caller: a config where strong < lean would
        // otherwise silently delete the leaning band instead of reporting anything.
        //
        // Computed in double, and deliberately. In float, `100f * (60 / 100.0f)` is 60.000004 -- 0.6f
        // is not representable -- so a wizard sitting exactly on the documented 60% threshold banded
        // one step lower than the number on their own character sheet said they should. Double makes
        // the round-trip exact for every percent, and the comparison is the whole contract here.
        double lean = (double) limit * Math.min(leanPercent, strongPercent) / 100.0;
        double strong = (double) limit * Math.max(leanPercent, strongPercent) / 100.0;
        double magnitude = Math.abs((double) value);

        if (magnitude < lean) {
            return StandingBand.NEUTRAL;
        }
        boolean positive = value > 0.0f;
        if (magnitude >= strong) {
            return positive ? StandingBand.STRONG_POSITIVE : StandingBand.STRONG_NEGATIVE;
        }
        return positive ? StandingBand.LEANING_POSITIVE : StandingBand.LEANING_NEGATIVE;
    }

    /** Band at the shipped thresholds. Convenience for tests and for callers with no config to hand. */
    public static StandingBand bandFor(float value) {
        return bandFor(value, DEFAULT_BOUND, DEFAULT_LEAN_PERCENT, DEFAULT_STRONG_PERCENT);
    }

    /**
     * The alignment axis: the light pole minus the dark one.
     *
     * <p>Both inputs are one-sided meters that already exist independently — light is earned by
     * protective magic, dark corruption by the acts that have always accrued it — and this is the only
     * place they are put on one line. A wizard deep in both is not neutral by accident; they are
     * genuinely torn, and the axis says so.
     */
    public static float alignmentOf(float light, float darkCorruption, float bound) {
        return clamp(clampUnipolar(light, bound) - clampUnipolar(darkCorruption, bound), bound);
    }

    /**
     * The Ministry axis: credit for holding office, less the heat currently on you.
     *
     * <p>Nothing here is stored. Notoriety and rank are the criminal record's own fields, so a pardon
     * or a fine paid moves this axis without any code in the standing system running at all — which is
     * the point of deriving it rather than mirroring it into a second number that could drift.
     *
     * @param rankCredit  standing conferred by Ministry office, {@code 0} for a private citizen
     * @param notoriety   current heat, {@code 0 … 100}
     */
    public static float ministryOf(float rankCredit, float notoriety, float bound) {
        return clamp(clampUnipolar(rankCredit, bound) - clampUnipolar(notoriety, bound), bound);
    }
}
