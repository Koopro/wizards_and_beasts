package at.koopro.wizardsandbeasts.client.gui.character.tab;

import net.minecraft.client.gui.GuiGraphics;

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
     * @param partialTick interpolated tick
     */
    void render(GuiGraphics g, int x, int y, int w, int h, float partialTick);

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
