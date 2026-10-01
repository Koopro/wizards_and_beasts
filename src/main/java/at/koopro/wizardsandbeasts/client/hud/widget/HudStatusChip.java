package at.koopro.wizardsandbeasts.client.hud.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A small status indicator: a coloured glyph and a word ("✚ Warded", "☠ Traced"). The companion of
 * {@link HudDurationBar} for states that have no clock. Stateless; the caller decides what is shown.
 */
public final class HudStatusChip {

    public static final int HEIGHT = 11;
    private static final int BACK = 0xAA101014;
    private static final int TEXT = 0xFFE8E0D0;

    private HudStatusChip() {}

    /** @return the chip's width, so a caller can lay several out in a row */
    public static int render(GuiGraphics g, Font font, int x, int y, String glyph, Component label, int color) {
        String text = label.getString();
        int width = font.width(glyph) + font.width(text) + 9;
        g.fill(x, y, x + width, y + HEIGHT, BACK);
        g.fill(x, y, x + 1, y + HEIGHT, color | 0xFF000000);
        g.drawString(font, glyph, x + 3, y + 2, color | 0xFF000000, true);
        g.drawString(font, text, x + 6 + font.width(glyph), y + 2, TEXT, true);
        return width;
    }
}
