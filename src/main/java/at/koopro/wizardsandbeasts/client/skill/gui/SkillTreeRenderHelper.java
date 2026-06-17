package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

public final class SkillTreeRenderHelper {

    private enum NodeVisualState {
        LOCKED,
        UNLOCKABLE,
        STARTED,
        MAXED
    }

    private SkillTreeRenderHelper() {
    }

    private static final Identifier PANEL_TEX = Identifier.fromNamespaceAndPath(
            at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "textures/gui/skill_tree/panel.png");

    public static void renderWindowFrame(GuiGraphics graphics, Font font, int panelX, int panelY, int panelW, int panelH, String title) {
        McStylePanel.drawTiled(graphics, PANEL_TEX, panelX, panelY, panelW, panelH, 64);
        McStylePanel.drawBorder(graphics, panelX, panelY, panelW, panelH, 0xFF6A5A90, 0xFF1A1626);

        graphics.drawCenteredString(font, title, panelX + panelW / 2, panelY + WizardsAndBeastsUiTokens.SkillTree.TITLE_Y, WizardsAndBeastsUiTokens.SkillTree.TITLE_COLOR);
    }

    public static void renderTabs(GuiGraphics graphics, Font font, int panelX, int panelY, SkillTreeId activeTree,
                                  List<SkillTreeScreen.TabBounds> tabs, int mouseX, int mouseY) {
        int pulse = (int) (System.currentTimeMillis() / 120L % 3L);
        for (SkillTreeScreen.TabBounds tab : tabs) {
            boolean active = tab.tree() == activeTree;
            boolean hover = tab.contains(mouseX, mouseY);

            int fill = active ? WizardsAndBeastsUiTokens.SkillTree.TAB_ACTIVE : WizardsAndBeastsUiTokens.SkillTree.TAB_INACTIVE;
            if (!active && hover) {
                fill = WizardsAndBeastsUiTokens.SkillTree.TAB_HOVER;
            }

            graphics.fill(tab.x(), tab.y(), tab.x() + tab.width(), tab.y() + tab.height(), fill);
            graphics.fill(tab.x() + 1, tab.y() + 1, tab.x() + tab.width() - 1, tab.y() + 3, WizardsAndBeastsUiTokens.SkillTree.TAB_TOP_HIGHLIGHT);
            if (hover && !active) {
                graphics.fill(tab.x() + 1, tab.y() + 1, tab.x() + tab.width() - 1, tab.y() + tab.height() - 1, WizardsAndBeastsUiTokens.SkillTree.TAB_HOVER_GLOW);
            }
            if (active && pulse == 1) {
                graphics.fill(tab.x() + 2, tab.y() + 2, tab.x() + tab.width() - 2, tab.y() + tab.height() - 2, WizardsAndBeastsUiTokens.SkillTree.TAB_ACTIVE_PULSE);
            }
            int border = active ? WizardsAndBeastsUiTokens.SkillTree.TAB_ACTIVE_BORDER : WizardsAndBeastsUiTokens.SkillTree.BORDER_COLOR;
            drawBorder(graphics, tab.x(), tab.y(), tab.width(), tab.height(), border);
            graphics.drawCenteredString(font, tab.tree().getDisplayName(), tab.x() + tab.width() / 2,
                    tab.y() + WizardsAndBeastsUiTokens.SkillTree.TAB_TEXT_Y, active ? WizardsAndBeastsUiTokens.SkillTree.TAB_TEXT_ACTIVE : WizardsAndBeastsUiTokens.SkillTree.TAB_TEXT);
        }
    }

    public static void renderViewportAndGraph(GuiGraphics graphics, int viewportX, int viewportY, int viewportW, int viewportH,
                                              List<Skill> skills, Map<String, SkillTreeScreen.NodeBounds> nodeBoundsById,
                                              SkillTreeScreen.NodeBounds hoveredNode, SkillTreeScreen.NodeBounds selectedNode,
                                              int panX, int panY, Map<String, Integer> levelsById, int availablePoints) {
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        graphics.fill(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH, WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_BG);

        renderGrid(graphics, viewportX, viewportY, viewportW, viewportH, panX, panY);
        renderConnectors(graphics, skills, nodeBoundsById, levelsById);

        for (Skill skill : skills) {
            SkillTreeScreen.NodeBounds node = nodeBoundsById.get(skill.getId());
            if (node == null) {
                continue;
            }

            int level = levelsById.getOrDefault(skill.getId(), 0);
            NodeVisualState visualState = getNodeVisualState(skill, level, availablePoints);
            boolean selected = node.equals(selectedNode);
            boolean hovered = node.equals(hoveredNode);

            renderSkillNodeTextures(graphics, skill, level, selected, hovered, node);

            int textColor = visualState == NodeVisualState.MAXED ? WizardsAndBeastsUiTokens.SkillTree.NODE_TEXT_MAXED : WizardsAndBeastsUiTokens.SkillTree.NODE_TEXT;
            graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                    Integer.toString(level), node.centerX(), node.y() + WizardsAndBeastsUiTokens.SkillTree.NODE_LEVEL_Y, textColor);
        }

        graphics.disableScissor();
        drawBorder(graphics, viewportX, viewportY, viewportW, viewportH, WizardsAndBeastsUiTokens.SkillTree.BORDER_COLOR);
    }

    public static void renderFooter(GuiGraphics graphics, Font font, int panelX, int panelY, int panelW, int panelH,
                                    int pointsLeft, int totalEarned, List<Skill> visibleSkills, PlayerSkillData data) {
        int footerY = panelY + panelH - WizardsAndBeastsUiTokens.SkillTree.FOOTER_HEIGHT;
        graphics.fill(panelX, footerY, panelX + panelW, panelY + panelH, WizardsAndBeastsUiTokens.SkillTree.HEADER_FILL);
        graphics.fill(panelX, footerY, panelX + panelW, footerY + 1, WizardsAndBeastsUiTokens.SkillTree.BORDER_COLOR);
        String pointsLabel = safeText(Component.translatable("screen.wizards_and_beasts.skill_tree.points").getString(), "SP");
        String earnedLabel = safeText(Component.translatable("screen.wizards_and_beasts.skill_tree.earned").getString(), "Earned");
        String spentLabel = safeText(Component.translatable("screen.wizards_and_beasts.skill_tree.spent").getString(), "Spent");
        String progressLabel = safeText(Component.translatable("screen.wizards_and_beasts.skill_tree.progress").getString(), "Progress");
        String hintLabel = Component.translatable("screen.wizards_and_beasts.skill_tree.hint").getString();
        int totalSpent = Math.max(0, totalEarned - pointsLeft);
        int unlockedInTree = 0;
        for (Skill skill : visibleSkills) {
            if (data.hasSkill(skill.getId())) {
                unlockedInTree++;
            }
        }
        int totalInTree = visibleSkills.size();
        String leftStatus = pointsLabel + ": " + pointsLeft
                + "  " + earnedLabel + ": " + totalEarned
                + "  " + spentLabel + ": " + totalSpent;
        String rightStatus = progressLabel + ": " + unlockedInTree + "/" + totalInTree
                + "  " + safeText(hintLabel, "LMB: Unlock");

        int rightX = panelX + panelW - WizardsAndBeastsUiTokens.SkillTree.FOOTER_RIGHT_PAD - font.width(rightStatus);
        int leftX = panelX + WizardsAndBeastsUiTokens.SkillTree.FOOTER_LEFT_X;
        int maxLeftWidth = rightX - leftX - WizardsAndBeastsUiTokens.SkillTree.FOOTER_MIN_GAP;
        String fittedLeft = fitText(font, leftStatus, Math.max(40, maxLeftWidth));

        if (maxLeftWidth < 80) {
            rightStatus = fitText(font, progressLabel + ": " + unlockedInTree + "/" + totalInTree,
                    panelW - WizardsAndBeastsUiTokens.SkillTree.FOOTER_LEFT_X - WizardsAndBeastsUiTokens.SkillTree.FOOTER_RIGHT_PAD);
            rightX = panelX + panelW - WizardsAndBeastsUiTokens.SkillTree.FOOTER_RIGHT_PAD - font.width(rightStatus);
        }

        graphics.drawString(font, fittedLeft, leftX,
                footerY + WizardsAndBeastsUiTokens.SkillTree.FOOTER_TEXT_Y, WizardsAndBeastsUiTokens.SkillTree.FOOTER_PRIMARY_COLOR, false);
        graphics.drawString(font, rightStatus, rightX,
                footerY + WizardsAndBeastsUiTokens.SkillTree.FOOTER_TEXT_Y, WizardsAndBeastsUiTokens.SkillTree.FOOTER_SECONDARY_COLOR, false);
    }

    public static void renderTooltipCard(GuiGraphics graphics, Font font, Skill skill, SkillTreeScreen.NodeBounds node, int mouseX, int mouseY,
                                         int level, int points, boolean prerequisitesMet) {
        int tooltipX = Math.min(mouseX + WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_OFFSET_X,
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() - WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_WIDTH - 8);
        int tooltipY = Math.min(mouseY + WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_OFFSET_Y,
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight() - WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_HEIGHT - 8);

        int w = WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_WIDTH;
        int h = WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_HEIGHT;
        graphics.fill(tooltipX, tooltipY, tooltipX + w, tooltipY + h, WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_BG);
        drawBorder(graphics, tooltipX, tooltipY, w, h, skill.getTree().getColor());

        graphics.drawString(font, skill.getDisplayName(), tooltipX + 8, tooltipY + 7, skill.getTree().getColor(), false);
        List<String> descLines = wrap(font, safeText(skill.getDescription(), "No description."), w - 16, 2);
        int descY = tooltipY + 21;
        for (String line : descLines) {
            graphics.drawString(font, line, tooltipX + 8, descY, WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_TEXT, false);
            descY += 10;
        }
        String levelText = Component.translatable("screen.wizards_and_beasts.skill_tree.level").getString();
        String costText = Component.translatable("screen.wizards_and_beasts.skill_tree.cost").getString();
        String prereqText = Component.translatable("screen.wizards_and_beasts.skill_tree.prereq").getString();
        String prereqMetText = Component.translatable("screen.wizards_and_beasts.skill_tree.prereq_met").getString();
        String prereqMissingText = Component.translatable("screen.wizards_and_beasts.skill_tree.prereq_missing").getString();
        int statsY = tooltipY + 45;
        graphics.drawString(font, safeText(levelText, "Level") + ": " + level + "/" + skill.getMaxLevel(),
                tooltipX + 8, statsY, WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_TEXT, false);
        graphics.drawString(font, safeText(costText, "Cost") + ": " + skill.getPointCost() + " SP",
                tooltipX + 8, statsY + 10, WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_TEXT, false);
        graphics.drawString(font, safeText(prereqText, "Prereq") + ": "
                        + (prerequisitesMet ? safeText(prereqMetText, "Met") : safeText(prereqMissingText, "Missing")),
                tooltipX + 8, statsY + 20,
                prerequisitesMet ? WizardsAndBeastsUiTokens.SkillTree.STATUS_OK : WizardsAndBeastsUiTokens.SkillTree.STATUS_WARN, false);

        boolean canUnlock = prerequisitesMet && points >= skill.getPointCost() && level < skill.getMaxLevel();
        boolean maxed = level >= skill.getMaxLevel();
        boolean unlocked = level > 0;
        int missingPoints = Math.max(0, skill.getPointCost() - points);
        String canUnlockText = Component.translatable("screen.wizards_and_beasts.skill_tree.click_unlock").getString();
        String cannotUnlockText = Component.translatable("screen.wizards_and_beasts.skill_tree.cannot_unlock").getString();
        String missingText = safeText(Component.translatable("screen.wizards_and_beasts.skill_tree.need_points").getString(), "Need");
        String unlockedText = safeText(Component.translatable("screen.wizards_and_beasts.skill_tree.unlocked").getString(), "Unlocked");
        String maxedText = safeText(Component.translatable("screen.wizards_and_beasts.skill_tree.maxed").getString(), "Maxed");
        String actionLine;
        if (maxed) {
            actionLine = maxedText;
        } else if (canUnlock) {
            actionLine = safeText(canUnlockText, "Click to unlock");
        } else if (unlocked) {
            actionLine = unlockedText;
        } else {
            actionLine = safeText(cannotUnlockText, "Cannot unlock yet")
                    + (missingPoints > 0 ? " (" + missingText + " " + missingPoints + " SP)" : "");
        }
        graphics.drawString(font, actionLine, tooltipX + 8, tooltipY + h - 14,
                canUnlock ? WizardsAndBeastsUiTokens.SkillTree.STATUS_OK : WizardsAndBeastsUiTokens.SkillTree.STATUS_WARN, false);
    }

    private static void renderGrid(GuiGraphics graphics, int viewportX, int viewportY, int viewportW, int viewportH, int panX, int panY) {
        int spacing = WizardsAndBeastsUiTokens.SkillTree.GRID_SPACING;
        int startX = Math.floorMod(panX, spacing);
        int startY = Math.floorMod(panY, spacing);
        for (int x = viewportX - startX; x < viewportX + viewportW; x += spacing) {
            graphics.fill(x, viewportY, x + 1, viewportY + viewportH, WizardsAndBeastsUiTokens.SkillTree.GRID_LINE);
        }
        for (int y = viewportY - startY; y < viewportY + viewportH; y += spacing) {
            graphics.fill(viewportX, y, viewportX + viewportW, y + 1, WizardsAndBeastsUiTokens.SkillTree.GRID_LINE);
        }
    }

    private static void renderConnectors(GuiGraphics graphics, List<Skill> skills, Map<String, SkillTreeScreen.NodeBounds> nodeBoundsById,
                                         Map<String, Integer> levelsById) {
        for (Skill skill : skills) {
            SkillTreeScreen.NodeBounds child = nodeBoundsById.get(skill.getId());
            if (child == null) {
                continue;
            }
            for (String prereqId : skill.getPrerequisites()) {
                SkillTreeScreen.NodeBounds parent = nodeBoundsById.get(prereqId);
                if (parent == null) {
                    continue;
                }
                int parentLevel = levelsById.getOrDefault(prereqId, 0);
                Skill parentSkill = parent.skill();
                int color = WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_LOCKED;
                int stroke = WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_LOCKED_STROKE;
                if (parentLevel > 0) {
                    if (parentLevel >= parentSkill.getMaxLevel()) {
                        color = WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_MAXED;
                    } else {
                        color = WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_STARTED;
                    }
                    stroke = WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_STROKE;
                }
                if (levelsById.getOrDefault(skill.getId(), 0) > 0) {
                    color = WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_UNLOCKED;
                    stroke = WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_STROKE;
                }
                drawLine(graphics, parent.centerX(), parent.centerY(), child.centerX(), child.centerY(), color, stroke);
                if (color != WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_LOCKED) {
                    drawFlowMark(graphics, parent.centerX(), parent.centerY(), child.centerX(), child.centerY());
                }
            }
        }
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int stroke) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1;
        int y = y1;
        int size = Math.max(1, stroke);
        while (true) {
            graphics.fill(x, y, x + size, y + size, color);
            if (x == x2 && y == y2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static NodeVisualState getNodeVisualState(Skill skill, int level, int availablePoints) {
        if (level >= skill.getMaxLevel()) {
            return NodeVisualState.MAXED;
        }
        if (level > 0) {
            return NodeVisualState.STARTED;
        }
        if (availablePoints >= skill.getPointCost()) {
            return NodeVisualState.UNLOCKABLE;
        }
        return NodeVisualState.LOCKED;
    }

    /**
     * Maxed: {@code skill_completed.png}, then {@code skill_{N}_completed.png} when {@code N >= 2}. Incomplete:
     * {@code skill.png}, {@code skill_{N}_uncompleted}, proportional left strip of {@code skill_{N}_completed}
     * when {@code 1 <= level < N}, then hover/selected.
     */
    private static void renderSkillNodeTextures(GuiGraphics graphics, Skill skill, int level, boolean selected, boolean hovered,
                                                SkillTreeScreen.NodeBounds node) {
        int max = skill.getMaxLevel();
        if (level >= max) {
            blitSkillNodeOpaque(graphics, SkillTreeGuiTextures.SKILL_NODE_COMPLETED, node);
            if (max >= 2) {
                blitSkillNodeOverlay(graphics, SkillTreeGuiTextures.rankCompletedOverlay(max), node);
            }
            if (hovered) {
                blitSkillNodeOverlay(graphics, SkillTreeGuiTextures.SKILL_NODE_HOVER, node);
            }
            if (selected) {
                blitSkillNodeOverlay(graphics, SkillTreeGuiTextures.SKILL_NODE_SELECTED, node);
            }
            return;
        }
        blitSkillNodeOpaque(graphics, SkillTreeGuiTextures.SKILL_NODE, node);
        if (max >= 2) {
            blitSkillNodeOverlay(graphics, SkillTreeGuiTextures.rankUncompletedOverlay(max), node);
            if (level >= 1) {
                blitRankPartialCompletedStrip(graphics, node, max, level);
            }
        }
        if (hovered) {
            blitSkillNodeOverlay(graphics, SkillTreeGuiTextures.SKILL_NODE_HOVER, node);
        }
        if (selected) {
            blitSkillNodeOverlay(graphics, SkillTreeGuiTextures.SKILL_NODE_SELECTED, node);
        }
    }

    /**
     * Draws the left {@code level/maxLevel} of {@code skill_{max}_completed.png} over the gray overlay so
     * the first {@code level} bars read filled (same horizontal cut for any {@code maxLevel >= 2}; align art in columns).
     */
    private static void blitRankPartialCompletedStrip(GuiGraphics graphics, SkillTreeScreen.NodeBounds node, int maxLevel, int level) {
        int tw = SkillTreeGuiTextures.NODE_TEXELS;
        int th = SkillTreeGuiTextures.NODE_TEXELS;
        int srcW = Math.max(1, (tw * level) / maxLevel);
        int destW = Math.max(1, (node.width() * level) / maxLevel);
        Identifier completed = SkillTreeGuiTextures.rankCompletedOverlay(maxLevel);
        graphics.blit(RenderPipelines.GUI_TEXTURED, completed,
                node.x(), node.y(),
                0.0F, 0.0F,
                destW, node.height(),
                srcW, th,
                tw, th);
    }

    private static void blitSkillNodeOpaque(GuiGraphics graphics, Identifier texture, SkillTreeScreen.NodeBounds node) {
        int tw = SkillTreeGuiTextures.NODE_TEXELS;
        int th = SkillTreeGuiTextures.NODE_TEXELS;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                node.x(), node.y(),
                0.0F, 0.0F,
                node.width(), node.height(),
                tw, th,
                tw, th);
    }

    private static void blitSkillNodeOverlay(GuiGraphics graphics, Identifier texture, SkillTreeScreen.NodeBounds node) {
        int tw = SkillTreeGuiTextures.NODE_TEXELS;
        int th = SkillTreeGuiTextures.NODE_TEXELS;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                node.x(), node.y(),
                0.0F, 0.0F,
                node.width(), node.height(),
                tw, th,
                tw, th);
    }

    private static void drawFlowMark(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        int mx = x1 + ((x2 - x1) * 3 / 4);
        int my = y1 + ((y2 - y1) * 3 / 4);
        int size = WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_FLOW_MARK_SIZE;
        graphics.fill(mx - size, my - size, mx + size, my + size, WizardsAndBeastsUiTokens.SkillTree.CONNECTOR_FLOW_MARK);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank() || value.startsWith("screen.")) {
            return fallback;
        }
        return value;
    }

    private static String fitText(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return end <= 0 ? "" : text.substring(0, end) + ellipsis;
    }

    private static List<String> wrap(Font font, String text, int maxWidth, int maxLines) {
        List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
                if (out.size() >= maxLines) {
                    break;
                }
            }
        }
        if (!line.isEmpty() && out.size() < maxLines) {
            out.add(line.toString());
        }
        if (out.size() == maxLines && words.length > 0) {
            String last = out.get(maxLines - 1);
            if (!last.endsWith("...")) {
                out.set(maxLines - 1, last + "...");
            }
        }
        return out;
    }
}
