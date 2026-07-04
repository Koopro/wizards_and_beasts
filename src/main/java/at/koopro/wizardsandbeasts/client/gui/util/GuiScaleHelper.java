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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * Immutable scaled-panel layout: a uniform scale plus a centred, on-screen
     * origin and panel rectangle. Use {@link #fit} for fixed-art panels that
     * must never upscale (avoids texture blur) and {@link #panel} for
     * procedurally drawn panels that may grow to fill large screens.
     */
    public static final class Layout {
        private final float scale;
        private final int panelX;
        private final int panelY;
        private final int panelW;
        private final int panelH;

        private Layout(float scale, int panelX, int panelY, int panelW, int panelH) {
            this.scale = scale;
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelW = panelW;
            this.panelH = panelH;
        }

        /**
         * Downscale-only layout for fixed-art panels. Never upscales (so
         * textures stay crisp); shrinks to fit within {@link #DEFAULT_MARGIN}
         * on every edge of small screens.
         */
        public static Layout fit(int screenW, int screenH, int baseW, int baseH) {
            float scale = computeScale(baseW, baseH, screenW, screenH, DEFAULT_MARGIN);
            int panelW = Math.round(baseW * scale);
            int panelH = Math.round(baseH * scale);
            int panelX = clampedLeft(panelW, screenW, DEFAULT_MARGIN);
            int panelY = clampedTop(panelH, screenH, DEFAULT_MARGIN);
            return new Layout(scale, panelX, panelY, panelW, panelH);
        }

        /**
         * Up-and-down scaling layout for procedurally drawn panels (no texture
         * to blur), clamped to a readable range so the panel neither overflows
         * tiny screens nor floats lost on huge ones.
         */
        public static Layout panel(int screenW, int screenH, int baseW, int baseH) {
            float sx = (screenW - 24.0F) / (float) baseW;
            float sy = (screenH - 36.0F) / (float) baseH;
            float scale = clamp(Math.min(sx, sy), 0.72F, 1.35F);
            int panelW = Math.max(120, Math.round(baseW * scale));
            int panelH = Math.max(90, Math.round(baseH * scale));
            int panelX = (screenW - panelW) / 2;
            int panelY = (screenH - panelH) / 2;
            return new Layout(scale, panelX, panelY, panelW, panelH);
        }

        public float scale() {
            return scale;
        }

        public int panelX() {
            return panelX;
        }

        public int panelY() {
            return panelY;
        }

        public int panelW() {
            return panelW;
        }

        public int panelH() {
            return panelH;
        }

        /** Scale a design-space length to screen-space (minimum 1px). */
        public int s(int value) {
            return Math.max(1, Math.round(value * scale));
        }

        /** Scale a design-space X offset and shift it onto the panel origin. */
        public int x(int designX) {
            return panelX + s(designX);
        }

        /** Scale a design-space Y offset and shift it onto the panel origin. */
        public int y(int designY) {
            return panelY + s(designY);
        }

        /**
         * Push a transform that scales subsequent drawing about the panel
         * origin, so draw code may use raw design-space coordinates
         * ({@code panelX + offset}) and still shrink to fit. Pair with a
         * {@code graphics.pose().popMatrix()} after drawing. Mouse coordinates
         * fed back in must pass through {@link #unmapX}/{@link #unmapY}.
         */
        public void applyScale(net.minecraft.client.gui.GuiGraphics graphics) {
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(panelX * (1.0F - scale), panelY * (1.0F - scale));
            pose.scale(scale, scale);
        }

        /** Map a screen-space mouse X back into the unscaled design space. */
        public double unmapX(double screenX) {
            return panelX + (screenX - panelX) / scale;
        }

        /** Map a screen-space mouse Y back into the unscaled design space. */
        public double unmapY(double screenY) {
            return panelY + (screenY - panelY) / scale;
        }
    }
}
