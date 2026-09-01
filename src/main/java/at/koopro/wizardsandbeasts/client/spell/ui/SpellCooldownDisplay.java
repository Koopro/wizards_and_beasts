package at.koopro.wizardsandbeasts.client.spell.ui;

import org.jspecify.annotations.NullMarked;

/**
 * The arithmetic behind every cooldown readout on the spell HUD, kept apart from the drawing so it
 * can be tested without a client.
 *
 * <p>Three rules the callers must not re-derive:
 *
 * <ul>
 *   <li><b>Divide by the applied span, never the base cooldown.</b> Modifiers scale a cooldown's real
 *       duration by up to 3x. Measuring the sweep against the spell's base value would clamp it at
 *       "full" for the first two thirds and read as frozen.</li>
 *   <li><b>Clamp both ends.</b> The client's clock and the server's stamp disagree by up to a tick, so
 *       an unclamped fraction goes slightly negative on the last frame and slightly over 1 on the first.</li>
 *   <li><b>The tick argument may carry a partial.</b> Callers pass {@code gameTick + partialTick} for a
 *       smooth sweep — and pass the whole tick with no partial while the clock is clamped, which is why
 *       this takes a float rather than reaching for the partial itself.</li>
 * </ul>
 */
@NullMarked
public final class SpellCooldownDisplay {

    /** Below this many remaining ticks the numeric readout is suppressed; the sweep alone reads better. */
    public static final float SECONDS_READOUT_THRESHOLD_TICKS = 20f;

    private static final float TICKS_PER_SECOND = 20f;

    private SpellCooldownDisplay() {}

    /** Ticks left before {@code expiryTick}, never negative. */
    public static float remainingTicks(long expiryTick, float nowTicks) {
        return Math.max(0f, expiryTick - nowTicks);
    }

    /**
     * Fraction of the cooldown still to run, in {@code [0, 1]}: 1 the instant it was applied, 0 when it
     * has elapsed. {@code spanTicks} is the duration actually applied, not the spell's base cooldown.
     */
    public static float remainingFraction(long expiryTick, float nowTicks, long spanTicks) {
        if (spanTicks <= 0L) {
            return 0f;
        }
        float fraction = (expiryTick - nowTicks) / (float) spanTicks;
        return Math.max(0f, Math.min(1f, fraction));
    }

    /** Whether the integer-seconds readout should be drawn over the slot. */
    public static boolean showsSecondsReadout(long expiryTick, float nowTicks) {
        return expiryTick - nowTicks > SECONDS_READOUT_THRESHOLD_TICKS;
    }

    /**
     * Seconds shown over the slot: rounded <em>up</em>, so a cooldown reads "1" for its whole last
     * second rather than flashing "0" at a player who still cannot cast.
     */
    public static int secondsRemaining(long expiryTick, float nowTicks) {
        return (int) Math.ceil(remainingTicks(expiryTick, nowTicks) / TICKS_PER_SECOND);
    }
}
