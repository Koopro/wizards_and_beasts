package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Every rectangle on the heritage gate, from the screen size and the widths the labels need.
 *
 * <p>Painting, hit-testing and widget bounds all read it, in native GUI pixels. The screen it replaces
 * scaled a 424x240 design by up to 2.25 while its text stayed native, so on a large window the columns
 * grew and the words in them did not.
 *
 * <p>Three columns where they fit -- the heritages, the dossier (with lineage and condition above it),
 * the player you are about to become -- and two below {@link #THREE_MIN_CONTENT_W}, where the preview is
 * dropped and the traits fold into the dossier. Every heritage row is always on screen: there are eight,
 * and a choice this weighty should never hide one below a scroll.
 *
 * @param rows         one per heritage, in {@code Heritage.values()} order
 * @param lineageChips one per lineage of the shown heritage
 * @param condition    the condition cycler's label plate; {@code null} when the lineage can carry none
 * @param preview      {@code null} in the two-column arrangement
 * @param traits       {@code null} in the two-column arrangement (the dossier carries them)
 */
@NullMarked
public record HeritageSelectionLayout(Mode mode, Rect panel, Rect content, Rect title, int ruleY,
                                      Rect list, int listLabelY, List<Rect> rows,
                                      int lineageLabelY, List<Rect> lineageChips,
                                      int conditionLabelY, @Nullable Rect condition, @Nullable Rect conditionPrev,
                                      @Nullable Rect conditionNext,
                                      Rect dossier, @Nullable Rect preview, @Nullable Rect traits,
                                      Rect hint, Rect randomise, Rect confirm,
                                      Rect overlay, Rect overlayCancel, Rect overlaySeal) {

    public enum Mode { THREE, TWO }

    static final int MARGIN = 8;
    static final int MAX_W = 600;
    static final int MAX_H = 360;
    static final int INSET = 12;
    static final int GAP = 8;
    static final int HEADER_H = 14;
    static final int BUTTON_H = 16;
    static final int THREE_MIN_CONTENT_W = 360;

    static final int WELL_PAD = 4;
    public static final int LABEL_H = 11;
    public static final int ROW_H = 14;
    public static final int ROW_STEP = 15;
    static final int CHIP_H = 13;
    static final int CHIP_GAP = 2;
    static final int ARROW_W = 14;

    static final int OVERLAY_W = 260;
    /** Title, name, a condition line, three lines of body, the warning, the buttons. */
    static final int OVERLAY_H = 136;
    static final int OVERLAY_BUTTON_W = 96;

    /**
     * @param heritages    how many heritage rows
     * @param lineageW     the width each lineage chip needs
     * @param hasCondition whether the shown lineage can begin with a condition
     * @param randomiseW   width the Randomise button needs
     * @param confirmW     width the Confirm button needs
     */
    public static HeritageSelectionLayout of(int screenW, int screenH, int heritages, int[] lineageW,
                                             boolean hasCondition, int randomiseW, int confirmW) {
        int pw = Math.min(MAX_W, screenW - 2 * MARGIN);
        int ph = Math.min(MAX_H, screenH - 2 * MARGIN);
        Rect panel = new Rect((screenW - pw) / 2, (screenH - ph) / 2, pw, ph);
        Rect content = panel.inset(INSET);

        Rect title = new Rect(content.x(), content.y(), content.w(), HEADER_H);
        int ruleY = content.y() + HEADER_H + 1;
        int bodyTop = ruleY + 8 + 2;

        int footerY = content.bottom() - BUTTON_H;
        Rect confirm = new Rect(content.right() - confirmW, footerY, confirmW, BUTTON_H);
        Rect randomise = new Rect(confirm.x() - GAP - randomiseW, footerY, randomiseW, BUTTON_H);
        Rect hint = new Rect(content.x(), footerY + 4, Math.max(0, randomise.x() - GAP - content.x()), BUTTON_H - 4);
        int bodyBottom = footerY - 6;
        int bodyH = bodyBottom - bodyTop;

        Mode mode = content.w() >= THREE_MIN_CONTENT_W ? Mode.THREE : Mode.TWO;
        int leftW = mode == Mode.THREE ? clamp(Math.round(content.w() * 0.24f), 108, 140)
                : clamp(Math.round(content.w() * 0.36f), 96, 120);
        int rightW = mode == Mode.THREE ? clamp(Math.round(content.w() * 0.26f), 92, 150) : 0;

        Rect list = new Rect(content.x(), bodyTop, leftW, bodyH);
        int listLabelY = list.y() + WELL_PAD + 1;
        List<Rect> rows = new ArrayList<>(heritages);
        int rowTop = list.y() + WELL_PAD + LABEL_H + 2;
        for (int i = 0; i < heritages; i++) {
            rows.add(new Rect(list.x() + WELL_PAD, rowTop + i * ROW_STEP, list.w() - 2 * WELL_PAD, ROW_H));
        }

        int midX = list.right() + GAP;
        int midW = content.right() - midX - (rightW > 0 ? rightW + GAP : 0);
        int lineageLabelY = bodyTop + 1;
        List<Rect> chips = flow(midX, lineageLabelY + LABEL_H, midW, lineageW, CHIP_H, CHIP_GAP);
        int y = chips.isEmpty() ? lineageLabelY + LABEL_H : chips.get(chips.size() - 1).bottom();

        int conditionLabelY = -1;
        @Nullable Rect condition = null;
        @Nullable Rect prev = null;
        @Nullable Rect next = null;
        if (hasCondition) {
            conditionLabelY = y + 4;
            int cy = conditionLabelY + LABEL_H;
            prev = new Rect(midX, cy, ARROW_W, CHIP_H);
            next = new Rect(midX + midW - ARROW_W, cy, ARROW_W, CHIP_H);
            condition = new Rect(prev.right() + CHIP_GAP, cy, next.x() - CHIP_GAP - prev.right() - CHIP_GAP, CHIP_H);
            y = cy + CHIP_H;
        }
        Rect dossier = new Rect(midX, y + 6, midW, bodyBottom - y - 6);

        @Nullable Rect preview = null;
        @Nullable Rect traits = null;
        if (rightW > 0) {
            int rx = content.right() - rightW;
            int previewH = clamp(Math.round(bodyH * 0.5f), 60, 130);
            preview = new Rect(rx, bodyTop, rightW, previewH);
            traits = new Rect(rx, preview.bottom() + 6, rightW, bodyBottom - preview.bottom() - 6);
        }

        int ow = Math.min(OVERLAY_W, content.w());
        int oh = Math.min(OVERLAY_H, content.h());
        Rect overlay = new Rect((screenW - ow) / 2, (screenH - oh) / 2, ow, oh);
        int bw = Math.min(OVERLAY_BUTTON_W, (ow - 3 * GAP) / 2);
        int by = overlay.bottom() - INSET - BUTTON_H;
        Rect cancel = new Rect(overlay.x() + ow / 2 - GAP / 2 - bw, by, bw, BUTTON_H);
        Rect seal = new Rect(overlay.x() + ow / 2 + GAP / 2, by, bw, BUTTON_H);

        return new HeritageSelectionLayout(mode, panel, content, title, ruleY, list, listLabelY, List.copyOf(rows),
                lineageLabelY, List.copyOf(chips), conditionLabelY, condition, prev, next,
                dossier, preview, traits, hint, randomise, confirm, overlay, cancel, seal);
    }

    /** Left-to-right placement, wrapping when the next box would cross {@code w}. */
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

    /** Heritage row under the point, or -1. */
    public int rowAt(double x, double y) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
