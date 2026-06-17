package at.koopro.wizardsandbeasts.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Vanilla-style GUI panels: stretched {@link VanillaGuiTextures#DEMO_BACKGROUND_TEXTURE} or flat beveled fills.
 */
public final class McStylePanel {

    private McStylePanel() {}

    /**
     * Tile a small square texture ({@code tile}×{@code tile}) across a {@code w}×{@code h} rect.
     * Source region is set larger than the sheet so the {@code GUI_TEXTURED} sampler UV-wraps (repeats).
     */
    public static void drawTiled(GuiGraphics g, Identifier tex, int x, int y, int w, int h, int tile) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F, w, h, w, h, tile, tile);
    }

    /** Blit a fixed-size texture stretched to a destination rect (1:1 region = whole sheet). */
    public static void drawTexture(GuiGraphics g, Identifier tex, int x, int y, int w, int h,
                                   int srcW, int srcH) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F, w, h, srcW, srcH, srcW, srcH);
    }

    /**
     * Nine-slice a square texture of edge length {@code ts} with corner inset {@code b} into a
     * {@code w}×{@code h} rect: corners stay fixed, edges/center stretch.
     */
    public static void drawNineSlice(GuiGraphics g, Identifier tex, int x, int y, int w, int h,
                                     int ts, int b) {
        int inner = ts - 2 * b;
        int iw = w - 2 * b;
        int ih = h - 2 * b;
        float far = ts - b;
        // corners
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x,       y,       0.0F, 0.0F, b, b, b, b, ts, ts);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - b, y,     far,  0.0F, b, b, b, b, ts, ts);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x,       y + h - b, 0.0F, far, b, b, b, b, ts, ts);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - b, y + h - b, far, far, b, b, b, b, ts, ts);
        // edges
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + b,   y,       (float) b, 0.0F, iw, b, inner, b, ts, ts);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + b,   y + h - b, (float) b, far, iw, b, inner, b, ts, ts);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x,       y + b,   0.0F, (float) b, b, ih, b, inner, ts, ts);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - b, y + b, far, (float) b, b, ih, b, inner, ts, ts);
        // center
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + b,   y + b,   (float) b, (float) b, iw, ih, inner, inner, ts, ts);
    }

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
