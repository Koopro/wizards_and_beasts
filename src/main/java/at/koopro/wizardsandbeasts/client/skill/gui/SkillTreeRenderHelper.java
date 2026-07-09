package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Drawing helpers for the {@link SkillTreeScreen} web canvas: window frame, footer counter,
 * hover tooltip, and the plain-shape primitives (line, circle) the Phase 2 canvas uses.
 */
public final class SkillTreeRenderHelper {

    private SkillTreeRenderHelper() {
    }

    private static final Identifier PANEL_TEX = Identifier.fromNamespaceAndPath(
            at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "textures/gui/skill_tree/panel.png");

    public static void renderWindowFrame(GuiGraphics graphics, Font font, int panelX, int panelY, int panelW, int panelH, String title) {
        McStylePanel.drawTiled(graphics, PANEL_TEX, panelX, panelY, panelW, panelH, 64);
        McStylePanel.drawBorder(graphics, panelX, panelY, panelW, panelH, 0xFF6A5A90, 0xFF1A1626);

        graphics.drawCenteredString(font, title, panelX + panelW / 2,
                panelY + WizardsAndBeastsUiTokens.SkillTree.TITLE_Y, WizardsAndBeastsUiTokens.SkillTree.TITLE_COLOR);
    }

    /** Earned / spent / cap counter — the web's core budget readout. */
    public static void renderFooter(GuiGraphics graphics, Font font, int panelX, int panelY, int panelW, int panelH,
                                    PlayerSkillData data) {
        int footerY = panelY + panelH - WizardsAndBeastsUiTokens.SkillTree.FOOTER_HEIGHT;
        graphics.fill(panelX, footerY, panelX + panelW, panelY + panelH, WizardsAndBeastsUiTokens.SkillTree.HEADER_FILL);
        graphics.fill(panelX, footerY, panelX + panelW, footerY + 1, WizardsAndBeastsUiTokens.SkillTree.BORDER_COLOR);

        int earned = data.getTotalPointsEarned();
        int unspent = data.getSkillPoints();
        int spent = Math.max(0, earned - unspent);
        String left = "Points: " + unspent + " unspent"
                + "  •  Earned: " + earned + "/" + SkillSystemAPI.MAX_SKILL_POINTS
                + "  •  Spent: " + spent;
        String right = "Drag: pan  Scroll: zoom  Click: allocate";

        int textY = footerY + WizardsAndBeastsUiTokens.SkillTree.FOOTER_TEXT_Y;
        graphics.drawString(font, left, panelX + WizardsAndBeastsUiTokens.SkillTree.FOOTER_LEFT_X, textY,
                WizardsAndBeastsUiTokens.SkillTree.FOOTER_PRIMARY_COLOR, false);
        int rightX = panelX + panelW - WizardsAndBeastsUiTokens.SkillTree.FOOTER_RIGHT_PAD - font.width(right);
        if (rightX > panelX + WizardsAndBeastsUiTokens.SkillTree.FOOTER_LEFT_X + font.width(left) + WizardsAndBeastsUiTokens.SkillTree.FOOTER_MIN_GAP) {
            graphics.drawString(font, right, rightX, textY, WizardsAndBeastsUiTokens.SkillTree.FOOTER_SECONDARY_COLOR, false);
        }
    }

    /** Hover card reusing the node's existing display info; the prereq line is now an adjacency line. */
    public static void renderTooltipCard(GuiGraphics graphics, Font font, Skill skill, int mouseX, int mouseY,
                                         int level, int points, boolean adjacencyOpen) {
        int w = WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_WIDTH;
        int h = WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_HEIGHT;
        int tooltipX = Math.min(mouseX + WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_OFFSET_X,
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() - w - 8);
        int tooltipY = Math.min(mouseY + WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_OFFSET_Y,
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight() - h - 8);

        graphics.fill(tooltipX, tooltipY, tooltipX + w, tooltipY + h, WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_BG);
        drawBorderRect(graphics, tooltipX, tooltipY, w, h, skill.getTree().getColor());

        graphics.drawString(font, skill.getDisplayName(), tooltipX + 8, tooltipY + 7, skill.getTree().getColor(), false);
        List<String> descLines = wrap(font, safeText(skill.getDescription(), "No description."), w - 16, 2);
        int descY = tooltipY + 21;
        for (String line : descLines) {
            graphics.drawString(font, line, tooltipX + 8, descY, WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_TEXT, false);
            descY += 10;
        }

        int statsY = tooltipY + 45;
        graphics.drawString(font, "Level: " + level + "/" + skill.getMaxLevel(),
                tooltipX + 8, statsY, WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_TEXT, false);
        graphics.drawString(font, "Cost: " + skill.getPointCost() + " SP",
                tooltipX + 8, statsY + 10, WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_TEXT, false);
        String regionLine = "Region: " + skill.getTree().getDisplayName();
        graphics.drawString(font, regionLine, tooltipX + 8, statsY + 20,
                WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_TEXT, false);

        boolean maxed = level >= skill.getMaxLevel();
        boolean started = level > 0;
        boolean affordable = points >= skill.getPointCost();
        String actionLine;
        int actionColor;
        if (maxed) {
            actionLine = "Maxed";
            actionColor = WizardsAndBeastsUiTokens.SkillTree.STATUS_OK;
        } else if (started || adjacencyOpen) {
            if (affordable) {
                actionLine = started ? "Click to level up" : "Click to allocate";
                actionColor = WizardsAndBeastsUiTokens.SkillTree.STATUS_OK;
            } else {
                actionLine = "Need " + (skill.getPointCost() - points) + " more SP";
                actionColor = WizardsAndBeastsUiTokens.SkillTree.STATUS_WARN;
            }
        } else {
            actionLine = "Locked — allocate a connected node first";
            actionColor = WizardsAndBeastsUiTokens.SkillTree.STATUS_WARN;
        }
        graphics.drawString(font, actionLine, tooltipX + 8, tooltipY + h - 14, actionColor, false);
    }

    // ── Plain-shape primitives ──

    public static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int stroke) {
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

    /** Filled circle via horizontal scanline spans. */
    public static void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.floor(Math.sqrt((double) radius * radius - (double) dy * dy));
            graphics.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    public static void drawBorderRect(GuiGraphics graphics, int x, int y, int w, int h, int color) {
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
