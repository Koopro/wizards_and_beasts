package at.koopro.wizardsandbeasts.client.gui.util;

/**
 * Shared clamp + downscale math for fixed-size GUIs on small screens.
 * Computes a uniform shrink factor (never upscales) and a centred,
 * margin-clamped origin for the scaled panel.
 */
public final class GuiScaleHelper {

    /** Default breathing room, in scaled pixels, on each screen edge. */
    public static final int DEFAULT_MARGIN = 8;

    private GuiScaleHelper() {
    }

    /**
     * Compute a uniform scale factor such that a GUI of (naturalW x naturalH)
     * fits within (screenW x screenH) with the given margin on each side.
     * Returns 1.0 if the GUI already fits; returns a value below 1.0 if it
     * does not. Never returns a value above 1.0 (does not upscale).
     */
    public static float computeScale(int naturalW, int naturalH,
                                     int screenW, int screenH,
                                     int margin) {
        float sx = (screenW - 2.0F * margin) / (float) naturalW;
        float sy = (screenH - 2.0F * margin) / (float) naturalH;
        return Math.min(1.0F, Math.min(sx, sy));
    }

    /**
     * Left origin that centres a panel of {@code scaledW} within the screen,
     * clamped so no edge goes outside {@code [margin, screenW - margin]}.
     */
    public static int clampedLeft(int scaledW, int screenW, int margin) {
        return clamp((screenW - scaledW) / 2, margin, screenW - scaledW - margin);
    }

    /**
     * Top origin that centres a panel of {@code scaledH} within the screen,
     * clamped so no edge goes outside {@code [margin, screenH - margin]}.
     */
    public static int clampedTop(int scaledH, int screenH, int margin) {
        return clamp((screenH - scaledH) / 2, margin, screenH - scaledH - margin);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
