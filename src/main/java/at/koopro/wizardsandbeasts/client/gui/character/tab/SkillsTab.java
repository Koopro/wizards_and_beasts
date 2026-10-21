package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.character.widget.SkillTreeBarWidget;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillDataState;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.build.BuildProfile;
import at.koopro.wizardsandbeasts.skill.build.BuildRules;
import at.koopro.wizardsandbeasts.skill.build.BuildTier;
import at.koopro.wizardsandbeasts.client.skill.gui.SkillTreeRenderHelper;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Skills tab: the player's build first — who the web says they are, the pathways they are walking, what to take
 * next and the capstone ahead — then one progress bar per region, unlocked node chips and unspent points.
 */
public final class SkillsTab implements CharacterTab {

    private static final int COLOR_SECTION = WizardsPalette.PAGE_RUBRIC;
    private static final int COLOR_LABEL   = WizardsPalette.PAGE_INK_2;
    private static final int COLOR_VALUE   = WizardsPalette.PAGE_INK;
    private static final int COLOR_CHIP_BG = WizardsPalette.PAGE_LIGHT;
    private static final int COLOR_CHIP_HI = 0xFFF6ECD0;
    private static final int COLOR_CHIP_SH = WizardsPalette.PAGE_DEEP;
    private static final int COLOR_CHIP_TXT = WizardsPalette.PAGE_INK;
    private static final int CHIP_H        = 9;
    private static final int CHIP_PAD      = 2;

    @Override
    public @NonNull String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.skills";
    }

    private float scrollOffset = 0f; // pixels scrolled from top
    private int lastTotalH = 0;      // content height measured last frame

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h,
                       int mouseX, int mouseY, float partialTick) {
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

        cy = drawBuild(g, font, cx, cy, innerW, unlocked);

        // ── Tree progress bars ────────────────────────────────────────────
        g.drawString(font, Component.translatable(S + "trees"), cx, cy, COLOR_SECTION, false);

        // Unspent points rides the section header rather than trailing the chips. It is the one
        // actionable number on this tab, and at the bottom it was the first thing to fall off the
        // end of a long list — the only readout here you would open the sheet specifically to check.
        String pointsText = Component.translatable(S + "unspent", skillData.getSkillPoints()).getString();
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
        g.drawString(font, Component.translatable(S + "nodes"), cx, cy, COLOR_SECTION, false);
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

    // ── build ─────────────────────────────────────────────────────────────

    /** Pathways shown at most; past three a sheet is a list, not a portrait. */
    private static final int MAX_PATHWAYS = 3;
    private static final String B = "gui.wizards_and_beasts.character_sheet.build.";
    private static final String S = "gui.wizards_and_beasts.character_sheet.skills.";

    /**
     * The build block: current build and how it was decided, then each active pathway's tier counts, the
     * capstone's state, and the next meaningful choices on that road. Everything is derived from the synced
     * allocation through {@code BuildProfile}, the same model {@code /wandb skill build} prints.
     */
    private int drawBuild(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                          @NonNull Map<String, Integer> unlocked) {
        // Cached on the skill sync: the profile walks every archetype's pathway and was rebuilt each frame.
        BuildProfile profile = at.koopro.wizardsandbeasts.client.gui.character.SheetCaches.build();
        g.drawString(font, Component.translatable(B + "header"), x, y, COLOR_SECTION, false);
        Component current = profile.current() == null
                ? Component.translatable(B + "status.undecided")
                : Component.translatable(B + "status." + profile.status().name().toLowerCase(Locale.ROOT),
                        Component.translatable(profile.current().nameKey()));
        g.drawString(font, current, x + w - font.width(current), y, COLOR_VALUE, false);
        y += 11;

        if (profile.pathways().isEmpty()) {
            for (var line : font.split(Component.translatable(B + "empty"), w)) {
                g.drawString(font, line, x, y, COLOR_LABEL, false);
                y += 9;
            }
            return y + 6;
        }

        int shown = 0;
        for (BuildProfile.PathwayProgress p : profile.pathways()) {
            if (shown++ >= MAX_PATHWAYS) {
                break;
            }
            Component name = Component.translatable(p.archetype().nameKey());
            g.drawString(font, name, x, y, COLOR_VALUE, false);
            String tiers = I18n.get(B + "tiers",
                    p.count(BuildTier.EARLY), p.size(BuildTier.EARLY),
                    p.count(BuildTier.MID), p.size(BuildTier.MID),
                    p.count(BuildTier.ADVANCED), p.size(BuildTier.ADVANCED));
            g.drawString(font, tiers, x + w - font.width(tiers), y, COLOR_LABEL, false);
            y += 10;

            Skill capstone = SkillTrees.clientById(p.archetype().capstone());
            Component capName = capstone == null ? Component.literal(p.archetype().capstone())
                    : Component.literal(SkillTreeRenderHelper.resolveDisplayName(capstone.getDisplayName()));
            Component capLine;
            if (p.capstoneHeld()) {
                capLine = Component.translatable(B + "capstone.held", capName);
            } else if (p.capstoneRefusal() == null) {
                capLine = Component.translatable(B + "capstone.open", capName);
            } else if (BuildRules.CAPSTONE_SHALLOW.equals(p.capstoneRefusal())) {
                capLine = Component.translatable(B + "capstone.shallow", capName, p.advancedStillNeeded());
            } else if (BuildRules.CAPSTONE_TAKEN.equals(p.capstoneRefusal())) {
                capLine = Component.translatable(B + "capstone.taken", capName);
            } else {
                capLine = Component.translatable(B + "capstone.sealed", capName);
            }
            for (var line : font.split(capLine, w - 4)) {
                g.drawString(font, line, x + 4, y, p.capstoneHeld() ? COLOR_SECTION : COLOR_LABEL, false);
                y += 9;
            }

            if (!p.nextChoices().isEmpty()) {
                StringBuilder next = new StringBuilder();
                for (String id : p.nextChoices()) {
                    Skill node = SkillTrees.clientById(id);
                    if (node == null) continue;
                    if (next.length() > 0) next.append(", ");
                    next.append(SkillTreeRenderHelper.resolveDisplayName(node.getDisplayName()))
                            .append(" (").append(node.getPointCost()).append(")");
                }
                for (var line : font.split(Component.translatable(B + "next", next.toString()), w - 4)) {
                    g.drawString(font, line, x + 4, y, COLOR_LABEL, false);
                    y += 9;
                }
            }
            y += 3;
        }
        return y + 4;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** Draws flow-layout chips; returns the y after the last row. */
    private int drawChips(@NonNull GuiGraphics g, @NonNull Font font,
                          int x, int y, int w, int maxH,
                          @NonNull List<String> labels) {
        if (labels.isEmpty()) {
            g.drawString(font, Component.translatable(S + "none"), x, y, COLOR_LABEL, false);
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
