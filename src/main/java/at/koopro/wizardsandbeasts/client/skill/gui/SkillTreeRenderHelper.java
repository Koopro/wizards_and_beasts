package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillEffectSummary;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Chrome for the {@link SkillTreeScreen}: window frame, footer, hover tooltip and display-name
 * resolution.
 *
 * <p>Every piece here is a sprite from the {@code star_chart} skin or the chart's own set. It used
 * to be {@code fill()} rectangles and a hand-rolled border, which is why a screen whose stars were
 * soft antialiased art sat inside hard flat boxes. The only thing this class draws directly is
 * text, always in the page's ink and never with a shadow.
 */
public final class SkillTreeRenderHelper {

    private SkillTreeRenderHelper() {
    }

    /** The material the whole screen is cut from — blue-grey vellum, indigo ink, silver leaf. */
    private static final GuiSkin SKIN = GuiSkin.STAR_CHART;

    /**
     * Resolves a node display name: lang keys (Phase 4 fillers, Polaris) translate; legacy
     * inline-English strings pass through unchanged (the fallback IS the literal).
     */
    public static String resolveDisplayName(String raw) {
        return I18n.exists(raw) ? I18n.get(raw) : raw;
    }

    /**
     * Clearance from the panel's outer edge to anything drawn inside it. The frame's double ink
     * rule sits 4 and 6px in at native size, whatever the panel scale, so this is not scaled.
     */
    private static final int FRAME_CLEAR = 8;
    /** The divider sprite's height: its two lines fall 3 and 5px below the y it is drawn at. */
    private static final int RULE_SPRITE_H = 8;

    /**
     * The header rule's y: directly above the chart well, so the rule, the well and the vocation
     * button share one budget at every panel scale instead of three scaled tokens that collide.
     */
    public static int headerRuleY(GuiScaleHelper.Layout layout) {
        return layout.panelY() + layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_Y) - RULE_SPRITE_H;
    }

    /**
     * The header row's top: the vocation button sits on the rule, clear of the frame's rules.
     *
     * <p>It used to hang at {@code panelY + s(3)}, straight across the double ink rule, which a
     * dark leather frame hid and a paper page does not.
     */
    public static int headerRowY(GuiScaleHelper.Layout layout, int rowH) {
        return Math.max(layout.panelY() + FRAME_CLEAR, headerRuleY(layout) + 2 - rowH);
    }

    /**
     * The window: a nine-sliced {@code star_chart} panel, the title written on the sheet over a
     * rule, and a seal in each top corner.
     *
     * <p>This used to tile a flat {@code skill_tree/panel.png} and stroke a two-colour border with
     * four {@code fill}s. The title is written in the page's ink with no shadow: a drop shadow on
     * paper reads as a misprint.
     */
    public static void renderWindowFrame(GuiGraphics graphics, Font font, GuiScaleHelper.Layout layout,
                                         String title) {
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int panelW = layout.panelW();
        McStylePanel.drawSkinPanel(graphics, SKIN, panelX, panelY, panelW, layout.panelH());

        int pad = WizardsMetrics.SPACE_L;
        McStylePanel.drawSkinDivider(graphics, SKIN, panelX + pad, headerRuleY(layout), panelW - pad * 2);

        int seal = layout.s(WizardsAndBeastsUiTokens.SkillTree.SEAL_INSET);
        McStylePanel.drawSkinSeal(graphics, SKIN, panelX + seal, panelY + seal);
        McStylePanel.drawSkinSeal(graphics, SKIN,
                panelX + panelW - seal - McStylePanel.SEAL_SIZE, panelY + seal);

        // Centred on the vocation button's row, so the header reads as one line of type.
        int rowH = layout.s(WizardsAndBeastsUiTokens.SkillTree.VOCATION_BUTTON_H);
        int titleY = headerRowY(layout, rowH) + (rowH - font.lineHeight) / 2 + 1;
        graphics.drawString(font, title, panelX + panelW / 2 - font.width(title) / 2, titleY,
                SKIN.ink(), false);
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
        // Once a point is spent, "how do I undo this" is the more useful of the two hints, and the
        // drag/scroll hint has already done its job. Refund is not a stub and never was:
        // `/wandb skill respec` carries no permission gate and refunds pointCost * level for every
        // allocated node, so the only thing missing was a player ever being told it exists.
        String right = I18n.get(spent > 0
                ? "screen.wizards_and_beasts.skill_tree.respec_hint"
                : "screen.wizards_and_beasts.skill_tree.controls");

        // Text is never scaled — the font has one legible size, and the panel scale exists to fit
        // the chrome to the window, not to shrink prose out of readability.
        int textY = footerY + (footerH - font.lineHeight) / 2;
        int textX = footerX + WizardsAndBeastsUiTokens.SkillTree.FOOTER_TEXT_X;
        graphics.drawString(font, left, textX, textY, SKIN.ink(), false);

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
            graphics.drawString(font, right, rightX, textY, SKIN.muted(), false);
        } else if (barX + barW < footerX + footerW - WizardsAndBeastsUiTokens.SkillTree.FOOTER_RIGHT_PAD) {
            drawPointsBar(graphics, barX, barY, barW, barH, earned);
        }
    }

    /** A groove pressed into the vellum, filled with silver leaf. Both sprites carry their colour. */
    private static void drawPointsBar(GuiGraphics graphics, int x, int y, int w, int h, int earned) {
        int sprite = SkillTreeChartTextures.BAR_SPRITE_SIZE;
        McStylePanel.drawTintedTexture(graphics, SkillTreeChartTextures.BAR_TRACK, x, y, w, h,
                sprite, sprite, SkillTreeChartTextures.UNTINTED);
        int cap = Math.max(1, SkillSystemAPI.MAX_SKILL_POINTS);
        int filled = (int) Math.round(w * Math.min(1.0, earned / (double) cap));
        if (filled > 0) {
            McStylePanel.drawTintedTexture(graphics, SkillTreeChartTextures.BAR_FILL, x, y, filled, h,
                    sprite, sprite, SkillTreeChartTextures.UNTINTED);
        }
    }

    /** Placeholder key for the sealed-region tooltip line (flavor text authored later; raw fallback shown). */
    private static final String SEALED_TOOLTIP_KEY = "skilltree.region.sealed.tooltip";

    /**
     * Hover card on a nine-sliced {@code star_chart} panel, its title inked to the node's state:
     * full ink once the node is started, the region's own ink before that.
     */
    /**
     * The hover card.
     *
     * <p>It grew two things it was missing. First, <b>what the node actually does</b>: the card used
     * to show only the authored {@code description} prose, which is a separate field from the
     * {@code effects} the game executes, so a rebalance that edited the number and not the sentence
     * left the tooltip quietly lying. The effect lines come straight out of
     * {@link SkillEffectSummary}, which reads the same field the cast does. Second, <b>which
     * prerequisite is missing</b>: "allocate a connected star first" is true but unhelpful on a chart
     * of 163 nodes, so the neighbours that would open it are named.
     *
     * <p>Height is measured rather than fixed. The old card was a constant 110px for every node,
     * which is why nothing longer than two wrapped description lines could ever have been shown.
     */
    public static void renderTooltipCard(GuiGraphics graphics, Font font, Skill skill, int mouseX, int mouseY,
                                         int level, int points, boolean adjacencyOpen, boolean sealed,
                                         List<Component> prerequisites) {
        renderTooltipCard(graphics, font, skill, mouseX, mouseY, level, points, adjacencyOpen, sealed,
                prerequisites, List.of());
    }

    /**
     * The node card: what this is, what it means, what it does, what it needs and what it opens.
     *
     * <p>The five questions the brief asks a learning screen to answer, in the order a student asks them.
     * {@code leadsTo} is the last of them — "what can I learn next" — and is drawn only for a node the player
     * has not taken yet, because once it is theirs the web itself shows the way on.
     */
    public static void renderTooltipCard(GuiGraphics graphics, Font font, Skill skill, int mouseX, int mouseY,
                                         int level, int points, boolean adjacencyOpen, boolean sealed,
                                         List<Component> prerequisites, List<Component> leadsTo) {
        int w = WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_WIDTH;
        int inner = w - TOOLTIP_MARGIN * 2;

        boolean maxed = level >= skill.getMaxLevel();
        boolean started = level > 0;
        boolean affordable = points >= skill.getPointCost();
        int regionTint = SkillTreeChartTextures.regionTint(skill.getTree());
        int regionInk = SkillTreeChartTextures.regionTextInk(skill.getTree());
        int accent = started ? SKIN.ink() : regionInk;

        List<String> descLines = wrap(font, safeText(skill.getDescription(), "No description."), inner, 2);

        // What one more point buys, when there is one to buy; otherwise what the node is giving now.
        // Those are different numbers on a multi-level node, and that is the choice being made.
        int shownLevel = maxed ? skill.getMaxLevel() : Math.max(1, level + (started ? 1 : 0));
        List<Component> effects = SkillEffectSummary.lines(skill, shownLevel);

        // Lore, then the practical example. Both optional: a node with nothing in-world to say says nothing
        // rather than padding the card with a restatement of its own description.
        List<String> loreLines = skill.getLore().isEmpty()
                ? List.of() : wrap(font, I18n.get(skill.getLore()), inner, 3);
        List<String> practiceLines = skill.getPractice().isEmpty()
                ? List.of() : wrap(font, I18n.get("screen.wizards_and_beasts.skill_tree.practice",
                        I18n.get(skill.getPractice())), inner, 2);
        List<String> provenanceLines = wrap(font, provenanceText(skill), inner, 2);
        List<String> leadsToLines = List.of();
        if (!leadsTo.isEmpty() && level <= 0) {
            String joined = leadsTo.stream().map(Component::getString)
                    .collect(java.util.stream.Collectors.joining(", "));
            leadsToLines = wrap(font,
                    I18n.get("screen.wizards_and_beasts.skill_tree.leads_to", joined), inner, 2);
        }

        List<String> prereqLines = List.of();
        if (!sealed && !started && !adjacencyOpen && !prerequisites.isEmpty()) {
            String joined = prerequisites.stream().map(Component::getString)
                    .collect(java.util.stream.Collectors.joining(", "));
            prereqLines = wrap(font,
                    I18n.get("screen.wizards_and_beasts.skill_tree.requires_any", joined), inner, 2);
        }

        int h = TOOLTIP_PAD_TOP
                + descLines.size() * LINE
                + (loreLines.isEmpty() ? 0 : loreLines.size() * LINE + 2)
                + STATS_BLOCK
                + (effects.isEmpty() ? 0 : LINE + effects.size() * LINE)
                + (practiceLines.isEmpty() ? 0 : practiceLines.size() * LINE + 2)
                + provenanceLines.size() * LINE
                + leadsToLines.size() * LINE
                + prereqLines.size() * LINE
                + TOOLTIP_PAD_BOTTOM;

        int tooltipX = Math.min(mouseX + WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_OFFSET_X,
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() - w - 8);
        int tooltipY = Math.min(mouseY + WizardsAndBeastsUiTokens.SkillTree.TOOLTIP_OFFSET_Y,
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight() - h - 8);
        tooltipY = Math.max(4, tooltipY);

        McStylePanel.drawSkinPanel(graphics, SKIN, tooltipX, tooltipY, w, h);
        // Inside the frame's double ink rule, so the title rule sits on the card rather than
        // across its border.
        McStylePanel.drawSkinDivider(graphics, SKIN, tooltipX + TOOLTIP_MARGIN, tooltipY + 17, inner);

        int textX = tooltipX + TOOLTIP_MARGIN;
        graphics.drawString(font, resolveDisplayName(skill.getDisplayName()), textX, tooltipY + 10, accent, false);

        int y = tooltipY + TOOLTIP_PAD_TOP;
        for (String line : descLines) {
            graphics.drawString(font, line, textX, y, SKIN.ink(), false);
            y += LINE;
        }
        if (!loreLines.isEmpty()) {
            for (String line : loreLines) {
                graphics.drawString(font, Component.literal(line).withStyle(style -> style.withItalic(true)),
                        textX, y, LORE_TEXT, false);
                y += LINE;
            }
            y += 2;
        }

        graphics.drawString(font, "Level: " + level + "/" + skill.getMaxLevel(),
                textX, y, SKIN.muted(), false);
        graphics.drawString(font, "Cost: " + skill.getPointCost() + " SP",
                textX, y + LINE, SKIN.muted(), false);

        String constellation = I18n.get("skilltree.region." + skill.getTree().getId() + ".constellation");
        boolean namedConstellation = !constellation.startsWith("skilltree.");
        McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.regionGlyph(skill.getTree()),
                textX + SkillTreeChartTextures.REGION_GLYPH_SIZE / 2, y + LINE * 2 + 4,
                SkillTreeChartTextures.REGION_GLYPH_SIZE, SkillTreeChartTextures.withAlpha(regionTint, 220));
        graphics.drawString(font, skill.getTree().getDisplayName()
                        + (namedConstellation ? " (" + constellation + ")" : ""),
                textX + 2 + SkillTreeChartTextures.REGION_GLYPH_SIZE, y + LINE * 2, regionInk, false);
        y += STATS_BLOCK;

        if (!effects.isEmpty()) {
            String header = maxed || !started
                    ? I18n.get("screen.wizards_and_beasts.skill_tree.effects")
                    : I18n.get("screen.wizards_and_beasts.skill_tree.next_level");
            graphics.drawString(font, header, textX, y, WizardsPalette.PAGE_RUBRIC, false);
            y += LINE;
            for (Component effect : effects) {
                graphics.drawString(font, effect, textX + 4, y, EFFECT_TEXT, false);
                y += LINE;
            }
        }

        if (!practiceLines.isEmpty()) {
            for (String line : practiceLines) {
                graphics.drawString(font, line, textX, y, PRACTICE_TEXT, false);
                y += LINE;
            }
            y += 2;
        }

        // Whether this is an attested piece of magic or a training step of the mod's own devising. Printed on
        // every card, because a player cannot tell by looking and should never have to guess.
        for (String line : provenanceLines) {
            graphics.drawString(font, line, textX, y, PROVENANCE_TEXT, false);
            y += LINE;
        }

        for (String line : leadsToLines) {
            graphics.drawString(font, line, textX, y, SKIN.muted(), false);
            y += LINE;
        }

        for (String line : prereqLines) {
            graphics.drawString(font, line, textX, y, WizardsPalette.PAGE_BAD, false);
            y += LINE;
        }

        String actionLine;
        int actionColor;
        if (sealed) {
            // Sealed region: capability tag denies this whole region. Distinct from adjacency-locked
            // only in wording.
            actionLine = I18n.exists(SEALED_TOOLTIP_KEY) ? I18n.get(SEALED_TOOLTIP_KEY) : "Sealed";
            actionColor = WizardsPalette.PAGE_BAD;
        } else if (maxed) {
            actionLine = I18n.get("screen.wizards_and_beasts.skill_tree.maxed");
            actionColor = SKIN.muted();
        } else if (started || adjacencyOpen) {
            if (affordable) {
                actionLine = I18n.get(started
                        ? "screen.wizards_and_beasts.skill_tree.level_up"
                        : "screen.wizards_and_beasts.skill_tree.allocate");
                actionColor = WizardsPalette.PAGE_GOOD;
            } else {
                actionLine = I18n.get("screen.wizards_and_beasts.skill_tree.need_points",
                        skill.getPointCost() - points);
                actionColor = WizardsPalette.PAGE_BAD;
            }
        } else {
            actionLine = I18n.get("screen.wizards_and_beasts.skill_tree.locked");
            actionColor = WizardsPalette.PAGE_BAD;
        }
        graphics.drawString(font, actionLine, textX, tooltipY + h - 18, actionColor, false);
    }

    /** Line height for every stacked text row in the card. */
    /** In-world voice: the thin ink and italic, so it never competes with what the node does. */
    private static final int LORE_TEXT = SKIN.muted();
    /** The worked example. */
    private static final int PRACTICE_TEXT = SKIN.muted();
    /** Canon attestation or an honest "this mod invented it". */
    private static final int PROVENANCE_TEXT = SKIN.muted();

    /**
     * One line saying where this node's content comes from.
     *
     * <p>A node declares its own provenance; nothing is inferred at render time, because the client cannot
     * reliably read the spell registry and a tooltip that guessed would be the exact failure this line exists
     * to prevent. {@code SkillNodeProvenanceTest} is what keeps a declaration honest against the spell it
     * teaches.
     */
    private static String provenanceText(Skill skill) {
        return skill.getProvenance()
                .map(provenance -> provenance.isCanon()
                        ? I18n.get("screen.wizards_and_beasts.skill_tree.canon",
                                provenance.citation().orElse(""))
                        : I18n.get("screen.wizards_and_beasts.skill_tree.mod_advancement"))
                .orElseGet(() -> I18n.get("screen.wizards_and_beasts.skill_tree.provenance_unstated"));
    }

    private static final int LINE = 10;
    /** Frame clearance: the panel's double ink rule sits 4 and 6px in. */
    private static final int TOOLTIP_MARGIN = WizardsMetrics.SPACE_L;
    /** Title, rule and the gap before the first description line. */
    private static final int TOOLTIP_PAD_TOP = 28;
    /** Level, cost and the region row. */
    private static final int STATS_BLOCK = LINE * 3 + 2;
    /** Room for the action line plus the panel bottom border and its rules. */
    private static final int TOOLTIP_PAD_BOTTOM = 22;
    /** Effect lines: full ink over the thin-ink stat rows, because they are the reason to buy the node. */
    private static final int EFFECT_TEXT = SKIN.ink();

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
