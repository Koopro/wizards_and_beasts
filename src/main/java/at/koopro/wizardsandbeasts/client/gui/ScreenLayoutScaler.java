package at.koopro.wizardsandbeasts.client.gui;

/**
 * Shared helper for scaling fixed-design GUI layouts to varying screen sizes.
 */
public final class ScreenLayoutScaler {
    private final float scale;
    private final int panelX;
    private final int panelY;
    private final int panelW;
    private final int panelH;

    private ScreenLayoutScaler(float scale, int panelX, int panelY, int panelW, int panelH) {
        this.scale = scale;
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;
        this.panelH = panelH;
    }

    public static ScreenLayoutScaler forScreen(int screenW, int screenH, int baseW, int baseH) {
        float sx = (screenW - 24.0F) / (float) baseW;
        float sy = (screenH - 36.0F) / (float) baseH;
        float scale = clamp(Math.min(sx, sy), 0.72F, 1.35F);
        int panelW = Math.max(120, Math.round(baseW * scale));
        int panelH = Math.max(90, Math.round(baseH * scale));
        int panelX = (screenW - panelW) / 2;
        int panelY = (screenH - panelH) / 2;
        return new ScreenLayoutScaler(scale, panelX, panelY, panelW, panelH);
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

    public int s(int value) {
        return Math.max(1, Math.round(value * scale));
    }

    public int x(int designX) {
        return panelX + s(designX);
    }

    public int y(int designY) {
        return panelY + s(designY);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
