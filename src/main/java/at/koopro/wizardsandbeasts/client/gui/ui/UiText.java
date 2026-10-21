package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The type hierarchy: five roles, one font, no scaling.
 *
 * <p>Minecraft has one typeface at one size, so hierarchy is colour, case, position and space, never
 * size. Text is <b>never shrunk</b> to fit on a player-facing screen -- at GUI scale 4 a 0.7x glyph is
 * four screen pixels tall and unreadable. What does not fit is shortened with an ellipsis
 * ({@link #fit}) and the full text goes in a tooltip.
 *
 * <table>
 *   <tr><th>Role</th><th>Colour</th><th>Step after it</th><th>Use</th></tr>
 *   <tr><td>{@link Role#TITLE}</td><td>surface ink</td><td>{@link WizardsMetrics#LINE_TITLE} 20</td><td>one per screen, top-left of the panel, followed by a divider</td></tr>
 *   <tr><td>{@link Role#HEADING}</td><td>surface heading (rubric / gilt)</td><td>{@link WizardsMetrics#LINE_SECTION} 16</td><td>a section inside a panel</td></tr>
 *   <tr><td>{@link Role#BODY}</td><td>ink</td><td>{@link WizardsMetrics#LINE_BODY} 12</td><td>prose, values, list entries</td></tr>
 *   <tr><td>{@link Role#LABEL}</td><td>ink2</td><td>{@link WizardsMetrics#LINE_BODY} 12</td><td>the name half of a name/value pair</td></tr>
 *   <tr><td>{@link Role#CAPTION}</td><td>ink3</td><td>{@link WizardsMetrics#LINE_TIGHT} 10</td><td>hints, placeholders, footnotes -- never something a player must read to act</td></tr>
 * </table>
 */
@NullMarked
public final class UiText {

    public enum Role {
        TITLE(WizardsMetrics.LINE_TITLE),
        HEADING(WizardsMetrics.LINE_SECTION),
        BODY(WizardsMetrics.LINE_BODY),
        LABEL(WizardsMetrics.LINE_BODY),
        CAPTION(WizardsMetrics.LINE_TIGHT);

        private final int step;

        Role(int step) {
            this.step = step;
        }

        /** How far down the next line of the same role starts. */
        public int step() {
            return step;
        }

        public int colour(UiSurface surface) {
            return switch (this) {
                case TITLE, BODY -> surface.ink();
                case HEADING -> surface.heading();
                case LABEL -> surface.ink2();
                case CAPTION -> surface.ink3();
            };
        }
    }

    public static final String ELLIPSIS = "…";

    private UiText() {}

    /** Draws one line in a role's colour, left-aligned. Returns the y of the next line. */
    public static int draw(GuiGraphics g, Font font, UiSurface surface, Role role, Component text, int x, int y) {
        g.drawString(font, text, x, y, role.colour(surface), surface.shadow());
        return y + role.step();
    }

    /** Draws in an explicit colour (a good/bad value, a grade) with the surface's shadow rule. */
    public static void draw(GuiGraphics g, Font font, UiSurface surface, Component text, int x, int y, int colour) {
        g.drawString(font, text, x, y, colour, surface.shadow());
    }

    /** One line, shortened with an ellipsis to {@code maxW}. Returns whether it had to be shortened. */
    public static boolean drawFit(GuiGraphics g, Font font, UiSurface surface, Component text,
                                  int x, int y, int maxW, int colour) {
        String full = text.getString();
        String shown = fit(font, full, maxW);
        g.drawString(font, shown, x, y, colour, surface.shadow());
        return !shown.equals(full);
    }

    /** Right-aligned so its last glyph ends at {@code right}. */
    public static void drawRight(GuiGraphics g, Font font, UiSurface surface, Component text,
                                 int right, int y, int colour) {
        g.drawString(font, text, right - font.width(text), y, colour, surface.shadow());
    }

    public static void drawCentered(GuiGraphics g, Font font, UiSurface surface, Component text,
                                    int centerX, int y, int colour) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, colour, surface.shadow());
    }

    /**
     * Wrapped prose in a role's colour. Returns the y below the last line. Lines past {@code maxLines}
     * are dropped and the last kept line ends in an ellipsis.
     */
    public static int drawWrapped(GuiGraphics g, Font font, UiSurface surface, Role role, Component text,
                                  int x, int y, int w, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, w);
        int colour = role.colour(surface);
        int step = role == Role.CAPTION ? WizardsMetrics.LINE_TIGHT : WizardsMetrics.LINE_BODY;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            if (i == maxLines - 1 && lines.size() > maxLines) {
                String last = plain(lines.get(i));
                g.drawString(font, fit(font, last + ELLIPSIS, w), x, y, colour, surface.shadow());
            } else {
                g.drawString(font, lines.get(i), x, y, colour, surface.shadow());
            }
            y += step;
        }
        return y;
    }

    private static String plain(FormattedCharSequence line) {
        StringBuilder sb = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    /** How many lines {@code text} wraps to at width {@code w}. */
    public static int lineCount(Font font, Component text, int w) {
        return font.split(text, w).size();
    }

    /** {@code text} shortened with an ellipsis so it fits {@code maxW}. Unchanged when it already fits. */
    public static String fit(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) {
            return text;
        }
        int ell = font.width(ELLIPSIS);
        if (maxW <= ell) {
            return "";
        }
        return font.plainSubstrByWidth(text, maxW - ell).stripTrailing() + ELLIPSIS;
    }
}
