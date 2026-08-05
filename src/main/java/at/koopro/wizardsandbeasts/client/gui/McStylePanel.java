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

    // ── Themed components ──────────────────────────────────────────────────
    //
    // These draw the shared `gui/theme/` sprites, which shipped with zero consumers. That is the
    // reason 23 of the mod's 34 screens hand-draw their chrome with `g.fill()`: there was a
    // nine-slicer and a set of sprites, but nothing that put the two together, so every screen
    // rolled its own bevel. A screen should call these rather than reinvent a panel.

    private static Identifier theme(String name) {
        return Identifier.fromNamespaceAndPath(
                at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "textures/gui/theme/" + name);
    }

    public static final Identifier THEME_PANEL = theme("panel.png");
    public static final Identifier THEME_PANEL_INSET = theme("panel_inset.png");
    public static final Identifier THEME_DIVIDER = theme("divider.png");
    public static final Identifier THEME_SCROLL_TRACK = theme("scrollbar_track.png");
    public static final Identifier THEME_SCROLL_THUMB = theme("scrollbar_thumb.png");

    /** The default raised panel: leather field, brass rule, lit from the top-left. */
    public static void drawThemedPanel(GuiGraphics g, int x, int y, int w, int h) {
        drawNineSlice(g, THEME_PANEL, x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    /**
     * The recessed variant, for lists, viewports and wells.
     *
     * <p>Its bevel is inverted rather than merely darker. Light comes from the top-left throughout
     * this mod, so on a raised panel that edge is lit and here it is in shadow; swap them and an
     * inset reads as a second panel stacked on the first.
     */
    public static void drawThemedInset(GuiGraphics g, int x, int y, int w, int h) {
        drawNineSlice(g, THEME_PANEL_INSET, x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    /**
     * A horizontal rule, stretched to {@code w}.
     *
     * <p>The sprite is horizontal lines only, so stretching in x resamples each row against itself
     * and is exact at any width. It carries alpha above and below: a divider sits on whatever panel
     * drew it and must not repaint that panel's field.
     */
    public static void drawDivider(GuiGraphics g, int x, int y, int w) {
        drawTexture(g, THEME_DIVIDER, x, y, w, WizardsMetrics.DIVIDER_H,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.DIVIDER_H);
    }

    /**
     * Track plus thumb, stretched to the given heights.
     *
     * <p>Both sprites are columnar — uniform down their length — so a vertical stretch is exact.
     * That is also why the thumb has no end caps: caps are right at one fixed height and a smear at
     * every other, and rounding them needs a three-slice this helper does not offer.
     */
    public static void drawScrollbar(GuiGraphics g, int x, int y, int trackH, int thumbY, int thumbH) {
        int w = WizardsMetrics.SCROLLBAR_W;
        drawTexture(g, THEME_SCROLL_TRACK, x, y, w, trackH, w, WizardsMetrics.PANEL_SPRITE_SIZE);
        if (thumbH > 0) {
            drawTexture(g, THEME_SCROLL_THUMB, x, thumbY, w, thumbH, w, WizardsMetrics.PANEL_SPRITE_SIZE);
        }
    }

    /**
     * A list row, with the selection tint composed over it when selected.
     *
     * <p>Alpha is composed separately from the hue: taking a palette constant wholesale would drag
     * its opaque {@code 0xFF} along and paint over the row beneath.
     */
    public static void drawRow(GuiGraphics g, int x, int y, int w, int h, boolean selected) {
        if (selected) {
            g.fill(x, y, x + w, y + h, 0x33000000 | (WizardsPalette.SELECT & 0x00FFFFFF));
        }
    }
}
