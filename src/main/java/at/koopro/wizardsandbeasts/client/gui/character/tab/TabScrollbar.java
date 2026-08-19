package at.koopro.wizardsandbeasts.client.gui.character.tab;

import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.NonNull;

/**
 * The one scrollbar every scrollable Character Sheet tab draws down its right edge.
 *
 * <p>Each tab used to carry its own copy of the width and the two colours, which is how the Skills
 * tab ended up scrolling with no bar at all: nothing tied "this tab scrolls" to "this tab shows it".
 * Tabs now reserve {@link #WIDTH} out of their content width and call {@link #draw} with the height
 * they measured, so the bar appears exactly when the content overflows.
 */
public final class TabScrollbar {

    /** Column reserved on the right edge of a scrollable tab's content area. */
    public static final int WIDTH = 4;

    private static final int COLOR_TRACK = 0xFF1A1005;
    private static final int COLOR_THUMB = 0xFF886622;
    /** Floor on the thumb so a very long list still leaves something grabbable to look at. */
    private static final int MIN_THUMB_H = 8;

    private TabScrollbar() {}

    /**
     * Draws the bar over the right edge of the tab rect, or nothing when the content fits.
     *
     * @param x            left edge of the tab content area
     * @param y            top edge of the tab content area
     * @param w            full width of the tab content area (bar sits in its rightmost column)
     * @param h            height of the tab content area
     * @param scrollOffset pixels scrolled from the top, already clamped by the caller
     * @param totalH       measured height of the tab's content
     */
    public static void draw(@NonNull GuiGraphics g, int x, int y, int w, int h,
                            float scrollOffset, int totalH) {
        if (totalH <= h) return;

        int sbX = x + w - WIDTH;
        g.fill(sbX, y, sbX + WIDTH, y + h, COLOR_TRACK);

        int thumbH = Math.max(MIN_THUMB_H, (int) ((long) h * h / totalH));
        int thumbY = y + (int) ((scrollOffset / Math.max(1f, totalH - h)) * (h - thumbH));
        g.fill(sbX, thumbY, sbX + WIDTH, thumbY + thumbH, COLOR_THUMB);
    }
}
