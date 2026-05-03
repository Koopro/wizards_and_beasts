package at.koopro.wizardsandbeasts.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Vanilla-style GUI panels: stretched {@link VanillaGuiTextures#DEMO_BACKGROUND_TEXTURE} or flat beveled fills.
 */
public final class McStylePanel {

    private McStylePanel() {}

    /** {@link VanillaGuiTextures#DEMO_BACKGROUND_TEXTURE} is 256×256; entire image is stretched to the panel rect. */
    private static final int DEMO_BG_SIZE = 256;

    /**
     * Draws {@code textures/gui/demo_background.png} stretched to the given size (any width/height).
     * <p>Uses the {@code blit} overload that sets <em>destination</em> size and <em>source</em> region separately.
     * The shorter overload treats on-screen width/height as the texel region too, so panels wider/taller than
     * 256px sample past the sheet and UV wrap makes the texture look <em>tiled</em>.
     */
    public static void drawTexturedPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, VanillaGuiTextures.DEMO_BACKGROUND_TEXTURE,
                x, y,
                0.0F, 0.0F,
                w, h,
                DEMO_BG_SIZE, DEMO_BG_SIZE,
                DEMO_BG_SIZE, DEMO_BG_SIZE);
    }

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
