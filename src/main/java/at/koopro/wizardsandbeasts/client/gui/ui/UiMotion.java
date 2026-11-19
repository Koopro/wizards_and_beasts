package at.koopro.wizardsandbeasts.client.gui.ui;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;

/**
 * The kit's one source of motion: a value that eases towards 0 or 1 over a fixed time.
 *
 * <p>Wall-clock, not ticks or partial ticks: a GUI keeps drawing while the game is paused, and a hover that only
 * moved on game ticks would stall on the pause menu's neighbours and stutter at 20 Hz everywhere else.
 *
 * <p>Rules for the kit: motion is short ({@link #HOVER_MS}, {@link #OPEN_MS}), it never moves text a reader is
 * reading, and a screen that does not ask for motion gets none. A widget owns one {@code UiMotion} per animated
 * property; stateless draw helpers stay instant.
 */
@NullMarked
public final class UiMotion {

    /** Hover wash in and out. Long enough to read as soft, short enough that a sweep across a row keeps up. */
    public static final int HOVER_MS = 110;
    /** A modal or card arriving. */
    public static final int OPEN_MS = 140;
    /** The pressed nudge after a click. */
    public static final int PRESS_MS = 90;

    private float value;
    private long last = -1L;

    public UiMotion() {
        this(0f);
    }

    public UiMotion(float start) {
        this.value = Mth.clamp(start, 0f, 1f);
    }

    /** Moves towards {@code on ? 1 : 0} by the time since the last call, and returns the raw linear value. */
    public float step(boolean on, int durationMs) {
        long now = Util.getMillis();
        if (last < 0L) {
            // First frame: start where the widget already is (a chip opened selected does not fade in).
            // Something that should animate from rest calls snap(0) first.
            last = now;
            value = on ? 1f : 0f;
            return value;
        }
        float delta = durationMs <= 0 ? 1f : (now - last) / (float) durationMs;
        last = now;
        value = Mth.clamp(value + (on ? delta : -delta), 0f, 1f);
        return value;
    }

    /** {@link #step} through an ease-out curve: fast start, soft landing. */
    public float eased(boolean on, int durationMs) {
        return easeOut(step(on, durationMs));
    }

    /** Snaps to a value without animating (a screen re-init, a widget created already hovered). */
    public void snap(float to) {
        value = Mth.clamp(to, 0f, 1f);
        last = Util.getMillis();
    }

    public float value() {
        return value;
    }

    /** Cubic ease-out. */
    public static float easeOut(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u * u;
    }

    /** {@code argb} with its alpha multiplied by {@code t}. */
    public static int fade(int argb, float t) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(t, 0f, 1f));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** White at alpha {@code t}: the tint that draws a texture at that opacity. */
    public static int alpha(float t) {
        return fade(0xFFFFFFFF, t);
    }

    /** True for the {@link #PRESS_MS} after {@code pressedAt} (a {@link Util#getMillis()} stamp, or -1). */
    public static boolean recentlyPressed(long pressedAt) {
        return pressedAt >= 0L && Util.getMillis() - pressedAt < PRESS_MS;
    }
}
