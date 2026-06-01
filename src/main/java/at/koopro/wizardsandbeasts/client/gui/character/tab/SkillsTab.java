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
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Skills tab: 5 tree bars, unlocked node chips, unspent points. */
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

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        Font font = Minecraft.getInstance().font;
        PlayerSkillData skillData = ClientSkillDataState.get();
        Map<String, Integer> unlocked = skillData.getUnlockedSkills();

        int cx = x + 2;
        int cy = y + 2;

        // ── Tree progress bars ────────────────────────────────────────────
        g.drawString(font, "Skill Trees", cx, cy, COLOR_SECTION, false);
        cy += 10;

        for (SkillTreeId treeId : SkillTreeId.values()) {
            List<Skill> treeSkills = SkillTrees.getTree(treeId);
            int total = treeSkills.size();
            int done  = (int) treeSkills.stream()
                    .filter(s -> unlocked.getOrDefault(s.getId(), 0) > 0)
                    .count();
            boolean locked = treeId == SkillTreeId.DARK_ARTS
                    && !ModuleManager.isEnabled(Module.DARK_ARTS);

            SkillTreeBarWidget.draw(g, cx, cy, w - 4,
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
            Skill skill = SkillTrees.byId(entry.getKey());
            if (skill != null) nodeNames.add(skill.getDisplayName());
        }
        cy = drawChips(g, font, cx, cy, w - 4, h - (cy - y), nodeNames);
        cy += 4;

        // ── Unspent points ────────────────────────────────────────────────
        String pointsText = "Unspent Points: " + skillData.getSkillPoints();
        g.drawString(font, pointsText, cx, cy, COLOR_VALUE, false);
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
                if (rowY + CHIP_H > y + maxH) break; // out of vertical space
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
