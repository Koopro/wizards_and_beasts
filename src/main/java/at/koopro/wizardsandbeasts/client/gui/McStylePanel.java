package at.koopro.wizardsandbeasts.client.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.module.ModuleState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * Vanilla-style GUI panels: stretched {@link VanillaGuiTextures#DEMO_BACKGROUND_TEXTURE} or flat beveled fills.
 */
@NullMarked
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
            g.fill(x, y, x + w, y + h, WizardsPalette.withAlpha(WizardsPalette.SELECT, ROW_SELECT_ALPHA));
        }
    }

    // ── Skinned components ─────────────────────────────────────────────────
    //
    // The same five shapes as above in five materials, plus the five widgets the mod never
    // had. The unskinned methods stay exactly as they are: they are the mod's leather-and-
    // brass default and two live call sites depend on them.
    //
    // Surfaces come off the GUI atlas, widgets come off tokens. That split is deliberate.
    // A panel's border carries drawn detail that has to nine-slice, so it has to be a sprite
    // and vanilla only nine-slices atlas sprites. A button is a filled rect with a one-pixel
    // bevel and four states; as art that is 4 states x 5 skins = 20 sprites for what two
    // `fill` calls and a shade factor express exactly, and every one of those 20 would have
    // to be redrawn to change one colour. Tokens also make the states *derived* -- hovered is
    // lit, pressed is the bevel inverted -- rather than four files that can drift apart.

    /** Selection tint strength. Alpha is composed rather than taken from the token, which is opaque. */
    private static final int ROW_SELECT_ALPHA = 0x33;
    /** The state badge is an 8x8 corner mark. */
    private static final int BADGE = 8;
    /** Slots are vanilla's 18x18 including their one-pixel well edge. */
    private static final int SLOT = 18;

    /** Multiply {@code token}'s RGB by {@code f}, keeping its alpha. */
    private static int shade(int token, float f) {
        int a = token & 0xFF000000;
        int r = Math.clamp((int) (((token >> 16) & 0xFF) * f), 0, 255);
        int g = Math.clamp((int) (((token >> 8) & 0xFF) * f), 0, 255);
        int b = Math.clamp((int) ((token & 0xFF) * f), 0, 255);
        return a | (r << 16) | (g << 8) | b;
    }

    /** The default panel in a skin. */
    public static void drawThemedPanel(GuiGraphics g, GuiSkin skin, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, skin.sprite("panel"), x, y, w, h);
    }

    /** The recessed variant, for lists, viewports and wells. Its bevel is inverted, not merely darker. */
    public static void drawThemedInset(GuiGraphics g, GuiSkin skin, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, skin.sprite("panel_inset"), x, y, w, h);
    }

    /** A horizontal rule, stretched to {@code w}. */
    public static void drawDivider(GuiGraphics g, GuiSkin skin, int x, int y, int w) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, skin.sprite("divider"), x, y, w, WizardsMetrics.DIVIDER_H);
    }

    /** Track plus thumb. Pass {@code thumbH <= 0} for a track with nothing to scroll. */
    public static void drawScrollbar(GuiGraphics g, GuiSkin skin, int x, int y,
                                     int trackH, int thumbY, int thumbH) {
        int w = WizardsMetrics.SCROLLBAR_W;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, skin.sprite("scrollbar_track"), x, y, w, trackH);
        if (thumbH > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, skin.sprite("scrollbar_thumb"), x, thumbY, w, thumbH);
        }
    }

    /** A list row, with the skin's selection tint composed over it when selected. */
    public static void drawRow(GuiGraphics g, GuiSkin skin, int x, int y, int w, int h, boolean selected) {
        if (selected) {
            g.fill(x, y, x + w, y + h, WizardsPalette.withAlpha(skin.accent(), ROW_SELECT_ALPHA));
        }
    }

    /**
     * The skin's corner motif, at a panel's top-left.
     *
     * <p>The one decoration the system permits, and it earns the exemption by carrying the
     * material: a strap tab reads as canvas, an ink stamp as the Ministry, a scorch as a bench.
     * Drawn at native 16x16 — a seal that stretches with its panel stops looking struck.
     */
    public static void drawSeal(GuiGraphics g, GuiSkin skin, int panelX, int panelY) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, skin.sprite("seal"),
                panelX + WizardsMetrics.SPACE_XS, panelY + WizardsMetrics.SPACE_XS, 16, 16);
    }

    /**
     * A title bar: the panel's top band, its title, and the rule under it.
     *
     * <p>Returns the y the caller's content starts at, so a screen never spells the sum of a
     * line height and a divider itself. Text is drawn at {@link WizardsMetrics#LINE_TITLE}'s
     * baseline rather than centred in the band: centring a 9px glyph in a 20px bar puts it a
     * half-pixel off at odd GUI scales, which is where the mod's title jitter came from.
     */
    public static int drawHeader(GuiGraphics g, GuiSkin skin, Font font, Component title,
                                 int x, int y, int w) {
        int textY = y + (WizardsMetrics.LINE_TITLE - font.lineHeight) / 2;
        g.drawString(font, title, x + WizardsMetrics.SPACE_M, textY, skin.ink(), false);
        drawDivider(g, skin, x + WizardsMetrics.SPACE_S, y + WizardsMetrics.LINE_TITLE,
                w - 2 * WizardsMetrics.SPACE_S);
        return y + WizardsMetrics.LINE_TITLE + WizardsMetrics.DIVIDER_H;
    }

    /** The four states a {@link #drawButton} can be in. */
    public enum ButtonState {
        NORMAL,
        HOVERED,
        PRESSED,
        DISABLED
    }

    /**
     * A button, derived from the skin's tokens rather than four sprites per skin.
     *
     * <p>Hovered is the face lit, pressed is the same face with its bevel inverted and its
     * content nudged, disabled is the face flattened toward the frame. Deriving them means the
     * four states cannot drift out of step with each other or with the skin.
     */
    public static void drawButton(GuiGraphics g, GuiSkin skin, int x, int y, int w, int h,
                                  ButtonState state) {
        int face = switch (state) {
            case NORMAL -> shade(skin.base(), 0.86f);
            case HOVERED -> shade(skin.base(), 1.0f);
            case PRESSED -> shade(skin.base(), 0.74f);
            case DISABLED -> shade(skin.base(), 0.62f);
        };
        int lit = state == ButtonState.DISABLED ? shade(skin.frame(), 1.1f) : skin.accent();
        int dark = shade(skin.frame(), 0.8f);
        g.fill(x, y, x + w, y + h, face);
        if (state == ButtonState.PRESSED) {
            drawBorder(g, x, y, w, h, dark, lit);
        } else {
            drawBorder(g, x, y, w, h, lit, dark);
        }
    }

    /**
     * A tab. Active tabs sit flush with the panel below them; inactive ones are set back.
     *
     * <p>The bottom edge is deliberately not drawn on an active tab — that is what makes it
     * read as continuous with its panel rather than as a button parked above one.
     */
    public static void drawTab(GuiGraphics g, GuiSkin skin, int x, int y, int w, int h, boolean active) {
        g.fill(x, y, x + w, y + h, active ? skin.base() : shade(skin.base(), 0.72f));
        int lit = active ? skin.accent() : shade(skin.frame(), 1.05f);
        int dark = shade(skin.frame(), 0.8f);
        g.fill(x, y, x + w, y + 1, lit);
        g.fill(x, y, x + 1, y + h, lit);
        g.fill(x + w - 1, y, x + w, y + h, dark);
        if (!active) {
            g.fill(x, y + h - 1, x + w, y + h, dark);
        }
    }

    /**
     * A container slot's well, drawn beneath the vanilla item render.
     *
     * <p>Belongs in {@code renderBg}: {@code AbstractContainerScreen} runs that strictly before
     * {@code renderSlots}, and vanilla draws no slot background of its own — slot wells are
     * painted into the container sheet — so nothing is being overdrawn. Nothing here reads or
     * writes {@code Slot} bounds, so hit detection is untouched by construction.
     *
     * @param slotX {@code Slot#x}, the item's top-left; the well is drawn one pixel out from it
     */
    public static void drawSlot(GuiGraphics g, GuiSkin skin, int slotX, int slotY) {
        int x = slotX - 1;
        int y = slotY - 1;
        g.fill(x, y, x + SLOT, y + SLOT, shade(skin.base(), 0.66f));
        drawBorder(g, x, y, SLOT, SLOT, shade(skin.frame(), 0.8f), shade(skin.base(), 1.05f));
    }

    /**
     * How a content tile should be tinted for a module's access state.
     *
     * <p>Returned rather than applied because a silhouette cannot be produced after the fact:
     * it is the content's own draw multiplied by a flat colour, so the caller has to pass this
     * into its {@code blitSprite}. {@code -1} is white — an unmodified draw.
     *
     * <p>{@code COMING_SOON} and {@code DISABLED} stay distinct on purpose. Coming-soon is
     * opaque skin frame, so the shape survives and the detail does not: <em>not yet</em>.
     * Disabled is a dimmed neutral: <em>switched off</em>. Collapsing them loses the difference
     * between a roadmap marker and an operator's toggle, which is the one thing
     * {@link ModuleState} is careful to keep apart.
     */
    public static int contentTint(GuiSkin skin, ModuleState state) {
        return switch (state) {
            case ENABLED, PREVIEW -> -1;
            case COMING_SOON -> 0xFF000000 | (skin.frame() & 0x00FFFFFF);
            case DISABLED -> WizardsPalette.withAlpha(shade(skin.muted(), 0.9f), 0x88);
        };
    }

    /**
     * The corner badge marking a content tile's access state. A no-op for {@code ENABLED}.
     *
     * <p>One 8x8 sprite for all four states and all five skins, tinted here. Preview wears the
     * skin's accent because it is reachable; the two unreachable states wear its muted tone.
     * Twenty near-identical triangles on the atlas would say nothing this does not.
     */
    public static void drawStateBadge(GuiGraphics g, GuiSkin skin, ModuleState state,
                                      int tileX, int tileY, int tileW) {
        if (state == ModuleState.ENABLED) {
            return;
        }
        int tint = state == ModuleState.PREVIEW ? skin.accent() : skin.muted();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, WizardsPalette.STATE_BADGE,
                tileX + tileW - BADGE, tileY, BADGE, BADGE, tint);
    }
}
