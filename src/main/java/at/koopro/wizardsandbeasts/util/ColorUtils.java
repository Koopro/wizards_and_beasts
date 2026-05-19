package at.koopro.wizardsandbeasts.util;

/**
 * ARGB color utilities for GUI rendering.
 * All colors use Minecraft's ARGB format: {@code 0xAARRGGBB}.
 */
public final class ColorUtils {

    private ColorUtils() {}

    // ── Common colors ──────────────────────────────────────────────────

    public static final int WHITE       = 0xFFFFFFFF;
    public static final int BLACK       = 0xFF000000;
    public static final int GOLD        = 0xFFFFD700;
    public static final int SILVER      = 0xFFC0C0C0;
    public static final int BRONZE      = 0xFFCD7F32;
    public static final int PARCHMENT   = 0xFFE8D5B3;
    public static final int GREEN       = 0xFF88FF88;
    public static final int RED         = 0xFFFF6666;
    public static final int DIM_GRAY    = 0xFF998877;
    public static final int LIGHT_GRAY  = 0xFFCCCCCC;

    // ── ARGB operations ────────────────────────────────────────────────

    /**
     * Creates an ARGB color from an RGB value and alpha (0-255).
     */
    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Creates an ARGB color from an RGB value and alpha (0.0-1.0).
     */
    public static int withAlpha(int rgb, float alpha) {
        return withAlpha(rgb, (int) (alpha * 255));
    }

    /**
     * Extracts the alpha component (0-255) from an ARGB color.
     */
    public static int getAlpha(int argb) {
        return (argb >> 24) & 0xFF;
    }

    /**
     * Extracts the RGB portion from an ARGB color (strips alpha).
     */
    public static int getRGB(int argb) {
        return argb & 0x00FFFFFF;
    }

    /**
     * Returns the color with a modified alpha, preserving the RGB components.
     */
    public static int setAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Returns the color with a modified alpha (0.0-1.0), preserving the RGB.
     */
    public static int setAlpha(int argb, float alpha) {
        return setAlpha(argb, (int) (alpha * 255));
    }

    /**
     * Creates a semi-transparent black background. Common for HUD panels.
     * @param alpha 0-255
     */
    public static int blackBg(int alpha) {
        return alpha << 24;
    }

    /**
     * Packs RGBA components (each 0-255) into a single ARGB int.
     */
    public static int pack(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Packs RGB components (each 0-255) into a fully opaque ARGB int.
     */
    public static int pack(int r, int g, int b) {
        return pack(r, g, b, 255);
    }

    // ── Component extraction ────────────────────────────────────────────

    public static int getRed(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int getGreen(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int getBlue(int argb) {
        return argb & 0xFF;
    }

    // ── Color manipulation ──────────────────────────────────────────────

    /**
     * Linearly interpolates between two ARGB colors.
     * @param t blend factor, 0.0 = colorA, 1.0 = colorB
     */
    public static int lerp(int colorA, int colorB, float t) {
        t = Math.clamp(t, 0.0f, 1.0f);
        int a = (int) (getAlpha(colorA) + (getAlpha(colorB) - getAlpha(colorA)) * t);
        int r = (int) (getRed(colorA) + (getRed(colorB) - getRed(colorA)) * t);
        int g = (int) (getGreen(colorA) + (getGreen(colorB) - getGreen(colorA)) * t);
        int b = (int) (getBlue(colorA) + (getBlue(colorB) - getBlue(colorA)) * t);
        return pack(r, g, b, a);
    }

    /**
     * Brightens a color by blending toward white.
     * @param amount 0.0 = no change, 1.0 = fully white
     */
    public static int brighten(int argb, float amount) {
        return lerp(argb, withAlpha(WHITE, getAlpha(argb)), amount);
    }

    /**
     * Darkens a color by blending toward black.
     * @param amount 0.0 = no change, 1.0 = fully black
     */
    public static int darken(int argb, float amount) {
        return lerp(argb, withAlpha(BLACK, getAlpha(argb)), amount);
    }

    /**
     * Adjusts color opacity by multiplying the existing alpha.
     * @param factor 0.0 = transparent, 1.0 = no change, 2.0 = double alpha (clamped)
     */
    public static int fadeAlpha(int argb, float factor) {
        int a = Math.clamp((int) (getAlpha(argb) * factor), 0, 255);
        return setAlpha(argb, a);
    }
}
