package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Drawing helpers for the {@link SkillTreeScreen} star chart: window frame, gold-on-night footer,
 * hover tooltip, display-name resolution (translate-with-fallback), and the shape primitives.
 */
public final class SkillTreeRenderHelper {

    private SkillTreeRenderHelper() {
    }

    private static final Identifier PANEL_TEX = Identifier.fromNamespaceAndPath(
            at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "textures/gui/skill_tree/panel.png");

    /**
     * Resolves a node display name: lang keys (Phase 4 fillers, Polaris) translate; legacy
     * inline-English strings pass through unchanged (the fallback IS the literal).
     */
    public static String resolveDisplayName(String raw) {
        return I18n.exists(raw) ? I18n.get(raw) : raw;
    }

    public static void renderWindowFrame(GuiGraphics graphics, Font font, int panelX, int panelY, int panelW, int panelH, String title) {
        McStylePanel.drawTiled(graphics, PANEL_TEX, panelX, panelY, panelW, panelH, 64);
        McStylePanel.drawBorder(graphics, panelX, panelY, panelW, panelH, 0xFF6A5A90, 0xFF1A1626);

        graphics.drawCenteredString(font, title, panelX + panelW / 2,
                panelY + WizardsAndBeastsUiTokens.SkillTree.TITLE_Y, WizardsAndBeastsUiTokens.SkillTree.TITLE_COLOR);
    }

    /** Earned / spent / cap counter, gold-on-night to match the chart plate. */
    public static void renderFooter(GuiGraphics graphics, Font font, int panelX, int panelY, int panelW, int panelH,
                                    PlayerSkillData data) {
        int footerY = panelY + panelH - WizardsAndBeastsUiTokens.SkillTree.FOOTER_HEIGHT;
        graphics.fill(panelX, footerY, panelX + panelW, panelY + panelH, SkillTreeChartTextures.NIGHT_BG);
        graphics.fill(panelX, footerY, panelX + panelW, footerY + 1,
                SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.GOLD, 110));

        int earned = data.getTotalPointsEarned();
        int unspent = data.getSkillPoints();
        int spent = Math.max(0, earned - unspent);
        String left = "Points: " + unspent + " unspent"
                + "  •  Earned: " + earned + "/" + SkillSystemAPI.MAX_SKILL_POINTS
                + "  •  Spent: " + spent;
        String right = "Drag: pan  Scroll: zoom  Click: allocate";

        int textY = footerY + WizardsAndBeastsUiTokens.SkillTree.FOOTER_TEXT_Y;
        graphics.drawString(font, left, panelX + WizardsAndBeastsUiTokens.SkillTree.FOOTER_LEFT_X, textY,
                SkillTreeChartTextures.GOLD, false);
        int rightX = panelX + panelW - WizardsAndBeastsUiTokens.SkillTree.FOOTER_RIGHT_PAD - font.width(right);
        if (rightX > panelX + WizardsAndBeastsUiTokens.SkillTree.FOOTER_LEFT_X + font.width(left) + WizardsAndBeastsUiTokens.SkillTree.FOOTER_MIN_GAP) {
            graphics.drawString(font, right, rightX, textY, SkillTreeChartTextures.NIGHT_TEXT_DIM, false);
        }
    }

    /** Placeholder key for the sealed-region tooltip line (flavor text authored later; raw fallback shown). */
    private static final String SEALED_TOOLTIP_KEY = "skilltree.region.sealed.tooltip";

    /** Hover card in the plate aesthetic; the title resolves lang-key names with literal fallback. */
    public static void renderTooltipCard(GuiGraphics graphics, Font font, Skill skill, int mouseX, int mouseY,
                                         int level, int points, boolean adjacencyOpen, boolean sealed) {
        int w = WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_WIDTH;
        int h = WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_HEIGHT;
        int tooltipX = Math.min(mouseX + WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_OFFSET_X,
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() - w - 8);
        int tooltipY = Math.min(mouseY + WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_OFFSET_Y,
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight() - h - 8);

        int regionTint = SkillTreeChartTextures.regionTint(skill.getTree());
        boolean maxed = level >= skill.getMaxLevel();
        boolean started = level > 0;
        int borderTint = started ? SkillTreeChartTextures.GOLD : regionTint;

        graphics.fill(tooltipX, tooltipY, tooltipX + w, tooltipY + h, SkillTreeChartTextures.NIGHT_BG);
        drawBorderRect(graphics, tooltipX, tooltipY, w, h, borderTint);

        graphics.drawString(font, resolveDisplayName(skill.getDisplayName()),
                tooltipX + 8, tooltipY + 7, borderTint, false);
        List<String> descLines = wrap(font, safeText(skill.getDescription(), "No description."), w - 16, 2);
        int descY = tooltipY + 21;
        for (String line : descLines) {
            graphics.drawString(font, line, tooltipX + 8, descY, 0xFFCED3E4, false);
            descY += 10;
        }

        int statsY = tooltipY + 45;
        graphics.drawString(font, "Level: " + level + "/" + skill.getMaxLevel(),
                tooltipX + 8, statsY, SkillTreeChartTextures.NIGHT_TEXT_DIM, false);
        graphics.drawString(font, "Cost: " + skill.getPointCost() + " SP",
                tooltipX + 8, statsY + 10, SkillTreeChartTextures.NIGHT_TEXT_DIM, false);
        String constellation = I18n.get("skilltree.region." + skill.getTree().getId() + ".constellation");
        graphics.drawString(font, "Region: " + skill.getTree().getDisplayName()
                        + (constellation.startsWith("skilltree.") ? "" : " (" + constellation + ")"),
                tooltipX + 8, statsY + 20, SkillTreeChartTextures.withAlpha(regionTint, 220), false);

        boolean affordable = points >= skill.getPointCost();
        String actionLine;
        int actionColor;
        if (sealed) {
            // Sealed region: capability tag denies this whole region. Distinct from adjacency-locked only
            // in wording; the flavor text is a placeholder lang key (raw-key fallback if unauthored).
            actionLine = I18n.exists(SEALED_TOOLTIP_KEY) ? I18n.get(SEALED_TOOLTIP_KEY) : "Sealed";
            actionColor = WizardsAndBeastsUiTokens.SkillTree.STATUS_WARN;
        } else if (maxed) {
            actionLine = "Maxed";
            actionColor = SkillTreeChartTextures.GOLD;
        } else if (started || adjacencyOpen) {
            if (affordable) {
                actionLine = started ? "Click to level up" : "Click to allocate";
                actionColor = SkillTreeChartTextures.GOLD;
            } else {
                actionLine = "Need " + (skill.getPointCost() - points) + " more SP";
                actionColor = WizardsAndBeastsUiTokens.SkillTree.STATUS_WARN;
            }
        } else {
            actionLine = "Locked — allocate a connected star first";
            actionColor = WizardsAndBeastsUiTokens.SkillTree.STATUS_WARN;
        }
        graphics.drawString(font, actionLine, tooltipX + 8, tooltipY + h - 14, actionColor, false);
    }

    // ── Shape primitives ──

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

    /** One-pixel midpoint-circle outline — the survey rings. */
    public static void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        int x = radius;
        int y = 0;
        int err = 1 - radius;
        while (x >= y) {
            plot8(graphics, cx, cy, x, y, color);
            y++;
            if (err < 0) {
                err += 2 * y + 1;
            } else {
                x--;
                err += 2 * (y - x) + 1;
            }
        }
    }

    private static void plot8(GuiGraphics graphics, int cx, int cy, int x, int y, int color) {
        graphics.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
        graphics.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
        graphics.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
        graphics.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
        graphics.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
        graphics.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
        graphics.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
        graphics.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);
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
