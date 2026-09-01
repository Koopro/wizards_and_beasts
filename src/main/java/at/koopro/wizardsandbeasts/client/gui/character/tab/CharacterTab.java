package at.koopro.wizardsandbeasts.client.gui.character.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Renders the content area of one tab in the Character Sheet right column. */
public interface CharacterTab {

    /** Translation key for the tab label button. */
    String translationKey();

    /**
     * Called each frame to draw this tab's content.
     *
     * @param g         GuiGraphics context
     * @param x         left edge of the content area
     * @param y         top edge of the content area
     * @param w         width of the content area
     * @param h         height of the content area
     * @param mouseX    mouse x in the sheet's <em>design</em> space, so a hover test compares against
     *                  the same coordinates the tab laid its content out in
     * @param mouseY    mouse y in design space
     * @param partialTick interpolated tick
     */
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick);

    /**
     * The tooltip this tab wants drawn for the frame just rendered, or null for none.
     *
     * <p>Handed back rather than drawn by the tab, because the two need different coordinate spaces.
     * The sheet renders its columns under a scale transform so the whole design fits a small window;
     * a tooltip is drawn by the framework after that transform is popped, in screen space. A tab
     * calling {@code setTooltipForNextFrame} itself would hit-test in design space and then place the
     * card at design-space coordinates on a screen-space canvas — correct only at scale 1.0.
     *
     * <p>Consumed once: the screen clears it after drawing, so a frame in which nothing is hovered
     * cannot inherit the previous frame's card.
     */
    default @Nullable List<Component> consumeTooltip() {
        return null;
    }

    /**
     * Called when the mouse is scrolled while hovering the content area.
     *
     * @param mouseX  mouse x (screen coords)
     * @param mouseY  mouse y (screen coords)
     * @param delta   scroll delta
     * @return true if consumed
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }
}
