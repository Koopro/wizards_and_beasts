package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

/**
 * Chrome for the {@link SkillTreeScreen}: window frame, footer, hover tooltip and display-name
 * resolution.
 *
 * <p>Every piece here is a sprite from the {@code star_chart} skin or the chart's own set. It used
 * to be {@code fill()} rectangles and a hand-rolled border, which is why a screen whose stars were
 * soft antialiased art sat inside hard flat boxes. The only thing this class draws directly is
 * text.
 */
public final class SkillTreeRenderHelper {

    private SkillTreeRenderHelper() {
    }

    /** The material the whole screen is cut from — night void, indigo frame, brass accent. */
    private static final String SKIN = McStylePanel.SKIN_STAR_CHART;

    /**
     * Resolves a node display name: lang keys (Phase 4 fillers, Polaris) translate; legacy
     * inline-English strings pass through unchanged (the fallback IS the literal).
     */
    public static String resolveDisplayName(String raw) {
        return I18n.exists(raw) ? I18n.get(raw) : raw;
    }

    /**
     * The window: a nine-sliced {@code star_chart} panel, a rule under the title, and a rivet in
     * each top corner.
     *
     * <p>This used to tile a flat {@code skill_tree/panel.png} and stroke a two-colour border with
     * four {@code fill}s. The skin it now wears was authored for exactly this screen by
     * {@code tools/gui_chrome.py} and had no consumer in Java at all.
     */
    public static void renderWindowFrame(GuiGraphics graphics, Font font, GuiScaleHelper.Layout layout,
                                         String title) {
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int panelW = layout.panelW();
        McStylePanel.drawSkinPanel(graphics, SKIN, panelX, panelY, panelW, layout.panelH());

        int pad = layout.s(WizardsAndBeastsUiTokens.SkillTree.CHROME_PAD);
        McStylePanel.drawSkinDivider(graphics, SKIN, panelX + pad,
                panelY + layout.s(WizardsAndBeastsUiTokens.SkillTree.HEADER_RULE_Y), panelW - pad * 2);

        int seal = layout.s(WizardsAndBeastsUiTokens.SkillTree.SEAL_INSET);
        McStylePanel.drawSkinSeal(graphics, SKIN, panelX + seal, panelY + seal,
                SkillTreeChartTextures.UNTINTED);
        McStylePanel.drawSkinSeal(graphics, SKIN,
                panelX + panelW - seal - McStylePanel.SEAL_SIZE, panelY + seal,
                SkillTreeChartTextures.UNTINTED);

        graphics.drawCenteredString(font, title, panelX + panelW / 2,
                panelY + layout.s(WizardsAndBeastsUiTokens.SkillTree.TITLE_Y),
                WizardsAndBeastsUiTokens.SkillTree.TITLE_COLOR);
    }

    /**
     * Earned / spent / cap counter over a recessed strip, with the campaign progress as a bar.
     *
     * <p>The bar is the one piece of new information: "Earned 21/60" is a number a player has to
     * read and divide, and the same fact as a filled track is read at a glance.
     */
    public static void renderFooter(GuiGraphics graphics, Font font, GuiScaleHelper.Layout layout,
                                    PlayerSkillData data) {
        int pad = layout.s(WizardsAndBeastsUiTokens.SkillTree.CHROME_PAD);
        int footerH = layout.s(WizardsAndBeastsUiTokens.SkillTree.FOOTER_HEIGHT);
        int footerY = layout.panelY() + layout.panelH() - footerH - pad;
        int footerX = layout.panelX() + pad;
        int footerW = layout.panelW() - pad * 2;

        McStylePanel.drawSkinInset(graphics, SKIN, footerX, footerY, footerW, footerH);

        int earned = data.getTotalPointsEarned();
        int unspent = data.getSkillPoints();
        int spent = Math.max(0, earned - unspent);
        String left = I18n.get("screen.wizards_and_beasts.skill_tree.footer", unspent, earned,
                SkillSystemAPI.MAX_SKILL_POINTS, spent);
        String right = I18n.get("screen.wizards_and_beasts.skill_tree.controls");

        // Text is never scaled — the font has one legible size, and the panel scale exists to fit
        // the chrome to the window, not to shrink prose out of readability.
        int textY = footerY + (footerH - font.lineHeight) / 2;
        int textX = footerX + WizardsAndBeastsUiTokens.SkillTree.FOOTER_TEXT_X;
        graphics.drawString(font, left, textX, textY, SkillTreeChartTextures.GOLD, false);

        int barW = WizardsAndBeastsUiTokens.SkillTree.POINTS_BAR_WIDTH;
        int barX = textX + font.width(left) + WizardsAndBeastsUiTokens.SkillTree.POINTS_BAR_GAP;
        int barH = WizardsAndBeastsUiTokens.SkillTree.POINTS_BAR_HEIGHT;
        int barY = footerY + (footerH - barH) / 2;
        int rightX = footerX + footerW - WizardsAndBeastsUiTokens.SkillTree.FOOTER_RIGHT_PAD
                - font.width(right);

        // The bar and the hint compete for the same run of footer; on a narrow window the hint
        // goes first, because the bar restates a number that is already on screen.
        if (barX + barW + WizardsAndBeastsUiTokens.SkillTree.FOOTER_MIN_GAP < rightX) {
            drawPointsBar(graphics, barX, barY, barW, barH, earned);
            graphics.drawString(font, right, rightX, textY,
                    SkillTreeChartTextures.NIGHT_TEXT_DIM, false);
        } else if (barX + barW < footerX + footerW - WizardsAndBeastsUiTokens.SkillTree.FOOTER_RIGHT_PAD) {
            drawPointsBar(graphics, barX, barY, barW, barH, earned);
        }
    }

    private static void drawPointsBar(GuiGraphics graphics, int x, int y, int w, int h, int earned) {
        int sprite = SkillTreeChartTextures.BAR_SPRITE_SIZE;
        McStylePanel.drawTintedTexture(graphics, SkillTreeChartTextures.BAR_TRACK, x, y, w, h,
                sprite, sprite, SkillTreeChartTextures.CHART_INK);
        int cap = Math.max(1, SkillSystemAPI.MAX_SKILL_POINTS);
        int filled = (int) Math.round(w * Math.min(1.0, earned / (double) cap));
        if (filled > 0) {
            McStylePanel.drawTintedTexture(graphics, SkillTreeChartTextures.BAR_FILL, x, y, filled, h,
                    sprite, sprite, SkillTreeChartTextures.GOLD);
        }
    }

    /** Placeholder key for the sealed-region tooltip line (flavor text authored later; raw fallback shown). */
    private static final String SEALED_TOOLTIP_KEY = "skilltree.region.sealed.tooltip";

    /**
     * Hover card on a nine-sliced {@code star_chart} panel, tinted to the node's own state.
     *
     * <p>Tinting the panel rather than stroking a coloured border around a flat fill is what lets
     * the whole card carry the state: gold once the node is started, the region's own colour
     * before that.
     */
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
        int accent = started ? SkillTreeChartTextures.GOLD : regionTint;

        // The panel art is night void with an indigo frame; a light tint would wash it out, so it
        // is only nudged toward the accent rather than painted with it.
        McStylePanel.drawSkinPanel(graphics, SKIN, tooltipX, tooltipY, w, h);
        // Inset by the panel's own 8px nine-slice border, so the rule sits in the card rather than
        // across its frame.
        McStylePanel.drawSkinDivider(graphics, SKIN, tooltipX + 8, tooltipY + 17, w - 16);

        graphics.drawString(font, resolveDisplayName(skill.getDisplayName()),
                tooltipX + 8, tooltipY + 7, accent, false);
        List<String> descLines = wrap(font, safeText(skill.getDescription(), "No description."), w - 16, 2);
        int descY = tooltipY + 24;
        for (String line : descLines) {
            graphics.drawString(font, line, tooltipX + 8, descY, 0xFFCED3E4, false);
            descY += 10;
        }

        int statsY = tooltipY + 48;
        graphics.drawString(font, "Level: " + level + "/" + skill.getMaxLevel(),
                tooltipX + 8, statsY, SkillTreeChartTextures.NIGHT_TEXT_DIM, false);
        graphics.drawString(font, "Cost: " + skill.getPointCost() + " SP",
                tooltipX + 8, statsY + 10, SkillTreeChartTextures.NIGHT_TEXT_DIM, false);

        String constellation = I18n.get("skilltree.region." + skill.getTree().getId() + ".constellation");
        boolean namedConstellation = !constellation.startsWith("skilltree.");
        McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.regionGlyph(skill.getTree()),
                tooltipX + 8 + SkillTreeChartTextures.REGION_GLYPH_SIZE / 2, statsY + 24,
                SkillTreeChartTextures.REGION_GLYPH_SIZE, SkillTreeChartTextures.withAlpha(regionTint, 220));
        graphics.drawString(font, skill.getTree().getDisplayName()
                        + (namedConstellation ? " (" + constellation + ")" : ""),
                tooltipX + 10 + SkillTreeChartTextures.REGION_GLYPH_SIZE, statsY + 20,
                SkillTreeChartTextures.withAlpha(regionTint, 220), false);

        boolean affordable = points >= skill.getPointCost();
        String actionLine;
        int actionColor;
        if (sealed) {
            // Sealed region: capability tag denies this whole region. Distinct from adjacency-locked
            // only in wording.
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
        graphics.drawString(font, actionLine, tooltipX + 8, tooltipY + h - 15, actionColor, false);
    }

    /**
     * Resolves a node's description for display.
     *
     * <p>This used to hand the raw field straight to the renderer, so a description could
     * only ever be literal English — untranslatable. It now goes through the same
     * {@link #resolveDisplayName} lookup the node's name does: a lang key is translated, and
     * anything with no matching key falls through unchanged. That keeps the nodes still
     * carrying literal prose working while new ones can be keyed.
     */
    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank() || value.startsWith("screen.")) {
            return fallback;
        }
        return resolveDisplayName(value);
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
