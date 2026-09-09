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
        drawNineSlice(g, tex, x, y, w, h, 0, 0, ts, b, ts, ts);
    }

    /**
     * Nine-slice one {@code ts}×{@code ts} cell of a larger sheet, the cell's top-left corner at
     * {@code (u, v)}.
     *
     * <p>The whole-file form above is this with the cell at the origin and the sheet exactly one
     * cell across, which is why it delegates rather than duplicating the nine blits.
     */
    public static void drawNineSlice(GuiGraphics g, Identifier tex, int x, int y, int w, int h,
                                     int u, int v, int ts, int b, int sheetW, int sheetH) {
        int inner = ts - 2 * b;
        int iw = w - 2 * b;
        int ih = h - 2 * b;
        float nearX = u;
        float nearY = v;
        float midX = u + b;
        float midY = v + b;
        float farX = u + ts - b;
        float farY = v + ts - b;
        // corners
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x,       y,       nearX, nearY, b, b, b, b, sheetW, sheetH);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - b, y,     farX,  nearY, b, b, b, b, sheetW, sheetH);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x,       y + h - b, nearX, farY, b, b, b, b, sheetW, sheetH);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - b, y + h - b, farX, farY, b, b, b, b, sheetW, sheetH);
        // edges
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + b,   y,       midX, nearY, iw, b, inner, b, sheetW, sheetH);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + b,   y + h - b, midX, farY, iw, b, inner, b, sheetW, sheetH);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x,       y + b,   nearX, midY, b, ih, b, inner, sheetW, sheetH);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - b, y + b, farX, midY, b, ih, b, inner, sheetW, sheetH);
        // center
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + b,   y + b,   midX, midY, iw, ih, inner, inner, sheetW, sheetH);
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

    // ── The controls atlas ─────────────────────────────────────────────────
    //
    // Every themed control is a fixed-size cell of one sheet rather than a file of its own. As
    // separate PNGs the set was twenty-two Identifiers that had to stay in step with twenty-two
    // filenames, all of them the same three sizes, and the button tones would have added twelve
    // more — a file per cell bought nothing that a `(u, v)` does not.
    //
    // Layout is mirrored in `tools/gui_chrome.py` (`ATLAS_*`), which composes the sheet. The two
    // must agree; both spell the numbers out rather than deriving them from the other.
    //
    //   cols = ButtonTone, rows = ControlState, 32px cells:
    //     (0,0) neutral   (32,0) confirm   (64,0) danger   (96,0) select   (128,0) accent
    //     row 1 at y=32 is those five hovered, row 2 at y=64 is those five disabled
    //   icon band at y=96:
    //     (0,96) randomise 12x12 | arrows right at x=16,28,40 | arrows left at x=52,64,76

    public static final Identifier THEME_CONTROLS = theme("controls.png");
    private static final int ATLAS_W = 160;
    private static final int ATLAS_H = 112;
    private static final int ATLAS_ICON_Y = 96;
    private static final int ATLAS_ARROW_PITCH = 12;
    private static final int ATLAS_ARROW_RIGHT_X = 16;
    private static final int ATLAS_ARROW_LEFT_X = 52;

    /** Cycler arrow art. 9x13, blitted at native size — see {@link #drawArrow}. */
    public static final int ARROW_W = 9;
    public static final int ARROW_H = 13;

    /** The randomise glyph: crossed shuffle arrows, 12x12, at the head of the icon band. */
    public static final Sprite THEME_ICON_RANDOMISE =
            new Sprite(THEME_CONTROLS, 0, ATLAS_ICON_Y, 12, 12, ATLAS_W, ATLAS_H);

    /**
     * A rectangle of a sheet, so a caller can name a piece of art without knowing whether it is a
     * whole file or one cell of an atlas.
     *
     * <p>{@link #whole} exists because the skinned controls are still a file each: five materials
     * times the full set is five atlases, which is a different job from collapsing the shared
     * {@code theme/} set into one.
     */
    public record Sprite(Identifier tex, int u, int v, int w, int h, int sheetW, int sheetH) {

        /** A sprite that is its entire file. */
        public static Sprite whole(Identifier tex, int w, int h) {
            return new Sprite(tex, 0, 0, w, h, w, h);
        }
    }

    /** Blits a sprite at its native size. */
    public static void drawSprite(GuiGraphics g, Sprite sprite, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, sprite.tex(), x, y, sprite.u(), sprite.v(),
                sprite.w(), sprite.h(), sprite.w(), sprite.h(), sprite.sheetW(), sprite.sheetH());
    }

    /**
     * Interaction state for the themed controls.
     *
     * <p>A row of the atlas rather than a tint applied at draw time: the {@code blit} calls in this
     * class carry no colour argument, and widening a shared drawing API for a cosmetic reason is
     * the wrong trade.
     */
    public enum ControlState {
        NORMAL("", 0), HOVER("_hover", 1), DISABLED("_off", 2);

        /** Filename suffix. Still used by the skinned sets, which remain a file per state. */
        private final String suffix;
        /** Atlas row. Explicit rather than {@code ordinal()}, so reordering the enum is safe. */
        private final int row;

        ControlState(String suffix, int row) {
            this.suffix = suffix;
            this.row = row;
        }

        /** Picks the state a widget is in, in the order a player perceives it. */
        public static ControlState of(boolean active, boolean highlighted) {
            return !active ? DISABLED : highlighted ? HOVER : NORMAL;
        }
    }

    /** Draws a cycler arrow at its native 9x13. */
    public static void drawArrow(GuiGraphics g, int x, int y, boolean pointsRight, ControlState state) {
        int u = (pointsRight ? ATLAS_ARROW_RIGHT_X : ATLAS_ARROW_LEFT_X)
                + state.row * ATLAS_ARROW_PITCH;
        drawSprite(g, new Sprite(THEME_CONTROLS, u, ATLAS_ICON_Y, ARROW_W, ARROW_H, ATLAS_W, ATLAS_H),
                x, y);
    }

    /**
     * What pressing a button does, as opposed to {@link ControlState}, which is whether you can.
     *
     * <p>The heritage gate had four buttons that read identically — Confirm, Randomise, and the
     * Cancel/Yes pair inside the overlay — so on the first screen of a new character the one
     * irreversible action looked exactly like the one that backs out of it.
     *
     * <p>Tones move only the button's field colour; the leather frame is shared, so a tone reads as
     * this chrome carrying a meaning rather than as a button borrowed from another widget set.
     * {@code tools/gui_chrome.py} derives all four from one colour each — see {@code TONE_FIELDS}.
     */
    public enum ButtonTone {
        /** The leather default. Anything that neither commits nor destroys. */
        NEUTRAL(0),
        /** Commits something the player cannot take back. */
        CONFIRM(1),
        /** Destroys, or refuses. Not used for merely-disabled, which is {@link ControlState}. */
        DANGER(2),
        /** The option currently chosen, where a button doubles as a state readout. */
        SELECT(3),
        /** A live but non-committing action — reroll, reveal, reset. */
        ACCENT(4);

        /** Atlas column. Explicit rather than {@code ordinal()}, so reordering the enum is safe. */
        private final int col;

        ButtonTone(int col) {
            this.col = col;
        }
    }

    /** Draws a themed button face, nine-sliced on the same 32/8 frame as the panels. */
    public static void drawThemedButton(GuiGraphics g, int x, int y, int w, int h, ControlState state) {
        drawThemedButton(g, x, y, w, h, ButtonTone.NEUTRAL, state);
    }

    /** The same face in one of the {@link ButtonTone}s — one cell of the controls atlas. */
    public static void drawThemedButton(GuiGraphics g, int x, int y, int w, int h,
                                        ButtonTone tone, ControlState state) {
        int ts = WizardsMetrics.PANEL_SPRITE_SIZE;
        drawNineSlice(g, THEME_CONTROLS, x, y, w, h,
                tone.col * ts, state.row * ts,
                ts, WizardsMetrics.PANEL_SPRITE_BORDER, ATLAS_W, ATLAS_H);
    }

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

    // ── Skinned components ─────────────────────────────────────────────────
    //
    // `tools/gui_chrome.py` generates the same shapes as `theme/` in five materials under
    // `gui/sprites/<skin>/`, and until now nothing in Java could ask for one -- five skins with
    // zero consumers, next to a screen hand-drawing its own night-sky chrome with `fill()`.
    //
    // Blitted by raw path rather than `blitSprite`. The mod ships an `atlases/gui.json` and the
    // generator writes nine-slice `.mcmeta` beside each panel, but no code has ever exercised
    // that atlas; the raw path with an explicit border is what `drawThemedPanel` above already
    // does, and it is the one that is known to work here.

    public static Identifier skinSprite(String skin, String element) {
        return Identifier.fromNamespaceAndPath(at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID,
                "textures/gui/sprites/" + skin + "/" + element + ".png");
    }

    public static Identifier skinSprite(WizardsPalette.GuiSkin skin, String element) {
        return skinSprite(skin.folder(), element);
    }

    // The GuiSkin overloads below are the ones new code should call; each delegates to the
    // String form, which the two screens that predate the enum still use.

    /** {@link #drawSkinPanel(GuiGraphics, String, int, int, int, int)} in a named material. */
    public static void drawSkinPanel(GuiGraphics g, WizardsPalette.GuiSkin skin,
                                     int x, int y, int w, int h) {
        drawSkinPanel(g, skin.folder(), x, y, w, h);
    }

    /** {@link #drawSkinInset(GuiGraphics, String, int, int, int, int)} in a named material. */
    public static void drawSkinInset(GuiGraphics g, WizardsPalette.GuiSkin skin,
                                     int x, int y, int w, int h) {
        drawSkinInset(g, skin.folder(), x, y, w, h);
    }

    /** {@link #drawSkinDivider(GuiGraphics, String, int, int, int)} in a named material. */
    public static void drawSkinDivider(GuiGraphics g, WizardsPalette.GuiSkin skin,
                                       int x, int y, int w) {
        drawSkinDivider(g, skin.folder(), x, y, w);
    }

    /** {@link #drawSkinButton} in a named material. */
    public static void drawSkinButton(GuiGraphics g, WizardsPalette.GuiSkin skin,
                                      int x, int y, int w, int h, ControlState state) {
        drawSkinButton(g, skin.folder(), x, y, w, h, state);
    }

    /** {@link #drawSkinSeal} in a named material, tinted with the skin's own accent. */
    public static void drawSkinSeal(GuiGraphics g, WizardsPalette.GuiSkin skin, int x, int y) {
        drawSkinSeal(g, skin.folder(), x, y, skin.accent());
    }

    /**
     * The same seal under a caller's tint.
     *
     * <p>The seal art is greyscale, so the tint is the whole of its colour — which is why a screen
     * that wants the motif to answer something other than the material (the skill web dims it when
     * a region is sealed) needs this rather than the accent-tinted form above.
     */
    public static void drawSkinSeal(GuiGraphics g, WizardsPalette.GuiSkin skin, int x, int y, int tint) {
        drawSkinSeal(g, skin.folder(), x, y, tint);
    }

    /** The raised panel in a skin — {@link #drawThemedPanel} in another material. */
    public static void drawSkinPanel(GuiGraphics g, String skin, int x, int y, int w, int h) {
        drawNineSlice(g, skinSprite(skin, "panel"), x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    /** The recessed well in a skin — inverted bevel, exactly as {@link #drawThemedInset} inverts it. */
    public static void drawSkinInset(GuiGraphics g, String skin, int x, int y, int w, int h) {
        drawNineSlice(g, skinSprite(skin, "panel_inset"), x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    /** A horizontal rule in a skin, stretched to {@code w}. Rows only, so the stretch is exact. */
    public static void drawSkinDivider(GuiGraphics g, String skin, int x, int y, int w) {
        drawTexture(g, skinSprite(skin, "divider"), x, y, w, WizardsMetrics.DIVIDER_H,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.DIVIDER_H);
    }

    /** A button face in a skin, on the same 32/8 frame as its panels. */
    public static void drawSkinButton(GuiGraphics g, String skin, int x, int y, int w, int h,
                                      ControlState state) {
        drawNineSlice(g, skinSprite(skin, "button" + state.suffix), x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    /**
     * Track plus thumb in a skin — {@link #drawScrollbar} in another material.
     *
     * <p>Its absence is why the Floo screen draws a two-pixel bar out of {@code fill()} calls:
     * every skin has shipped a {@code scrollbar_track.png} and a {@code scrollbar_thumb.png} since
     * the generator was written, and nothing in Java could ask for either. Both sprites are
     * columnar, so the vertical stretch is exact, and there are no end caps for the same reason the
     * shared set has none.
     */
    public static void drawSkinScrollbar(GuiGraphics g, WizardsPalette.GuiSkin skin, int x, int y,
                                         int trackH, int thumbY, int thumbH) {
        int w = WizardsMetrics.SCROLLBAR_W;
        drawTexture(g, skinSprite(skin, "scrollbar_track"), x, y, w, trackH,
                w, WizardsMetrics.PANEL_SPRITE_SIZE);
        if (thumbH > 0) {
            drawTexture(g, skinSprite(skin, "scrollbar_thumb"), x, thumbY, w, thumbH,
                    w, WizardsMetrics.PANEL_SPRITE_SIZE);
        }
    }

    /**
     * A list row in a skin — {@link #drawRow} in another material.
     *
     * <p>Tinted with the skin's own {@link WizardsPalette.GuiSkin#accent()} rather than the
     * leather {@link WizardsPalette#SELECT}: a warm brown wash over the star chart's night void is
     * the same mismatch a leather button on an indigo panel is, and half the skins are light
     * enough that the leather tint reads as a stain rather than a selection.
     *
     * <p>Alpha is composed separately from the hue for the reason {@link #drawRow} gives — taking
     * the colour wholesale drags its opaque {@code 0xFF} along and paints over the row beneath.
     */
    public static void drawSkinRow(GuiGraphics g, WizardsPalette.GuiSkin skin,
                                   int x, int y, int w, int h, boolean selected) {
        if (!selected) {
            return;
        }
        g.fill(x, y, x + w, y + h, ROW_SELECT_ALPHA | (skin.accent() & 0x00FFFFFF));
        // Plus an opaque edge. A quarter-alpha wash is a clear selection on the star chart's night
        // void and almost nothing on the four light materials, where the tint and the face differ
        // by a few points of luminance. The outline reads on all of them.
        drawBorder(g, x, y, w, h, skin.accent(), skin.accent());
    }

    /** How much of a row's tint is the colour and how much is the row underneath. */
    private static final int ROW_SELECT_ALPHA = 0x44000000;

    /** The corner motif for a skin, at its native 16x16. */
    public static void drawSkinSeal(GuiGraphics g, String skin, int x, int y, int tint) {
        drawTintedTexture(g, skinSprite(skin, "seal"), x, y, SEAL_SIZE, SEAL_SIZE,
                SEAL_SIZE, SEAL_SIZE, tint);
    }

    /** Native size of every {@code seal.png} the skin generator writes. */
    public static final int SEAL_SIZE = 16;

    // ── Tinted and rotated blits ───────────────────────────────────────────

    /**
     * {@link #drawTexture} with an ARGB tint multiplied into the sprite.
     *
     * <p>The tinted {@code blit} overload is how a greyscale sprite set serves many colours from
     * one PNG — the skill chart's whole star set is authored white and coloured here.
     */
    public static void drawTintedTexture(GuiGraphics g, Identifier tex, int x, int y, int w, int h,
                                         int srcW, int srcH, int tint) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F, w, h, srcW, srcH, srcW, srcH, tint);
    }

    /** Centres a square sprite on {@code (cx, cy)} at {@code size} px, tinted. */
    public static void drawTintedCentered(GuiGraphics g, Identifier tex, int cx, int cy, int size, int tint) {
        drawTintedTexture(g, tex, cx - size / 2, cy - size / 2, size, size, size, size, tint);
    }

    /**
     * Blits a strip sprite along the segment {@code (x1,y1) → (x2,y2)}, rotated to its angle.
     *
     * <p>The alternative is a Bresenham run of one-pixel {@code fill}s, which is what this mod's
     * skill web did: a hard stair-stepped line sitting under soft antialiased star sprites, two
     * renderers on one canvas. A strip that is uniform along its length stretches exactly, so the
     * only thing the texture has to carry is the falloff across its width.
     *
     * <p>{@code GuiGraphics.pose()} is a {@code Matrix3x2fStack} and transforms the quad's
     * vertices, so rotation is free. Scissor rectangles are screen-space and untouched by the
     * pose, so a caller's viewport clip still holds.
     */
    public static void drawTexturedSegment(GuiGraphics g, Identifier tex, double x1, double y1,
                                           double x2, double y2, int thickness,
                                           int srcW, int srcH, int tint) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0) {
            return;
        }
        int span = (int) Math.round(length);
        int weight = Math.max(1, thickness);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) x1, (float) y1);
        pose.rotate((float) Math.atan2(dy, dx));
        // Centre the strip on the segment rather than hanging it below.
        pose.translate(0.0F, -weight / 2.0F);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0.0F, 0.0F, span, weight,
                srcW, srcH, srcW, srcH, tint);
        pose.popMatrix();
    }
}
