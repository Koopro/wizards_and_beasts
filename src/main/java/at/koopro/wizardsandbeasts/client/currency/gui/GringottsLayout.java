package at.koopro.wizardsandbeasts.client.currency.gui;

import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Every rectangle on the Gringotts counter, from the screen size and the widths its labels need.
 *
 * <p>Native GUI pixels, never scaled: the screen this replaces scaled a 400x256 design by 0.72-1.35 while
 * its text stayed native, and at 720p its labels no longer fitted their buttons. Painting, widget bounds
 * and hit-tests all read this.
 *
 * <p>Two ledger pages on the bank's marble -- the <b>vault page</b> (coin rows: icon, name, vault count,
 * the deposit/withdraw arrows between the two counts, purse count; the amount picker; the bulk buttons;
 * the totals) and the <b>counter page</b> (coin exchanges, the Dragot board and its trades, the door to the
 * bank's business). Side by side from {@link #TWO_PAGE_MIN_CONTENT_W}; below it the pages share one rect
 * and two tabs choose between them.
 *
 * @param tabs empty in the two-page arrangement
 */
@NullMarked
public record GringottsLayout(Mode mode, Rect panel, Rect content, int titleY, int ruleY, List<Rect> tabs,
                              Rect vaultPage, Rect counterPage, Vault vault, Counter counter) {

    public enum Mode { TWO_PAGE, TABBED }

    /**
     * The vault page.
     *
     * @param rows       one per coin, galleon / sickle / knut, full page width
     * @param vaultRight right edge of the vault count column
     * @param deposit    the "into the vault" arrow of each row
     * @param withdraw   the "out to the purse" arrow of each row
     */
    public record Vault(int headingY, List<Rect> amounts, int columnHeadY, List<Rect> rows,
                        int iconX, int nameX, int nameRight, int vaultRight,
                        List<Rect> deposit, List<Rect> withdraw, int walletRight,
                        Rect depositAll, Rect withdrawAll, int footRuleY, int totalsY) {}

    /** The counter page: two exchange rows of two, the Dragot board, two Dragot rows of two, the business door. */
    public record Counter(int exchangeHeadY, List<Rect> exchange, int dragotHeadY, int boardY,
                          List<Rect> dragot, Rect business) {}

    /** What the screen measured with its font. */
    public record Measure(int[] amountChipW, int vaultColW, int walletColW, int tabW) {}

    static final int MARGIN = 8;
    static final int MAX_W = 460;
    /**
     * The sheet is as tall as its pages need and no taller: two pages side by side need 204, the tabbed
     * arrangement 224 (a tab row on top, and one page holding the longer column).
     */
    static final int MAX_H_TWO_PAGE = 204;
    static final int MAX_H_TABBED = 224;
    /** Marble shown round the pages: the frame's gold inlay sits 4-5px in. */
    static final int INSET = 10;
    static final int GUTTER = 8;
    static final int PAGE_PAD = 7;
    static final int TWO_PAGE_MIN_CONTENT_W = 360;
    public static final int BUTTON_H = 16;
    public static final int CHIP_H = 13;
    public static final int ROW_H = 21;
    /** Wide enough for the arrow glyph inside the button's own padding (at 17 it ellipsised to nothing). */
    public static final int ARROW_W = 20;
    static final int ICON = 16;

    public static GringottsLayout of(int screenW, int screenH, Measure m) {
        int pw = Math.min(MAX_W, screenW - 2 * MARGIN);
        Mode mode = pw - 2 * INSET >= TWO_PAGE_MIN_CONTENT_W ? Mode.TWO_PAGE : Mode.TABBED;
        int ph = Math.min(mode == Mode.TWO_PAGE ? MAX_H_TWO_PAGE : MAX_H_TABBED, screenH - 2 * MARGIN);
        Rect panel = new Rect((screenW - pw) / 2, (screenH - ph) / 2, pw, ph);
        Rect content = panel.inset(INSET);
        int titleY = content.y() + 3;
        int ruleY = titleY + 12;
        int bodyTop = ruleY + 5;

        List<Rect> tabs = new ArrayList<>(2);
        Rect vaultPage;
        Rect counterPage;
        if (mode == Mode.TWO_PAGE) {
            int vaultW = clamp(Math.round((content.w() - GUTTER) * 0.55f), 190, 230);
            vaultPage = new Rect(content.x(), bodyTop, vaultW, content.bottom() - bodyTop);
            counterPage = new Rect(vaultPage.right() + GUTTER, bodyTop, content.right() - vaultPage.right() - GUTTER,
                    content.bottom() - bodyTop);
        } else {
            int tabY = bodyTop;
            tabs.add(new Rect(content.x(), tabY, m.tabW(), CHIP_H));
            tabs.add(new Rect(content.x() + m.tabW() + 2, tabY, m.tabW(), CHIP_H));
            int pageTop = tabY + CHIP_H + 3;
            vaultPage = new Rect(content.x(), pageTop, content.w(), content.bottom() - pageTop);
            counterPage = vaultPage;
        }
        return new GringottsLayout(mode, panel, content, titleY, ruleY, List.copyOf(tabs), vaultPage, counterPage,
                vault(vaultPage.inset(PAGE_PAD), m), counter(counterPage.inset(PAGE_PAD)));
    }

    private static Vault vault(Rect in, Measure m) {
        int headingY = in.y();
        // Amount chips right-aligned on the heading line.
        List<Rect> amounts = new ArrayList<>(4);
        int ax = in.right();
        int[] w = m.amountChipW();
        int[] xs = new int[w.length];
        for (int i = w.length - 1; i >= 0; i--) {
            ax -= w[i];
            xs[i] = ax;
            ax -= 2;
        }
        for (int i = 0; i < w.length; i++) {
            amounts.add(new Rect(xs[i], headingY - 2, w[i], CHIP_H));
        }
        int columnHeadY = headingY + CHIP_H + 4;
        int walletRight = in.right();
        int arrowsRight = walletRight - m.walletColW() - 5;
        int arrowsLeft = arrowsRight - 2 * ARROW_W - 2;
        int vaultRight = arrowsLeft - 5;
        int iconX = in.x();
        int nameX = iconX + ICON + 4;
        int nameRight = vaultRight - m.vaultColW() - 4;

        List<Rect> rows = new ArrayList<>(3);
        List<Rect> deposit = new ArrayList<>(3);
        List<Rect> withdraw = new ArrayList<>(3);
        int rowTop = columnHeadY + 11;
        for (int i = 0; i < 3; i++) {
            Rect row = new Rect(in.x(), rowTop + i * ROW_H, in.w(), ROW_H);
            rows.add(row);
            int by = row.y() + (ROW_H - BUTTON_H) / 2;
            deposit.add(new Rect(arrowsLeft, by, ARROW_W, BUTTON_H));
            withdraw.add(new Rect(arrowsLeft + ARROW_W + 2, by, ARROW_W, BUTTON_H));
        }
        int bulkY = rowTop + 3 * ROW_H + 5;
        int half = (in.w() - 4) / 2;
        Rect depositAll = new Rect(in.x(), bulkY, half, BUTTON_H);
        Rect withdrawAll = new Rect(in.right() - half, bulkY, half, BUTTON_H);
        int footRuleY = bulkY + BUTTON_H + 6;
        int totalsY = footRuleY + 5;
        return new Vault(headingY, List.copyOf(amounts), columnHeadY, List.copyOf(rows), iconX, nameX, nameRight,
                vaultRight, List.copyOf(deposit), List.copyOf(withdraw), walletRight, depositAll, withdrawAll,
                footRuleY, totalsY);
    }

    private static Counter counter(Rect in) {
        int half = (in.w() - 4) / 2;
        int exchangeHeadY = in.y();
        List<Rect> exchange = new ArrayList<>(4);
        int ey = exchangeHeadY + 12;
        for (int r = 0; r < 2; r++) {
            exchange.add(new Rect(in.x(), ey + r * (BUTTON_H + 3), half, BUTTON_H));
            exchange.add(new Rect(in.right() - half, ey + r * (BUTTON_H + 3), half, BUTTON_H));
        }
        int dragotHeadY = ey + 2 * (BUTTON_H + 3) + 7;
        int boardY = dragotHeadY + 12;
        List<Rect> dragot = new ArrayList<>(4);
        int dy = boardY + 22;
        for (int r = 0; r < 2; r++) {
            dragot.add(new Rect(in.x(), dy + r * (BUTTON_H + 3), half, BUTTON_H));
            dragot.add(new Rect(in.right() - half, dy + r * (BUTTON_H + 3), half, BUTTON_H));
        }
        Rect business = new Rect(in.x(), in.bottom() - BUTTON_H, in.w(), BUTTON_H);
        return new Counter(exchangeHeadY, List.copyOf(exchange), dragotHeadY, boardY, List.copyOf(dragot), business);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
