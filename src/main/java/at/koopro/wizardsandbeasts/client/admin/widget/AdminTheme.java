package at.koopro.wizardsandbeasts.client.admin.widget;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.NullMarked;

/**
 * The Control Center's visual language, drawn from {@link WizardsPalette} rather than invented.
 *
 * <p>Administration speaks for the <em>Ministry</em>, and the palette already has that brand: the emblem's
 * purple for the framing, its parchment stock for the working surface, gilt for accents, rubric red for
 * headings. So the frame is {@link WizardsPalette#MINISTRY_DARK}, the page is {@link WizardsPalette#PARCHMENT},
 * and nothing here is a new literal except the translucent washes, which are the palette's colours at a
 * lower alpha.
 *
 * <p>Flat fills with a one-pixel lit edge, not nine-sliced art: the panel is dense with small controls, and
 * the parchment kit's torn edges and seals would be decoration competing with data.
 */
@NullMarked
public final class AdminTheme {

    // ── frame: the Ministry's purple ──
    public static final int FRAME = WizardsPalette.MINISTRY_DARK;
    public static final int FRAME_RAISED = WizardsPalette.MINISTRY;
    public static final int FRAME_HOVER = WizardsPalette.MINISTRY_LIGHT;
    public static final int FRAME_TEXT = WizardsPalette.BRASS_HI;
    public static final int FRAME_TEXT_DIM = WizardsPalette.TEXT_DIM;
    /** "Refused" on the purple chrome: the page's red is too dark there, so a lighter rose of the same hue. */
    public static final int FRAME_BAD = 0xFFE0857D;
    /** "Saved" on the purple chrome, likewise a lighter green. */
    public static final int FRAME_GOOD = 0xFF9BD08A;

    // ── accents: muted gilt ──
    public static final int GOLD = WizardsPalette.GILT;
    public static final int GOLD_DARK = WizardsPalette.GILT_DARK;
    public static final int GOLD_LIGHT = WizardsPalette.GILT_LIGHT;

    // ── the working page ──
    public static final int PAPER = WizardsPalette.PARCHMENT;
    public static final int PAPER_SHADE = WizardsPalette.PARCHMENT_SHADE;
    public static final int PAPER_DEEP = WizardsPalette.PAGE_SHADE;
    public static final int PAPER_RULE = WizardsPalette.PAGE_DEEP;
    public static final int PAPER_SELECT = WizardsPalette.PAGE_SELECT;
    public static final int INK = WizardsPalette.PARCHMENT_INK;
    public static final int INK_2 = WizardsPalette.PAGE_INK_2;
    public static final int INK_3 = WizardsPalette.PAGE_INK_3;
    public static final int RUBRIC = WizardsPalette.PAGE_RUBRIC;
    public static final int GOOD = WizardsPalette.PAGE_GOOD;
    public static final int BAD = WizardsPalette.PAGE_BAD;
    public static final int WAX = WizardsPalette.WAX;

    /** A row under the pointer: the gilt selection tint, thin enough that the page still reads. */
    public static final int ROW_HOVER = 0x55000000 | (WizardsPalette.PAGE_SELECT & 0xFFFFFF);
    /** Behind a modal: the frame colour, most of the way opaque. */
    public static final int SCRIM = 0xC0000000 | (WizardsPalette.MINISTRY_DARK & 0xFFFFFF);
    /** A warning banner's wash: wax at low alpha, so the paper grain is still the material. */
    public static final int WARNING_WASH = 0x33000000 | (WizardsPalette.WAX & 0xFFFFFF);
    /** A caution banner's wash: gilt at low alpha. */
    public static final int GILT_WASH = 0x33000000 | (WizardsPalette.GILT & 0xFFFFFF);
    /** An information banner's wash: the Ministry purple at low alpha. */
    public static final int INFO_WASH = 0x22000000 | (WizardsPalette.MINISTRY & 0xFFFFFF);
    /** The outline of the widget that has keyboard focus. */
    public static final int FOCUS = WizardsPalette.GILT_LIGHT;

    // ── metrics (WizardsMetrics' 4pt scale) ──
    public static final int PAD = 8;
    public static final int GAP = 4;
    public static final int CONTROL_H = 16;
    public static final int ROW_H = 26;

    private AdminTheme() {}

    /** A raised rectangle: face, one-pixel border, lit top edge. */
    public static void raised(GuiGraphics g, int x, int y, int w, int h, int face, int border, int highlight) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, highlight);
    }

    /** A recessed well on the page: dark top edge, paper-deep face. */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PAPER_RULE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PAPER_DEEP);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, INK_3);
    }

    /** A one-pixel outline. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * A light gilt ring around {@code widget} while it holds keyboard focus and the last input was a key — so Tab and
     * arrow navigation always show where they are, without lighting every control the mouse last clicked.
     */
    public static void focusRing(GuiGraphics g, net.minecraft.client.gui.components.AbstractWidget widget) {
        if (widget.isFocused() && net.minecraft.client.Minecraft.getInstance().getLastInputType().isKeyboard()) {
            outline(g, widget.getX() - 1, widget.getY() - 1, widget.getWidth() + 2, widget.getHeight() + 2, FOCUS);
        }
    }

    /** The double gilt rule under a heading. */
    public static void giltRule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, GOLD_DARK);
        g.fill(x, y + 2, x + w, y + 3, GOLD);
    }
}
