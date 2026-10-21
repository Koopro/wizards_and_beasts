package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Every rectangle on the spell loadout screen, computed once per resize from the screen size.
 *
 * <p>The painting, the hit-testing, the widgets' bounds and the drop targets all read these numbers;
 * nothing on the screen computes a position of its own. Geometry is in native GUI pixels and is never
 * scaled: the old screen scaled a 310x250 design by 0.72-1.35 while its text stayed native, so at GUI
 * scale 4 the list rows shrank under unshrunk names. Here the panel takes the room the screen has and
 * the columns are fitted to it.
 *
 * <p>Two arrangements:
 * <ul>
 *   <li>{@link Mode#WIDE} -- library | wand | details, side by side, with the bound-spell list under
 *       the wand diamond.</li>
 *   <li>{@link Mode#STACKED} -- when the three columns would starve the details (768p at GUI 3, and
 *       anything smaller): library | (wand above details), the bound-spell list beside the diamond if
 *       it fits, otherwise left to the sockets' tooltips.</li>
 * </ul>
 *
 * @param sockets  up, right, down, left -- the HUD diamond's order ({@code SpellDiamondOverlay})
 * @param bindings one row per socket in the same order; empty when the arrangement has no room for them
 * @param chips    one per filter, in the order their widths were given
 */
@NullMarked
public record SpellMenuLayout(Mode mode, Rect panel, Rect content, Rect title, int ruleY, Rect skills,
                              Rect library, Rect search, List<Rect> chips, Rect list, Rect scrollbar,
                              Rect wand, List<Rect> sockets, int hubX, int hubY, List<Rect> bindings,
                              Rect detail, Rect hint) {

    public enum Mode { WIDE, STACKED }

    /** Screen edge kept clear around the sheet. */
    static final int MARGIN = 8;
    /** The sheet stops growing here: past it, a wider sheet is only emptier columns. */
    static final int MAX_W = 600;
    static final int MAX_H = 360;
    /** Content inset from the sheet's edge: its 8px frame plus breathing room. */
    static final int INSET = 12;
    static final int GAP = 8;
    static final int HEADER_H = 14;
    static final int SKILLS_W = 60;
    static final int FOOTER_H = 10;
    /** Below this content width the details column would fall under ~130px: stack instead. */
    static final int WIDE_MIN_CONTENT_W = 420;

    static final int WELL_PAD = 4;
    static final int SEARCH_H = 14;
    static final int CHIP_H = 13;
    static final int CHIP_GAP = 2;
    public static final int ROW_H = 16;
    public static final int ROW_STEP = 17;
    static final int SCROLLBAR_W = 6;

    static final int WAND_W = 120;
    /** Socket edge, drawn at native size. */
    public static final int SOCKET = 30;
    /** Socket centre to diamond centre. Wide leaves room for the hub's channels; stacked saves height. */
    static final int SPACING_WIDE = 40;
    static final int SPACING_STACKED = 34;
    /** Under the bottom socket: its proficiency pips. */
    static final int PIP_ROOM = 6;
    public static final int BINDING_H = 12;
    static final int BINDINGS_MIN_W = 90;

    /**
     * The layout for a screen of {@code screenW} x {@code screenH} GUI pixels.
     *
     * @param chipWidths the width each filter chip needs for its label (the caller measures with its font)
     */
    public static SpellMenuLayout of(int screenW, int screenH, int[] chipWidths) {
        int pw = Math.min(MAX_W, screenW - 2 * MARGIN);
        int ph = Math.min(MAX_H, screenH - 2 * MARGIN);
        Rect panel = new Rect((screenW - pw) / 2, (screenH - ph) / 2, pw, ph);
        Rect content = panel.inset(INSET);

        Rect skills = new Rect(content.right() - SKILLS_W, content.y(), SKILLS_W, HEADER_H);
        Rect title = new Rect(content.x(), content.y(), content.w() - SKILLS_W - GAP, HEADER_H);
        int ruleY = content.y() + HEADER_H + 1;
        int bodyTop = ruleY + 8 + 2;
        Rect hint = new Rect(content.x(), content.bottom() - FOOTER_H + 1, content.w(), FOOTER_H - 1);
        int bodyBottom = hint.y() - 3;
        int bodyH = bodyBottom - bodyTop;

        Mode mode = content.w() >= WIDE_MIN_CONTENT_W ? Mode.WIDE : Mode.STACKED;
        int libW = mode == Mode.WIDE
                ? clamp(Math.round(content.w() * 0.36f), 150, 196)
                : clamp(Math.round(content.w() * 0.44f), 130, 180);
        Rect library = new Rect(content.x(), bodyTop, libW, bodyH);

        Rect search = new Rect(library.x() + WELL_PAD, library.y() + WELL_PAD,
                library.w() - 2 * WELL_PAD, SEARCH_H);
        List<Rect> chips = flow(search.x(), search.bottom() + 3, search.w(), chipWidths, CHIP_H, CHIP_GAP);
        int chipsBottom = chips.isEmpty() ? search.bottom() : chips.get(chips.size() - 1).bottom();
        int listTop = chipsBottom + 4;
        int listH = library.bottom() - WELL_PAD - listTop;
        // Whole rows only, so the last row is never half under the well's edge.
        listH = Math.max(ROW_STEP, listH - listH % ROW_STEP);
        Rect list = new Rect(library.x() + WELL_PAD, listTop,
                library.w() - 2 * WELL_PAD - SCROLLBAR_W - 2, listH);
        Rect scrollbar = new Rect(list.right() + 2, listTop, SCROLLBAR_W, listH);

        int rightX = library.right() + GAP;
        Rect wand;
        Rect detail;
        int spacing;
        List<Rect> bindings = new ArrayList<>(4);
        if (mode == Mode.WIDE) {
            spacing = SPACING_WIDE;
            wand = new Rect(rightX, bodyTop, WAND_W, bodyH);
            detail = new Rect(wand.right() + GAP, bodyTop, content.right() - wand.right() - GAP, bodyH);
        } else {
            spacing = SPACING_STACKED;
            int extent = 2 * spacing + SOCKET;
            int rightW = content.right() - rightX;
            int bindingsW = rightW - extent - GAP;
            int wandW = bindingsW >= BINDINGS_MIN_W ? rightW : extent;
            wand = new Rect(rightX, bodyTop, wandW, extent + PIP_ROOM);
            int detailTop = wand.bottom() + 4;
            detail = new Rect(rightX, detailTop, rightW, bodyBottom - detailTop);
        }

        int extent = 2 * spacing + SOCKET;
        int hubX = mode == Mode.WIDE ? wand.x() + wand.w() / 2 : wand.x() + extent / 2;
        int hubY = wand.y() + extent / 2;
        boolean wideBindings = false;
        if (mode == Mode.WIDE) {
            // Diamond and its rows as one group, a third of the way down the column: top-aligned it
            // left the column's lower half empty on a tall screen, centred it floated below the list.
            int groupH = extent + PIP_ROOM + 8 + 4 * BINDING_H;
            wideBindings = groupH <= wand.h();
            int used = wideBindings ? groupH : extent + PIP_ROOM;
            hubY += Math.max(0, wand.h() - used) / 3;
        }
        List<Rect> sockets = new ArrayList<>(4);
        int half = SOCKET / 2;
        int[][] dir = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] d : dir) {
            sockets.add(new Rect(hubX + d[0] * spacing - half, hubY + d[1] * spacing - half,
                    SOCKET, SOCKET));
        }

        if (wideBindings) {
            int top = hubY + extent / 2 + PIP_ROOM + 8;
            for (int i = 0; i < 4; i++) {
                bindings.add(new Rect(wand.x(), top + i * BINDING_H, wand.w(), BINDING_H));
            }
        } else if (mode == Mode.STACKED) if (wand.w() > extent) {
            int x = wand.x() + extent + GAP;
            int top = hubY - 2 * BINDING_H;
            for (int i = 0; i < 4; i++) {
                bindings.add(new Rect(x, top + i * BINDING_H, wand.right() - x, BINDING_H));
            }
        }

        return new SpellMenuLayout(mode, panel, content, title, ruleY, skills, library, search,
                List.copyOf(chips), list, scrollbar, wand, List.copyOf(sockets), hubX, hubY,
                List.copyOf(bindings), detail, hint);
    }

    /**
     * Left-to-right placement that wraps to a new row when the next box would cross {@code w}. A box
     * wider than the whole row is clamped to it rather than allowed out of the well.
     */
    static List<Rect> flow(int x, int y, int w, int[] widths, int h, int gap) {
        List<Rect> out = new ArrayList<>(widths.length);
        int cx = x;
        int cy = y;
        for (int width : widths) {
            int bw = Math.min(width, w);
            if (cx > x && cx + bw > x + w) {
                cx = x;
                cy += h + gap;
            }
            out.add(new Rect(cx, cy, bw, h));
            cx += bw + gap;
        }
        return out;
    }

    /** How many list rows fit. */
    public int visibleRows() {
        return list.h() / ROW_STEP;
    }

    /** The {@code i}-th visible row, counted from the top of the list (not from the scroll offset). */
    public Rect row(int i) {
        return new Rect(list.x(), list.y() + i * ROW_STEP, list.w(), ROW_H);
    }

    /** Visible row under the point, or -1. */
    public int rowAt(double x, double y) {
        if (!list.contains(x, y)) {
            return -1;
        }
        int i = (int) ((y - list.y()) / ROW_STEP);
        return y - list.y() - i * ROW_STEP < ROW_H && i < visibleRows() ? i : -1;
    }

    /** Socket under the point -- the socket itself or its bound-spell row -- or -1. */
    public int slotAt(double x, double y) {
        for (int i = 0; i < 4; i++) {
            if (sockets.get(i).contains(x, y) || (i < bindings.size() && bindings.get(i).contains(x, y))) {
                return i;
            }
        }
        return -1;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
