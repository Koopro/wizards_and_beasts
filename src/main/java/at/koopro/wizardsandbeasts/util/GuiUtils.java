package at.koopro.wizardsandbeasts.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Common GUI drawing helpers to reduce boilerplate in screens and overlays.
 */
public final class GuiUtils {

    private GuiUtils() {}

    // ── Text drawing ───────────────────────────────────────────────────

    /**
     * Draws text centered horizontally within the given bounds.
     */
    public static void drawCenteredText(GuiGraphics graphics, Font font, String text,
                                        int x, int y, int width, int color) {
        int textWidth = font.width(text);
        graphics.drawString(font, text, x + (width - textWidth) / 2, y, color, false);
    }

    /**
     * Draws text centered within a rectangular area (both axes).
     * Text height is assumed to be 8 pixels (Minecraft font).
     */
    public static void drawCenteredInRect(GuiGraphics graphics, Font font, String text,
                                          int x, int y, int w, int h, int color) {
        int textWidth = font.width(text);
        graphics.drawString(font, text,
                x + (w - textWidth) / 2,
                y + (h - 8) / 2,
                color, false);
    }

    // ── Rectangle drawing ──────────────────────────────────────────────

    /**
     * Draws a 1-pixel border rectangle (outline only, no fill).
     */
    public static void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);             // top
        graphics.fill(x, y + h - 1, x + w, y + h, color);     // bottom
        graphics.fill(x, y, x + 1, y + h, color);             // left
        graphics.fill(x + w - 1, y, x + w, y + h, color);     // right
    }

    /**
     * Draws a filled rectangle with a 1-pixel border.
     */
    public static void drawBorderedRect(GuiGraphics graphics, int x, int y, int w, int h,
                                        int fillColor, int borderColor) {
        graphics.fill(x, y, x + w, y + h, fillColor);
        drawBorder(graphics, x, y, w, h, borderColor);
    }

    /**
     * Draws a horizontal divider line.
     */
    public static void drawHLine(GuiGraphics graphics, int x1, int x2, int y, int color) {
        graphics.fill(x1, y, x2, y + 1, color);
    }

    // ── Panel helpers ──────────────────────────────────────────────────

    /**
     * Calculates a centered panel X position.
     */
    public static int centerX(int screenWidth, int panelWidth) {
        return (screenWidth - panelWidth) / 2;
    }

    /**
     * Calculates a centered panel Y position.
     */
    public static int centerY(int screenHeight, int panelHeight) {
        return (screenHeight - panelHeight) / 2;
    }

    // ── Progress bars ───────────────────────────────────────────────────

    /**
     * Draws a horizontal progress bar.
     * @param progress 0.0 to 1.0
     */
    public static void drawProgressBar(GuiGraphics graphics, int x, int y, int w, int h,
                                        float progress, int fillColor, int bgColor) {
        graphics.fill(x, y, x + w, y + h, bgColor);
        int fillWidth = (int) (w * Math.clamp(progress, 0.0f, 1.0f));
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + h, fillColor);
        }
    }

    /**
     * Draws a bordered progress bar with label.
     */
    public static void drawProgressBar(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                        float progress, int fillColor, int bgColor, int borderColor,
                                        String label, int labelColor) {
        drawProgressBar(graphics, x, y, w, h, progress, fillColor, bgColor);
        drawBorder(graphics, x, y, w, h, borderColor);
        if (label != null && !label.isEmpty()) {
            drawCenteredInRect(graphics, font, label, x, y, w, h, labelColor);
        }
    }

    // ── Vertical line ───────────────────────────────────────────────────

    /**
     * Draws a vertical divider line.
     */
    public static void drawVLine(GuiGraphics graphics, int x, int y1, int y2, int color) {
        graphics.fill(x, y1, x + 1, y2, color);
    }
}
