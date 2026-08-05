package at.koopro.wizardsandbeasts.client.gui;

/**
 * The one set of sizes every screen in this mod measures with.
 *
 * <p>Sibling to {@link WizardsPalette} rather than part of it: that class is scoped to colour and
 * its single responsibility is its main virtue. This is the other half — space, panel size and
 * line height.
 *
 * <p>It exists because it did not. Measured across the 34 client screens before this class landed:
 * <strong>25 distinct panel sizes</strong> (14 widths, 19 heights, with no relationship between
 * them), and of 19 padding constants <strong>9 sat off any grid</strong> — 3, 6, 7, 9, 10, 13, 19,
 * 22, 26, each appearing once or twice with no shared step. Text was worse: 86 {@code drawString}
 * calls, only six screens consulting {@code font.lineHeight}, and ad-hoc y-offsets stepping 11/11 on
 * one screen and 12/12/14 on another.
 *
 * <p>That is why fixes felt like whack-a-mole. A label colliding with its value, content drawn on
 * top of the frame, a fixed-width card in a wider column, a near-square box around a standing
 * figure — each was individually real, and all four shared one cause: no shared vocabulary for size
 * or space. Import this rather than inventing another number.
 *
 * <p>Colour is deliberately absent. See {@link WizardsPalette}.
 */
public final class WizardsMetrics {

    // ── Spacing: a 4pt scale ───────────────────────────────────────────────
    //
    // Every padding, gap, inset and margin resolves to one of these. The nine off-grid values the
    // mod carried map to their nearest step; nothing needs a bespoke number. Where an existing
    // value was load-bearing the nearest step still clears it -- the character sheet's FRAME_PAD
    // was 7, derived from `gui_chrome.dossier_backdrop` painting its frame out to 6px, and M
    // clears that with a pixel to spare.

    /** Hairline separation. Between a bar and its label, a pip and its neighbour. */
    public static final int SPACE_XS = 2;
    /** Inside a small component: a card's text from its own edge. */
    public static final int SPACE_S = 4;
    /** The default. Between components, and clear of a panel's frame. */
    public static final int SPACE_M = 8;
    /** Between a section and the next. */
    public static final int SPACE_L = 12;
    /** Between major blocks in a column. */
    public static final int SPACE_XL = 16;
    /** Column gutters. */
    public static final int SPACE_XXL = 24;
    /** Full-width breathing room; rarely needed inside a 320px panel. */
    public static final int SPACE_XXXL = 32;

    // ── Panel sizes ────────────────────────────────────────────────────────
    //
    // Four, from twenty-five. A screen picks the smallest that fits its content rather than
    // inventing a size to match it -- inventing the size is how 14 distinct widths happened.

    /** Confirm/deny and single-message modals. */
    public static final int PANEL_MODAL_W = 256;
    public static final int PANEL_MODAL_H = 144;
    /** The default. Vanilla's own panel proportion, which most of this mod's screens already sat near. */
    public static final int PANEL_STANDARD_W = 320;
    public static final int PANEL_STANDARD_H = 240;
    /** Two-column layouts and canvases (skill chart, debug tooling). */
    public static final int PANEL_WIDE_W = 400;
    public static final int PANEL_WIDE_H = 256;

    /**
     * Container screens keep vanilla's 176px width and are <em>not</em> covered by the sizes above.
     *
     * <p>Their slot offsets are vanilla's, so an invented frame misaligns all of them — the same
     * reason {@code gui_chrome.restyle} recolours vanilla container art in place rather than
     * redrawing it. Listed here so the omission reads as deliberate.
     */
    public static final int PANEL_CONTAINER_W = 176;

    // ── Type ───────────────────────────────────────────────────────────────
    //
    // Anchored on Minecraft's own 9px line height plus leading, rather than the 11s, 12s and 14s
    // screens currently step by. A screen picks one and stays on it.

    /** Dense lists and stat rows. 9px glyphs + 1. */
    public static final int LINE_TIGHT = 10;
    /** Body copy and most labelled values. */
    public static final int LINE_BODY = 12;
    /** Between a section heading and its content. */
    public static final int LINE_SECTION = 16;
    /** Title bars. */
    public static final int LINE_TITLE = 20;

    // ── Component metrics ──────────────────────────────────────────────────

    /** Nine-slice border of the {@code gui/theme/} panel sprites. They are 32x32 cut on 8. */
    public static final int PANEL_SPRITE_SIZE = 32;
    public static final int PANEL_SPRITE_BORDER = 8;
    /** Divider sprite is 32x8; scrollbar sprites are 8x32. */
    public static final int DIVIDER_H = 8;
    public static final int SCROLLBAR_W = 8;
    /** A selectable list row. */
    public static final int ROW_H = 16;

    private WizardsMetrics() {
    }
}
