package at.koopro.wizardsandbeasts.client.gui.character.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

/** Renders a named progress bar (label + filled rect). */
public final class VitalsBarWidget {

    private static final int BAR_H          = 5;
    private static final int COLOR_LABEL    = 0xFFCCBB99;
    private static final int COLOR_TRACK    = 0xFF1A1005;
    private static final int COLOR_BAR_RED  = 0xFFCC3333;
    private static final int COLOR_BAR_BLUE = 0xFF4488CC;
    private static final int COLOR_BAR_XP   = 0xFF55AA33;

    private VitalsBarWidget() {}

    /**
     * Draws a single bar row.
     *
     * @param g      GuiGraphics context
     * @param x      left edge
     * @param y      top edge
     * @param w      available width
     * @param label  bar label string
     * @param value  current value
     * @param max    maximum value
     * @param color  fill color (ARGB)
     */
    public static void draw(@NonNull GuiGraphics g, int x, int y, int w,
                            @NonNull String label, float value, float max, int color) {
        // label + fraction text on same line
        String text = label + "  " + format(value) + " / " + format(max);
        g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                text, x, y, COLOR_LABEL, false);

        int barY = y + 9;
        g.fill(x, barY, x + w, barY + BAR_H, COLOR_TRACK);
        float pct = max > 0 ? Mth.clamp(value / max, 0.0f, 1.0f) : 0.0f;
        int filled = (int)(w * pct);
        if (filled > 0) {
            g.fill(x, barY, x + filled, barY + BAR_H, color);
        }
    }

    /** Draws health bar. */
    public static void drawHealth(@NonNull GuiGraphics g, int x, int y, int w, float hp, float maxHp) {
        draw(g, x, y, w, "Health", hp, maxHp, COLOR_BAR_RED);
    }

    /** Draws XP bar using level + progress as separate display. */
    public static void drawXp(@NonNull GuiGraphics g, int x, int y, int w, int level, float progress) {
        String label = "XP  Lv." + level;
        String text  = label + "  " + Math.round(progress * 100) + "%";
        g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                text, x, y, COLOR_LABEL, false);
        int barY = y + 9;
        g.fill(x, barY, x + w, barY + BAR_H, COLOR_TRACK);
        int filled = (int)(w * Mth.clamp(progress, 0f, 1f));
        if (filled > 0) {
            g.fill(x, barY, x + filled, barY + BAR_H, COLOR_BAR_XP);
        }
    }

    private static String format(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format("%.1f", v);
    }
}
