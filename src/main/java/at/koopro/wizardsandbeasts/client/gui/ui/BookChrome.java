package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * An open book lying flat (UI renaissance P6): a cover nine-sliced around two parchment pages, the pages darkening into
 * the gutter, a strip of binding between them and a silk ribbon across the right page. Shared by the field journal
 * and the brewing log so the two books are built alike; each brings its own cover and ribbon.
 */
@NullMarked
public final class BookChrome {

    public static final int BORDER = 8;
    public static final int SPINE_W = 6;
    private static final Identifier PAGE = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
            "textures/gui/journal/page.png");

    /** A book's cover art and ribbon colours. */
    public record Style(Identifier cover, int spine, int ribbon, int ribbonShade) {}

    public static final Style FIELD_JOURNAL = new Style(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/journal/cover.png"),
            0xFF4A2A18, 0xFF8E2320, 0xFF5A1410);
    public static final Style BREWING_LOG = new Style(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/journal/cover_brewing.png"),
            0xFF16301F, 0xFFC9973A, 0xFF8F6522);

    private BookChrome() {}

    public static int pageW(int w) {
        return (w - 2 * BORDER - SPINE_W) / 2;
    }

    public static int leftPageX(int x) {
        return x + BORDER;
    }

    public static int rightPageX(int x, int w) {
        return x + BORDER + pageW(w) + SPINE_W;
    }

    /** Where the ribbon lies, from the right page's outer edge: text on that page stays clear of it. */
    public static final int RIBBON_INSET = 11;

    public static void draw(GuiGraphics g, Style style, int x, int y, int w, int h) {
        McStylePanel.drawNineSliceTiled(g, style.cover(), x, y, w, h, 32, BORDER);
        int pw = pageW(w);
        int py = y + BORDER - 2;
        int ph = h - 2 * BORDER + 4;
        McStylePanel.drawNineSliceTiled(g, PAGE, leftPageX(x), py, pw, ph, 32, 0);
        McStylePanel.drawNineSliceTiled(g, PAGE, rightPageX(x, w), py, pw, ph, 32, 0);
        for (int i = 0; i < 6; i++) {
            int a = 0x10 + i * 0x0C;
            g.fill(leftPageX(x) + pw - 6 + i, py, leftPageX(x) + pw - 5 + i, py + ph, (a << 24) | 0x3A2410);
            g.fill(rightPageX(x, w) + 5 - i, py, rightPageX(x, w) + 6 - i, py + ph, (a << 24) | 0x3A2410);
        }
        g.fill(leftPageX(x) + pw, py - 1, rightPageX(x, w), py + ph + 1, style.spine());
        int rx = rightPageX(x, w) + pw - RIBBON_INSET;
        g.fill(rx, y + 2, rx + 5, y + h + 6, style.ribbon());
        g.fill(rx + 4, y + 2, rx + 5, y + h + 6, style.ribbonShade());
        g.fill(rx, y + h + 6, rx + 2, y + h + 8, style.ribbon());
        g.fill(rx + 3, y + h + 6, rx + 5, y + h + 8, style.ribbon());
    }
}
