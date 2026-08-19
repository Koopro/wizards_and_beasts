package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.gui.character.widget.SkillTreeBarWidget;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillDataState;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Skills tab: one progress bar per region (all 8 {@link SkillTreeId}s), unlocked node chips, unspent points. */
public final class SkillsTab implements CharacterTab {

    private static final int COLOR_SECTION = 0xFFDDB97A;
    private static final int COLOR_LABEL   = 0xFF887766;
    private static final int COLOR_VALUE   = 0xFFEEDDBB;
    private static final int COLOR_CHIP_BG = 0xFF2A1E0F;
    private static final int COLOR_CHIP_HI = 0xFF44321A;
    private static final int COLOR_CHIP_SH = 0xFF160C00;
    private static final int COLOR_CHIP_TXT = 0xFFCCBB99;
    private static final int CHIP_H        = 9;
    private static final int CHIP_PAD      = 2;

    @Override
    public @NonNull String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.skills";
    }

    private float scrollOffset = 0f; // pixels scrolled from top
    private int lastTotalH = 0;      // content height measured last frame

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        Font font = Minecraft.getInstance().font;
        PlayerSkillData skillData = ClientSkillDataState.get();
        Map<String, Integer> unlocked = skillData.getUnlockedSkills();

        // Nothing here clipped to the tab's rect, so on a full skill list "Unspent Points" was
        // drawn below the sheet entirely — floating over the world under the panel.
        g.enableScissor(x, y, x + w, y + h);

        float maxScroll = Math.max(0, lastTotalH - h);
        scrollOffset = Mth.clamp(scrollOffset, 0f, maxScroll);

        int cx = x + 2;
        int cy = y + 2 - (int) scrollOffset;
        int contentTop = cy;
        // Scrollbar is overlaid on the right edge, so lay content out inside a width that
        // already excludes it — "Unspent: N" is right-aligned and would run underneath.
        int innerW = w - 4 - TabScrollbar.WIDTH;

        // ── Tree progress bars ────────────────────────────────────────────
        g.drawString(font, "Skill Trees", cx, cy, COLOR_SECTION, false);

        // Unspent points rides the section header rather than trailing the chips. It is the one
        // actionable number on this tab, and at the bottom it was the first thing to fall off the
        // end of a long list — the only readout here you would open the sheet specifically to check.
        String pointsText = "Unspent: " + skillData.getSkillPoints();
        g.drawString(font, pointsText, cx + innerW - font.width(pointsText), cy, COLOR_VALUE, false);
        cy += 10;

        for (SkillTreeId treeId : SkillTreeId.values()) {
            List<Skill> treeSkills = SkillTrees.clientGetTree(treeId);
            int total = treeSkills.size();
            int done  = (int) treeSkills.stream()
                    .filter(s -> unlocked.getOrDefault(s.getId(), 0) > 0)
                    .count();
            boolean locked = treeId == SkillTreeId.DARK_ARTS
                    && !ModuleManager.isEnabled(Module.DARK_ARTS);

            SkillTreeBarWidget.draw(g, cx, cy, innerW,
                    treeId.getDisplayName(), done, total,
                    treeId.getColor(), locked);
            cy += SkillTreeBarWidget.rowHeight();
        }
        cy += 4;

        // ── Unlocked node chips ───────────────────────────────────────────
        g.drawString(font, "Unlocked Nodes", cx, cy, COLOR_SECTION, false);
        cy += 10;

        List<String> nodeNames = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : unlocked.entrySet()) {
            if (entry.getValue() <= 0) continue;
            Skill skill = SkillTrees.clientById(entry.getKey());
            if (skill != null) {
                nodeNames.add(at.koopro.wizardsandbeasts.client.skill.gui.SkillTreeRenderHelper
                        .resolveDisplayName(skill.getDisplayName()));
            }
        }
        // Chips get the height they need; the tab scrolls rather than dropping rows on the floor.
        cy = drawChips(g, font, cx, cy, innerW, Integer.MAX_VALUE, nodeNames);

        g.disableScissor();

        lastTotalH = cy - contentTop + 4;

        TabScrollbar.draw(g, x, y, w, h, scrollOffset, lastTotalH);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float) (delta * 10.0);
        return true;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** Draws flow-layout chips; returns the y after the last row. */
    private int drawChips(@NonNull GuiGraphics g, @NonNull Font font,
                          int x, int y, int w, int maxH,
                          @NonNull List<String> labels) {
        if (labels.isEmpty()) {
            g.drawString(font, "None", x, y, COLOR_LABEL, false);
            return y + 9;
        }

        int rowX = x;
        int rowY = y;

        for (String label : labels) {
            int chipW = font.width(label) + CHIP_PAD * 2;
            if (rowX + chipW > x + w && rowX > x) {
                rowX = x;
                rowY += CHIP_H + 2;
                // Measured as a height from the top rather than an absolute y: callers pass
                // Integer.MAX_VALUE for "unbounded", and `y + maxH` overflows to a negative
                // number, which ends the loop after the first wrapped row.
                if ((rowY - y) + CHIP_H > maxH) break; // out of vertical space
            }
            at.koopro.wizardsandbeasts.client.gui.McStylePanel.drawPanel(
                    g, rowX, rowY, chipW, CHIP_H,
                    COLOR_CHIP_BG, COLOR_CHIP_HI, COLOR_CHIP_SH);
            g.drawString(font, label, rowX + CHIP_PAD, rowY + 1, COLOR_CHIP_TXT, false);
            rowX += chipW + 2;
        }
        return rowY + CHIP_H + 2;
    }
}
