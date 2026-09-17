package at.koopro.wizardsandbeasts.render.outline;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * One outline as it crosses the wire: a colour, and when it ends.
 *
 * <p>The expiry is for <em>drawing</em> only. The server still decides when an outline is gone and sends
 * the clear; the client uses {@link #expiresAt} to ease the alpha down over the last {@link #FADE_TICKS}
 * so a reveal fades instead of blinking out. Both clocks are the overworld game time, which the client
 * receives from the server every second and advances between, so they agree to within a tick.
 *
 * @param argb      packed ARGB; {@code 0} means "no outline" and is only ever sent as a clear
 * @param expiresAt game time the outline ends, or {@link #NEVER} for a permanent (debug) outline
 */
@NullMarked
public record OutlineEntry(int argb, long expiresAt) {

    /** Expiry of an outline that does not expire, and so never fades. */
    public static final long NEVER = Long.MAX_VALUE;

    /** How long the fade-out takes: the last second. */
    public static final int FADE_TICKS = 20;

    /** The clear. */
    public static final OutlineEntry NONE = new OutlineEntry(0, NEVER);

    public static OutlineEntry permanent(int argb) {
        return new OutlineEntry(argb, NEVER);
    }

    public boolean isNone() {
        return argb == 0;
    }

    /**
     * The colour to draw at this moment: {@link #argb} until the last {@link #FADE_TICKS}, then its alpha
     * eased linearly to nothing. Returns {@code 0} once fully faded, so a caller that treats {@code 0} as
     * "no outline" stops drawing rather than drawing a transparent one.
     */
    public int colourAt(long gameTime, float partialTick) {
        int faded = ARGB.multiplyAlpha(argb, fade(expiresAt, gameTime, partialTick));
        // multiplyAlpha can round the alpha to 0 while keeping the RGB, which is non-zero and so still reads
        // as "draw an outline" everywhere downstream — a transparent one. Collapse it to the real clear.
        return ARGB.alpha(faded) == 0 ? 0 : faded;
    }

    /** 1 before the fade window, falling to 0 at {@code expiresAt}; always 1 for {@link #NEVER}. */
    public static float fade(long expiresAt, long gameTime, float partialTick) {
        if (expiresAt == NEVER) {
            return 1.0f;
        }
        double remaining = expiresAt - gameTime - (double) partialTick;
        return (float) Mth.clamp(remaining / FADE_TICKS, 0.0, 1.0);
    }
}
