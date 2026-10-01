package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * An inline notice on the page: a coloured bar and glyph on the left, a faint wash, and wrapped ink text. Used for
 * section-wide facts an administrator must not miss — "these are client settings", "a restart is pending" — and
 * inside the confirmation dialog.
 *
 * <p>Three severities, told apart by glyph as well as colour so they read without colour vision:
 * {@link Severity#INFO} (ℹ, Ministry purple) explains, {@link Severity#WARNING} (⚠, gilt) asks for attention,
 * {@link Severity#DANGER} (✖, sealing wax) is for what affects every player or cannot be undone.
 */
@NullMarked
public final class AdminWarning {

    public enum Severity {
        INFO("ℹ", AdminTheme.FRAME_RAISED, AdminTheme.INFO_WASH),
        WARNING("⚠", AdminTheme.GOLD_DARK, AdminTheme.GILT_WASH),
        DANGER("✖", AdminTheme.WAX, AdminTheme.WARNING_WASH);

        private final String glyph;
        private final int bar;
        private final int wash;

        Severity(String glyph, int bar, int wash) {
            this.glyph = glyph;
            this.bar = bar;
            this.wash = wash;
        }

        public String glyph() {
            return glyph;
        }

        public int bar() {
            return bar;
        }
    }

    private static final int BAR_W = 3;
    private static final int INSET = 5;
    private static final int GLYPH_W = 10;

    private AdminWarning() {}

    /** Height {@link #render} will use at this width. */
    public static int measure(Font font, int w, Component text) {
        return font.split(text, textWidth(w)).size() * (font.lineHeight + 1) + 2 * INSET - 1;
    }

    private static int textWidth(int w) {
        return w - BAR_W - 2 * INSET - GLYPH_W;
    }

    /** Draws a {@link Severity#WARNING} notice and returns its height. */
    public static int render(GuiGraphics g, Font font, int x, int y, int w, Component text) {
        return render(g, font, x, y, w, text, Severity.WARNING);
    }

    /** Draws the notice and returns its height. */
    public static int render(GuiGraphics g, Font font, int x, int y, int w, Component text, Severity severity) {
        List<FormattedCharSequence> lines = font.split(text, textWidth(w));
        int h = lines.size() * (font.lineHeight + 1) + 2 * INSET - 1;
        g.fill(x, y, x + w, y + h, severity.wash);
        g.fill(x, y, x + BAR_W, y + h, severity.bar);
        g.drawString(font, severity.glyph, x + BAR_W + INSET - 1, y + INSET, severity.bar, false);
        int lineY = y + INSET;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + BAR_W + INSET + GLYPH_W, lineY, AdminTheme.INK, false);
            lineY += font.lineHeight + 1;
        }
        return h;
    }
}
