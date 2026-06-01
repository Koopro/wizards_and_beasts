package at.koopro.wizardsandbeasts.client.gui.character.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.NonNull;

/** Renders a single skill-tree progress bar row. */
public final class SkillTreeBarWidget {

    private static final int BAR_H         = 6;
    private static final int COLOR_LABEL   = 0xFFCCBB99;
    private static final int COLOR_TRACK   = 0xFF1A1005;
    private static final int COLOR_LOCKED  = 0xFF443322;
    private static final int COLOR_RANK    = 0xFF887766;

    private SkillTreeBarWidget() {}

    /**
     * Draws one skill-tree row.
     *
     * @param g        GuiGraphics context
     * @param x        left edge
     * @param y        top edge
     * @param w        available width
     * @param treeName display name of the tree
     * @param unlocked nodes unlocked by the player in this tree
     * @param total    total nodes in this tree
     * @param barColor ARGB fill color (from SkillTreeId.getColor())
     * @param locked   if true, render as locked (module disabled)
     */
    public static void draw(@NonNull GuiGraphics g, int x, int y, int w,
                            @NonNull String treeName, int unlocked, int total,
                            int barColor, boolean locked) {
        Font font = Minecraft.getInstance().font;

        String rankText = locked ? "[Locked]" : unlocked + " / " + total;
        int rankW = font.width(rankText);

        // Tree name
        int nameColor = locked ? COLOR_LOCKED : COLOR_LABEL;
        String truncName = font.plainSubstrByWidth(treeName, w - rankW - 4);
        g.drawString(font, truncName, x, y, nameColor, false);

        // Rank label (right-aligned within w)
        g.drawString(font, rankText, x + w - rankW, y, COLOR_RANK, false);

        // Progress bar
        int barY = y + 9;
        g.fill(x, barY, x + w, barY + BAR_H, COLOR_TRACK);

        if (!locked && total > 0 && unlocked > 0) {
            int filled = (int)((float) unlocked / total * w);
            filled = Math.min(filled, w);
            g.fill(x, barY, x + filled, barY + BAR_H, barColor | 0xFF000000);
        }
    }

    /** Pixel height of a single row including the bar. */
    public static int rowHeight() {
        return 9 + BAR_H + 3; // label + bar + gap
    }
}
