package at.koopro.wizardsandbeasts.visual.beam;

/**
 * The preview's loop for showing a beam's fades: brighten over {@code fadeIn}, hold, dim over {@code fadeOut}, a short
 * gap, repeat. Pure math so the loop can be tested without a render context; a beam with neither fade just holds.
 */
public final class BeamFadeCycle {

    /** Ticks the preview holds a beam at full brightness each loop. */
    public static final int HOLD_TICKS = 40;
    /** Ticks of darkness between loops, so the fade-in reads as a fresh start. */
    public static final int GAP_TICKS = 10;

    private BeamFadeCycle() {}

    public static int length(int fadeIn, int fadeOut) {
        return Math.max(0, fadeIn) + HOLD_TICKS + Math.max(0, fadeOut) + GAP_TICKS;
    }

    /** Brightness 0..1 at {@code ticks} (may include a partial tick) since the preview started. */
    public static float alpha(float ticks, int fadeIn, int fadeOut) {
        if (fadeIn <= 0 && fadeOut <= 0) {
            return 1f;
        }
        int in = Math.max(0, fadeIn);
        int out = Math.max(0, fadeOut);
        float age = ticks % length(in, out);
        if (age < in) {
            return age / in;
        }
        age -= in;
        if (age < HOLD_TICKS) {
            return 1f;
        }
        age -= HOLD_TICKS;
        if (age < out) {
            return 1f - age / out;
        }
        return 0f;
    }
}
