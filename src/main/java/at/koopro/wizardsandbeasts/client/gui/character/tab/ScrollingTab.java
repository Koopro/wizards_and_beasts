package at.koopro.wizardsandbeasts.client.gui.character.tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The scrolling, clipping, hover and click plumbing every long sheet page needs, so a page only lays out its
 * column: {@link #drawContent} draws top to bottom and returns the y it ended at.
 *
 * <p>The older pages each carry their own copy of this (scroll clamp, scissor, scrollbar, a hit list cleared per
 * frame, a visible-rect check so a row scrolled under the scissor neither pops a card nor takes a click). The pages
 * written for the progression hub share this one instead.
 */
@NullMarked
public abstract class ScrollingTab implements CharacterTab {

    /** A clickable rectangle drawn this frame, in design space. */
    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private @Nullable List<Component> pendingTooltip;
    private float scrollOffset;
    private int lastTotalH;
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;

    /**
     * Draws the page's column at {@code (x, y)}, {@code w} wide, and returns the y below the last thing drawn.
     * {@code y} already carries the scroll offset.
     */
    protected abstract int drawContent(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY);

    @Override
    public final void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        hits.clear();
        pendingTooltip = null;
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;
        scrollOffset = Mth.clamp(scrollOffset, 0f, Math.max(0, lastTotalH - h));

        g.enableScissor(x, y, x + w, y + h);
        int top = y + 2 - (int) scrollOffset;
        // The scrollbar is drawn over the right edge, so content is laid out inside a width that excludes it.
        int end = drawContent(g, font, x + 2, top, w - 4 - TabScrollbar.WIDTH, mouseX, mouseY);
        g.disableScissor();

        lastTotalH = end - top + 4;
        TabScrollbar.draw(g, x, y, w, h, scrollOffset, lastTotalH);
    }

    /** Back to the top, for {@link #onShown}. */
    protected final void resetScroll() {
        scrollOffset = 0f;
    }

    /** Whether the pointer is over this rectangle and the rectangle is not scrolled out of view there. */
    protected final boolean hovered(int rx, int ry, int rw, int rh, int mouseX, int mouseY) {
        return mouseX >= Math.max(rx, viewX) && mouseX < Math.min(rx + rw, viewX + viewW)
                && mouseY >= Math.max(ry, viewY) && mouseY < Math.min(ry + rh, viewY + viewH);
    }

    /** The card to show for this frame; the last call in a frame wins. */
    protected final void tooltip(List<Component> lines) {
        pendingTooltip = lines;
    }

    /** Registers a click target, unless it is entirely scrolled out of the visible area. */
    protected final void clickable(int x, int y, int w, int h, Runnable action) {
        if (y + h > viewY && y < viewY + viewH) {
            hits.add(new Hit(x, y, w, h, action));
        }
    }

    @Override
    public final @Nullable List<Component> consumeTooltip() {
        List<Component> out = pendingTooltip;
        pendingTooltip = null;
        return out;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY) && mouseY >= viewY && mouseY < viewY + viewH) {
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f));
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float) (delta * 10.0);
        return true;
    }
}
