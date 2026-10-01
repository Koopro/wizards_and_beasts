package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * A section's title in rubric, an optional one-line summary in secondary ink, and the double gilt rule
 * beneath — the page's only heading style, so every panel opens the same way.
 */
@NullMarked
public final class AdminSectionHeader {

    private AdminSectionHeader() {}

    /** Draws the header and returns the height it used, rule included. */
    public static int render(GuiGraphics g, Font font, int x, int y, int w, Component title, Component summary) {
        g.drawString(font, title, x, y, AdminTheme.RUBRIC, false);
        int used = font.lineHeight + 2;
        if (!summary.getString().isEmpty()) {
            List<FormattedCharSequence> lines = font.split(summary, w);
            // One line only: the header is a label, not a paragraph. The tooltip and the panel carry detail.
            if (!lines.isEmpty()) {
                g.drawString(font, lines.get(0), x, y + used, AdminTheme.INK_2, false);
                used += font.lineHeight + 1;
            }
        }
        AdminTheme.giltRule(g, x, y + used + 1, w);
        return used + 5;
    }

    /** A smaller in-panel sub-heading: ink-2 caps text and a single rule. */
    public static int renderSub(GuiGraphics g, Font font, int x, int y, int w, Component title) {
        g.drawString(font, title, x, y, AdminTheme.INK_2, false);
        g.fill(x, y + font.lineHeight + 1, x + w, y + font.lineHeight + 2, AdminTheme.PAPER_RULE);
        return font.lineHeight + 4;
    }
}
