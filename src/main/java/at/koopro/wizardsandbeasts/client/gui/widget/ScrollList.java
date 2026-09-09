package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * A fixed-height list of uniform rows that scrolls by whole rows, with the scrollbar that goes
 * with it.
 *
 * <p>Not a {@code Screen} widget. It owns geometry and an offset and nothing else: the caller keeps
 * its own items, draws its own row contents, and asks this what is visible and where. That is what
 * makes it usable from both a {@code Screen} and an {@code AbstractContainerScreen}, and from a
 * panel that is one of several on a screen.
 *
 * <p><strong>Why this exists.</strong> Seven screens had already written this. They had written it
 * four different ways — row-index scroll in the Bestiary and the Map, pixel scroll in the character
 * sheet's tabs, line scroll in the Handbook — behind three separate scrollbars: the shared 8px
 * sprite, {@code TabScrollbar}'s 4px fill, and a 3px one private to the Handbook. One of the four
 * drew no scrollbar at all, and neither did the Pensieve, whose wheel worked perfectly against a
 * bar that was never on screen. The arithmetic here is the Bestiary's, which is the copy that was
 * already right; the rest is the wrapping that stops it being copied a fifth time.
 *
 * <p>Rows are addressed by index rather than by an item type, so a caller whose list mixes headers
 * and entries — the Bestiary's does — needs no wrapper type to use it.
 */
public final class ScrollList {

    /** Minimum thumb length. Below this a thumb on a long list stops reading as a thumb. */
    private static final int MIN_THUMB_H = WizardsMetrics.SPACE_M;

    private final int rowHeight;

    private int x;
    private int y;
    private int width;
    private int height;

    private int itemCount;
    private int scrollOffset;

    /**
     * @param rowHeight height of one row in pixels, including whatever gap the caller wants between
     *     rows — this class draws nothing between them
     */
    public ScrollList(int rowHeight) {
        this.rowHeight = Math.max(1, rowHeight);
    }

    /**
     * Places the list. The width is the whole strip <em>including</em> the scrollbar, so a caller
     * sizes against the space it has rather than against that space less a bar it has to remember.
     */
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    /** Tells the list how many items the caller now has, and re-clamps the offset. */
    public void setItemCount(int itemCount) {
        this.itemCount = Math.max(0, itemCount);
        clampScroll();
    }

    public int itemCount() {
        return itemCount;
    }

    public int rowHeight() {
        return rowHeight;
    }

    /** How many rows fit. At least one, so a list too short for a row still draws something. */
    public int visibleRows() {
        return Math.max(1, height / rowHeight);
    }

    /** The largest first-visible index. Zero when everything fits. */
    public int maxScroll() {
        return Math.max(0, itemCount - visibleRows());
    }

    public int scrollOffset() {
        return scrollOffset;
    }

    /** Index of the first visible item. Alias of {@link #scrollOffset()}, read at the call site. */
    public int firstVisible() {
        return scrollOffset;
    }

    /** One past the last visible item, clamped to the item count. */
    public int lastVisibleExclusive() {
        return Math.min(itemCount, scrollOffset + visibleRows());
    }

    public boolean scrollable() {
        return maxScroll() > 0;
    }

    /** Screen y of the row drawing item {@code index}, whether or not it is currently visible. */
    public int rowTop(int index) {
        return y + (index - scrollOffset) * rowHeight;
    }

    /** Width available to a row: the strip less the scrollbar, but only when there is one. */
    public int rowWidth() {
        return scrollable() ? width - WizardsMetrics.SCROLLBAR_W : width;
    }

    public int rowLeft() {
        return x;
    }

    /**
     * Handles a mouse wheel turn, in the sign convention {@code Screen#mouseScrolled} delivers.
     *
     * @return whether the offset actually moved, so a caller can decline to consume the event at
     *     the ends of a list and let a parent scroll instead
     */
    public boolean mouseScrolled(double scrollY) {
        int before = scrollOffset;
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll());
        return scrollOffset != before;
    }

    public void setScrollOffset(int offset) {
        scrollOffset = Mth.clamp(offset, 0, maxScroll());
    }

    /** Scrolls the minimum distance that brings {@code index} into view. */
    public void scrollTo(int index) {
        if (index < scrollOffset) {
            setScrollOffset(index);
        } else if (index >= scrollOffset + visibleRows()) {
            setScrollOffset(index - visibleRows() + 1);
        }
    }

    /**
     * The item under the cursor, or {@code null} when the cursor is outside the list or past its
     * last item.
     *
     * <p>Boxed rather than a {@code -1} sentinel: the two are indistinguishable at a call site that
     * forgets to check, and a row index that is silently {@code -1} is how a list ends up
     * highlighting nothing while swearing it selected something.
     *
     * <p>Deliberately excludes the scrollbar strip. A drag on the bar is not a click on the row
     * behind it.
     */
    public @Nullable Integer indexAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + rowWidth() || mouseY < y || mouseY >= y + height) {
            return null;
        }
        int index = scrollOffset + (int) ((mouseY - y) / rowHeight);
        return index >= 0 && index < itemCount ? index : null;
    }

    /**
     * Draws the scrollbar for this list in a skin's material, on the right edge of the strip.
     *
     * <p>Drawn even when the list does not scroll — as an empty track, exactly as the Bestiary
     * does. A track that vanishes takes 8px of the row width with it, so every row would change
     * length as items are added.
     */
    public void renderScrollbar(GuiGraphics graphics, WizardsPalette.GuiSkin skin) {
        int trackLeft = x + width - WizardsMetrics.SCROLLBAR_W;
        if (!scrollable()) {
            McStylePanel.drawSkinScrollbar(graphics, skin, trackLeft, y, height, y, 0);
            return;
        }
        McStylePanel.drawSkinScrollbar(graphics, skin, trackLeft, y, height, thumbTop(), thumbHeight());
    }

    /** The same bar in the shared leather-and-brass chrome. */
    public void renderScrollbar(GuiGraphics graphics) {
        int trackLeft = x + width - WizardsMetrics.SCROLLBAR_W;
        if (!scrollable()) {
            McStylePanel.drawScrollbar(graphics, trackLeft, y, height, y, 0);
            return;
        }
        McStylePanel.drawScrollbar(graphics, trackLeft, y, height, thumbTop(), thumbHeight());
    }

    private int thumbHeight() {
        return Math.max(MIN_THUMB_H, height * visibleRows() / Math.max(1, itemCount));
    }

    /**
     * Top of the thumb.
     *
     * <p>{@code travel} is guarded because a thumb clamped up to {@link #MIN_THUMB_H} on a very
     * short track can be as long as the track itself, and dividing by a zero travel is how a
     * scrollbar throws on the one list nobody tested with.
     */
    private int thumbTop() {
        int travel = Math.max(0, height - thumbHeight());
        return travel == 0 ? y : y + scrollOffset * travel / maxScroll();
    }

    private void clampScroll() {
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
    }
}
