package at.koopro.wizardsandbeasts.client.gui.util;

/**
 * Keeps identity colours legible when they are used as <em>text</em> colour.
 *
 * <p>Heritages, lineages and spell categories each carry a signature colour picked to look right as a
 * sigil, bar or outline. Drawing that same colour as a glyph over a fixed panel is where it breaks: a
 * near-white lineage tint (Veela {@code 0xFFE0F5}) vanishes into parchment, and a deep purple
 * (Dark Arts {@code 0x8B00FF}, Pure-blood {@code 0x220042}) vanishes into a dark panel. The colour is
 * still the right identity; only its luminance is wrong for that particular ground.</p>
 *
 * <p>{@link #readableOn} keeps the hue and slides the luminance toward white or black — whichever the
 * background has headroom for — until the pair clears a WCAG contrast ratio. Colours that already read
 * fine come back untouched, so this never repaints art that was already correct.</p>
 */
public final class UiContrast {

    /** WCAG AA for body text. */
    public static final double AA_TEXT = 4.5;
    /** WCAG AA for large/bold text — the right target for headers drawn with a drop shadow. */
    public static final double AA_LARGE = 3.0;

    private static final int STEPS = 20;

    private UiContrast() {}

    /** {@link #readableOn(int, int, double)} at the {@link #AA_TEXT} target. */
    public static int readableOn(int fg, int bg) {
        return readableOn(fg, bg, AA_TEXT);
    }

    /**
     * Returns {@code fg} (opaque) nudged toward white or black until it reaches {@code minRatio} against
     * {@code bg}. Alpha on either argument is ignored: pass the colour the ground actually resolves to.
     */
    public static int readableOn(int fg, int bg, double minRatio) {
        int base = fg | 0xFF000000;
        if (ratio(base, bg) >= minRatio) {
            return base;
        }
        // Dark grounds have room to push the ink lighter; light grounds have room to push it darker.
        boolean lighten = luminance(bg) < 0.5;
        int candidate = base;
        // Walk t over 0..2: the first unit gains brightness with the hue intact, the second gives hue up
        // only as far as the ratio demands. Darkening is scaling, so it never needs the second unit.
        int total = lighten ? STEPS * 2 : STEPS;
        for (int step = 1; step <= total; step++) {
            candidate = lighten ? lighten(base, (float) step / STEPS) : lerp(base, 0x000000, (float) step / STEPS);
            if (ratio(candidate, bg) >= minRatio) {
                break;
            }
        }
        return candidate;
    }

    /**
     * Brightens {@code color} by {@code t}. For {@code t <= 1} every channel is scaled by a common gain,
     * which holds hue and saturation exactly — a deep purple becomes a vivid purple, not a grey. Only past
     * {@code t = 1}, where the brightest channel is already clipped, does it bleed toward white.
     */
    private static int lighten(int color, float t) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max == 0) {
            return lerp(color, 0xFFFFFF, Math.min(1.0F, t)); // Pure black has no hue to preserve.
        }
        float gain = 1.0F + Math.min(1.0F, t) * (255.0F / max - 1.0F);
        int scaled = 0xFF000000
                | (Math.min(255, Math.round(r * gain)) << 16)
                | (Math.min(255, Math.round(g * gain)) << 8)
                | Math.min(255, Math.round(b * gain));
        return t <= 1.0F ? scaled : lerp(scaled, 0xFFFFFF, t - 1.0F);
    }

    /** WCAG contrast ratio of two colours, 1.0 (identical) .. 21.0 (black on white). */
    public static double ratio(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** WCAG relative luminance of an ARGB colour (alpha ignored). */
    public static double luminance(int argb) {
        double r = channel((argb >> 16) & 0xFF);
        double g = channel((argb >> 8) & 0xFF);
        double b = channel(argb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
