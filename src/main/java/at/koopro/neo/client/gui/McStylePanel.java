package at.koopro.neo.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Minecraft-style flat panel fill and beveled border (used by debug / tool screens).
 */
public final class McStylePanel {

    private McStylePanel() {}

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h,
                                 int fillColor, int highlightTopLeft, int shadowBottomRight) {
        g.fill(x, y, x + w, y + h, fillColor);
        g.fill(x, y, x + w, y + 1, highlightTopLeft);
        g.fill(x, y, x + 1, y + h, highlightTopLeft);
        g.fill(x + 1, y + h - 1, x + w, y + h, shadowBottomRight);
        g.fill(x + w - 1, y + 1, x + w, y + h, shadowBottomRight);
    }

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h,
                                  int highlightTopLeft, int shadowBottomRight) {
        g.fill(x, y, x + w, y + 1, highlightTopLeft);
        g.fill(x, y, x + 1, y + h, highlightTopLeft);
        g.fill(x, y + h - 1, x + w, y + h, shadowBottomRight);
        g.fill(x + w - 1, y, x + w, y + h, shadowBottomRight);
    }
}
