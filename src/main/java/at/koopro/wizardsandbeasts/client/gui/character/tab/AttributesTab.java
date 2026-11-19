package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.gui.character.StatMeasureChart;
import at.koopro.wizardsandbeasts.client.gui.character.StatStyle;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.character.StatIcons;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.stats.ClientStatLevelUps;
import at.koopro.wizardsandbeasts.client.stats.ClientStatsState;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import at.koopro.wizardsandbeasts.stats.PowerBandTable;
import at.koopro.wizardsandbeasts.stats.StatReadout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Attributes page: the five stats with what each is doing, then the attributes everything else adds up to. */
public final class AttributesTab implements CharacterTab {

    private static final String KEY = "gui.wizards_and_beasts.character_sheet.";

    private static final int COLOR_SECTION  = WizardsPalette.PAGE_RUBRIC;
    private static final int COLOR_LABEL    = WizardsPalette.PAGE_INK_2;
    private static final int COLOR_VALUE    = WizardsPalette.PAGE_INK;
    /** Empty-bar track. Was {@code 0xFF0D0905}, near-black on the tile these rows replaced — an
     *  attribute sitting at zero looked like a row with no bar at all rather than an empty one,
     *  which is why Armor / Wand Affinity / Beast Resistance read as unfinished. */
    private static final int COLOR_BAR_TRACK = WizardsPalette.PAGE_SHADE;
    private static final int COLOR_BAR_FILL  = WizardsPalette.GILT_DARK;
    private static final int COLOR_PRODIGY   = WizardsPalette.PAGE_RUBRIC;
    /** The heritage ceiling, marked across the Power spoke of the Measure. */
    private static final int COLOR_CAP_TICK   = WizardsPalette.PAGE_INK_2;
    /** Effect-line tones. Off pure red/green to match the effects column: parchment ink, not an LED. */
    private static final int COLOR_EFFECT_GOOD = WizardsPalette.PAGE_GOOD;
    private static final int COLOR_EFFECT_BAD  = WizardsPalette.PAGE_BAD;
    /** Clear space kept between a truncated label and its right-aligned value. */
    private static final int LABEL_VALUE_GAP = 4;

    /** One attribute row: label line plus its bar. */
    private static final int ATTR_ROW_H = 12;

    /** A stat line's wash under the pointer: a tint of the page in the stat's ink, never a coloured block. */
    private static final int LINE_WASH_HOVER = 0x2C;
    /** Space the loading line takes before the first sync: about the chart's height, so the page does not jump. */
    private static final int MEASURE_LOADING_H = 90;

    private float scrollOffset = 0f; // pixels scrolled from top
    private int lastTotalH = 0;      // content height measured last frame

    /** The card the screen should draw this frame, in screen space. See {@link #consumeTooltip()}. */
    private @Nullable List<Component> pendingTooltip;

    /**
     * Last stat card built, and what it was built from.
     *
     * <p>A card is a dozen {@link Component}s and, for a trainable stat, a bounded replay of the
     * training accumulator to work out how many events the next point is away. That is cheap once and
     * wasteful sixty times a second for as long as the pointer sits still — which is exactly how long
     * someone reads a tooltip. Rebuilt only when the stat under the cursor or one of its numbers
     * actually changes.
     */
    private @Nullable PlayerStat cachedTooltipStat;
    private int cachedTooltipValue = -1;
    private int cachedTooltipProgress = -1;
    private int cachedTooltipCap = -1;
    private boolean cachedTooltipProdigy;
    private @Nullable List<Component> cachedTooltip;

    /** The visible content rect, so a hover test can ignore a row scrolled under the scissor. */
    private int viewX, viewY, viewW, viewH;

    @Override
    public @NonNull String translationKey() {
        return KEY + "tab.attributes";
    }

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h,
                       int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof LocalPlayer player)) return;
        Font font = mc.font;

        pendingTooltip = null;
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;

        float maxScroll = Math.max(0, lastTotalH - h);
        scrollOffset = Mth.clamp(scrollOffset, 0f, maxScroll);

        g.enableScissor(x, y, x + w, y + h);

        int cx = x + 2;
        int cy = y + 2 - (int) scrollOffset;
        int top = cy;
        // The scrollbar is drawn over the right edge of this rect, so content is laid out inside
        // a width that already excludes it. Right-aligned values used to run underneath it.
        int innerW = w - 4 - TabScrollbar.WIDTH;

        // ── The character's own five numbers ──────────────────────────────
        // Above the attributes on purpose: these are the character's own numbers, where the block
        // below is the sum of everything currently modifying them.
        if (ModuleManager.isEnabled(Module.PLAYER_STATS)) {
            if (ClientStatsState.hasData()) {
                cy = drawStatsSection(g, font, cx, cy, innerW, mouseX, mouseY);
            } else {
                // Before the first sync the five stats used to be simply absent, and the page read as if the
                // character had none. Say it is on its way instead, in the space the section will take.
                Ui.loadingState(g, font, UiSurface.PAGE, new Ui.Rect(cx, cy, innerW, MEASURE_LOADING_H),
                        Component.translatable(KEY + "stats.loading"));
                cy += MEASURE_LOADING_H + 4;
            }
        }

        // ── Attributes: the sum of everything currently modifying the character ──
        section(g, font, cx, cy, "attributes");
        cy += 10;

        drawAttributeRows(g, player, cx, cy, innerW, mouseX, mouseY);
        cy += 6 * ATTR_ROW_H + 2;
        // The wand and the vault used to follow here. They moved to the Wand page and the Identity page
        // (2026-10-15): this page is what the character is made of, not what they carry.

        g.disableScissor();

        lastTotalH = cy - top + 4;

        TabScrollbar.draw(g, x, y, w, h, scrollOffset, lastTotalH);
    }

    @Override
    public @Nullable List<Component> consumeTooltip() {
        List<Component> tooltip = pendingTooltip;
        pendingTooltip = null;
        return tooltip;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float) (delta * 10.0);
        return true;
    }

    // ── private helpers ───────────────────────────────────────────────────

    /**
     * Paints the five {@link PlayerStat} values and returns the new content cursor.
     *
     * <p>The page answers four questions in the order a player asks them: what is this, how big is it, what is it
     * doing for me (the Measure and its lines), and how close am I to the next point (the hover card).
     *
     * <p>The stat <em>names</em> and every effect line go through {@link StatReadout}, which reads the
     * same {@link at.koopro.wizardsandbeasts.stats.StatEffects} curves the cast pipeline spends. No
     * percentage on this screen is worked out here.
     */
    private int drawStatsSection(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                                 int mouseX, int mouseY) {
        PlayerStatsData stats = ClientStatsState.get();
        if (stats == null) return y;

        section(g, font, x, y, "stats");
        y += 10;

        int powerCap = powerCap();

        // "The Measure" (owner's pick, 2026-11-18): the five stats as an inked pentagon, then one line each for what
        // the stat does. Replaced the five stacked stat rows.
        return drawMeasure(g, font, x, y, w, stats, powerCap, mouseX, mouseY);
    }

    /**
     * The stats as "The Measure": {@link StatMeasureChart} with a glyph and value at each tip (value outboard, so
     * nothing crosses the chart), then one line per stat in its {@link StatStyle} ink with its effect on the right.
     * Hovering a tip or a line opens the full stat card, which carries the training progress and heritage ceiling.
     */
    private int drawMeasure(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                            @NonNull PlayerStatsData stats, int powerCap, int mouseX, int mouseY) {
        PlayerStat[] all = PlayerStat.values();
        int n = all.length;
        int radius = Math.max(16, Math.min(30, (w - 2 * (StatIcons.SIZE + 22)) / 2));
        int tipGap = 11;
        int cx = x + w / 2;
        int cy = y + StatIcons.SIZE / 2 + tipGap + radius + 2;
        float[] fractions = new float[n];
        int[] inks = new int[n];
        for (int i = 0; i < n; i++) {
            fractions[i] = stats.get(all[i]) / (float) PlayerStatsData.MAX_VALUE;
            inks[i] = StatStyle.ink(all[i]);
        }
        StatMeasureChart.draw(g, cx, cy, radius, fractions, inks);
        // Where heritage stops Power, marked across its spoke: a shape that stalls short of the ring otherwise looks
        // like a bug. Power is spoke 0, straight up, so the mark is a short horizontal rule.
        for (int i = 0; i < n; i++) {
            if (all[i].isHeritageCapped() && powerCap < PlayerStatsData.MAX_VALUE) {
                int[] at = StatMeasureChart.point(cx, cy, radius, i, n, powerCap / (float) PlayerStatsData.MAX_VALUE);
                g.fill(at[0] - 2, at[1], at[0] + 3, at[1] + 1, COLOR_CAP_TICK);
            }
        }
        boolean prodigy = stats.isProdigy();

        // Glyph and value at each tip: the value sits outboard of the glyph, so nothing crosses the chart.
        for (int i = 0; i < n; i++) {
            PlayerStat stat = all[i];
            int[] tip = StatMeasureChart.point(cx, cy, radius + tipGap, i, n, 1f);
            int gx = tip[0] - StatIcons.SIZE / 2;
            int gy = tip[1] - StatIcons.SIZE / 2;
            StatIcons.drawStat(g, stat, gx, gy);
            String value = Integer.toString(stats.get(stat));
            boolean left = tip[0] < cx - 2;
            int vx = left ? gx - 2 - font.width(value) : gx + StatIcons.SIZE + 2;
            int vy = tip[1] - font.lineHeight / 2 + 1;
            boolean capped = stat.isHeritageCapped() && stats.get(stat) >= powerCap;
            g.drawString(font, value, vx, vy, capped ? COLOR_PRODIGY : StatStyle.ink(stat), false);
            // The row's state mark, outboard of the value: the heritage padlock, or the prodigy star on Power.
            int markX = left ? vx - 1 - StatIcons.SIZE : vx + font.width(value) + 1;
            if (capped) {
                StatIcons.drawCapped(g, markX, gy);
            } else if (prodigy && stat == PlayerStat.POWER) {
                StatIcons.drawProdigy(g, markX, gy);
            }
            if (hovered(gx - 2, gy - 2, StatIcons.SIZE + 4, StatIcons.SIZE + 4, mouseX, mouseY)) {
                pendingTooltip = tooltipFor(stat, stats.get(stat), ClientStatsState.trainingProgress(stat),
                        stat.isHeritageCapped() ? powerCap : PlayerStatsData.MAX_VALUE, prodigy);
            }
        }
        y = cy + (int) Math.ceil(radius * Math.cos(Math.PI / n)) + tipGap + StatIcons.SIZE / 2 + 6;

        // One line per stat: a swatch, its name in its ink, then what it does on the right.
        int lineH = 11;
        for (PlayerStat stat : all) {
            int value = stats.get(stat);
            int ink = StatStyle.ink(stat);
            // A point that just landed lifts its own line for a moment, as the old stat row did.
            float flash = ClientStatLevelUps.flashStrength(stat);
            if (flash > 0f) {
                g.fill(x - 2, y - 1, x + w + 2, y + lineH - 1,
                        ((int) (flash * 64f) << 24) | (WizardsPalette.GILT & 0x00FFFFFF));
            }
            boolean over = hovered(x - 2, y - 1, w + 4, lineH, mouseX, mouseY);
            if (over) {
                g.fill(x - 2, y - 1, x + w + 2, y + lineH - 1, StatStyle.wash(stat, LINE_WASH_HOVER));
                pendingTooltip = tooltipFor(stat, value, ClientStatsState.trainingProgress(stat),
                        stat.isHeritageCapped() ? powerCap : PlayerStatsData.MAX_VALUE, prodigy);
            }
            g.fill(x, y + 2, x + 4, y + 6, ink);
            String name = stat.displayName().getString();
            String effectValue = StatReadout.effectValue(stat, value).getString();
            String effectLabel = StatReadout.effectLabel(stat).getString();
            int nameW = font.width(name);
            g.drawString(font, name, x + 7, y, ink, false);
            int evX = x + w - font.width(effectValue);
            g.drawString(font, effectValue, evX, y, toneColor(StatReadout.effectTone(stat, value)), false);
            int labelX = x + 7 + nameW + 6;
            int labelW = evX - LABEL_VALUE_GAP - labelX;
            if (labelW > 12) {
                String fitted = UiText.fit(font, effectLabel, labelW);
                g.drawString(font, fitted, evX - LABEL_VALUE_GAP - font.width(fitted), y, COLOR_LABEL, false);
            }
            y += lineH;
        }
        return y + 6;
    }

    /**
     * The heritage ceiling on POWER, read on the client from the synced variant.
     *
     * <p>{@link PowerBandTable} is pure and lives in the common package precisely so both sides can
     * consult one table; nothing is trusted to this number, which only decides where a tick is drawn.
     * The server clamps growth itself in {@code PlayerStatsAPI.grantPowerGrowth}.
     */
    private static int powerCap() {
        HeritageVariant variant = ClientHeritageDataState.get().getSelectedHeritageVariant();
        return variant == null ? PlayerStatsData.MAX_VALUE : PowerBandTable.getBandMax(variant);
    }


    /**
     * The hover card for one stat, rebuilt only when something it shows has changed.
     *
     * <p>Training progress is compared at the whole percent the card actually prints, not at full
     * float precision — the eased hairline changes every frame while a sync settles, and keying on
     * that would defeat the cache for the one number nobody can read moving.
     */
    private List<Component> tooltipFor(PlayerStat stat, int value, float progress, int cap,
                                       boolean prodigy) {
        int progressPercent = Math.round(Mth.clamp(progress, 0f, 1f) * 100f);
        List<Component> cached = cachedTooltip;
        if (cached != null && cachedTooltipStat == stat && cachedTooltipValue == value
                && cachedTooltipProgress == progressPercent && cachedTooltipCap == cap
                && cachedTooltipProdigy == prodigy) {
            return cached;
        }
        List<Component> built = StatReadout.tooltip(stat, value, progress, cap, prodigy);
        cachedTooltipStat = stat;
        cachedTooltipValue = value;
        cachedTooltipProgress = progressPercent;
        cachedTooltipCap = cap;
        cachedTooltipProdigy = prodigy;
        cachedTooltip = built;
        return built;
    }

    /** Warm parchment equivalents of the three tones {@link StatReadout} hands back. */
    private static int toneColor(ChatFormatting tone) {
        return switch (tone) {
            case GREEN -> COLOR_EFFECT_GOOD;
            case RED -> COLOR_EFFECT_BAD;
            default -> COLOR_LABEL;
        };
    }

    /**
     * Hit-test for a row, clipped to the visible content rect.
     *
     * <p>The section is drawn inside a scissor and scrolls under it, so a row can be laid out at a
     * y-coordinate that is off-panel entirely. Testing the row rect alone would pop a tooltip for a
     * stat the player cannot see, positioned over whatever is above the tab.
     */
    private boolean hovered(int rx, int ry, int rw, int rh, int mouseX, int mouseY) {
        return mouseX >= Math.max(rx, viewX) && mouseX < Math.min(rx + rw, viewX + viewW)
                && mouseY >= Math.max(ry, viewY) && mouseY < Math.min(ry + rh, viewY + viewH);
    }

    private static void section(@NonNull GuiGraphics g, @NonNull Font font, int x, int y,
                                @NonNull String id) {
        Ui.rubric(g, font, Component.translatable(KEY + "section." + id).getString(), x, y);
    }

    /**
     * Column widths for a group of meter rows, measured from the strings the group will actually
     * draw rather than assumed.
     *
     * <p>A fixed gutter was wrong twice over in the same frame: too narrow for the labels, so
     * "Max Health" rendered as "Max Healt" and "Beast Resistance" as "Beast Resi", and too generous
     * about the right edge, so "20.50" ran under the scrollbar and lost its last digit. Both gutters
     * are now the widest string in the group, and the bar takes what is left.
     *
     * @param labelW widest label in the group, capped so a long label cannot eat the whole row
     * @param barX   left edge of every bar in the group, so the eye tracks one vertical edge
     * @param barW   space between the two gutters, or non-positive when there is no room for a bar
     */
    private record RowGutters(int labelW, int barX, int barW) {}

    private static RowGutters gutters(@NonNull Font font, int x, int w,
                                      @NonNull List<String> labels,
                                      @NonNull List<String> values) {
        int labelW = 0;
        for (String s : labels) {
            labelW = Math.max(labelW, font.width(s));
        }
        // 55%: the widest label the sheet ships is "Beast Resistance" at 96px, and the
        // content column is 180px inside its padding. A tighter cap truncated it.
        labelW = Math.min(labelW, w * 55 / 100);

        int valueW = 0;
        for (String s : values) {
            valueW = Math.max(valueW, font.width(s));
        }
        int barX = x + labelW + LABEL_VALUE_GAP;
        return new RowGutters(labelW, barX, w - labelW - LABEL_VALUE_GAP * 2 - valueW);
    }

    /**
     * One attribute row: label, bar, right-aligned value.
     *
     * <p>These were 26px tiles in a 2x3 grid, which is what a 200px column could hold and no more.
     * A row is 12px, so the same space carries every attribute plus the sections below.
     */
    private void drawMeterRow(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                              @NonNull RowGutters gut, @NonNull String label, @NonNull String value,
                              double fraction) {
        g.drawString(font, UiText.fit(font, label, gut.labelW()), x, y + 1, COLOR_LABEL, false);
        g.drawString(font, value, x + w - font.width(value), y + 1, COLOR_VALUE, false);

        int barW = gut.barW();
        if (barW <= 0) return;
        int barX = gut.barX();
        int barY = y + 2;
        g.fill(barX, barY, barX + barW, barY + 4, COLOR_BAR_TRACK);
        int filled = Math.max(0, Math.min(barW, (int) (fraction * barW)));
        g.fill(barX, barY, barX + filled, barY + 4, COLOR_BAR_FILL);
    }

    private void drawAttributeRows(@NonNull GuiGraphics g, @NonNull LocalPlayer player,
                                    int x, int y, int w, int mouseX, int mouseY) {
        record AttrRow(String name, double value, double min, double max) {}

        AttributeInstance health  = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance armor   = player.getAttribute(Attributes.ARMOR);
        AttributeInstance speed   = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance affin   = player.getAttribute(ModAttributes.WAND_AFFINITY);
        AttributeInstance corrupt = player.getAttribute(ModAttributes.DARK_CORRUPTION);
        AttributeInstance beast   = player.getAttribute(ModAttributes.BEAST_RESISTANCE);

        // Names come from the attributes themselves rather than from six literals here. Vanilla
        // already ships "Max Health" and "Armor" in every language it supports, and the mod's three
        // carry their own description ids — a second English list would be untranslated and would
        // drift the moment one of them was renamed.
        AttrRow[] cards = {
            new AttrRow(attrName(Attributes.MAX_HEALTH.value()),      val(health),  0,  40),
            new AttrRow(attrName(Attributes.ARMOR.value()),           val(armor),   0,  30),
            new AttrRow(attrName(Attributes.MOVEMENT_SPEED.value()),  val(speed),   0,  1),
            new AttrRow(attrName(ModAttributes.WAND_AFFINITY.get()),  val(affin),   0.5, 2),
            new AttrRow(attrName(ModAttributes.DARK_CORRUPTION.get()), val(corrupt), 0,  100),
            new AttrRow(attrName(ModAttributes.BEAST_RESISTANCE.get()), val(beast), 0,  1),
        };

        Font font = Minecraft.getInstance().font;
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (AttrRow row : cards) {
            labels.add(row.name());
            values.add(formatAttr(row.value()));
        }
        RowGutters gut = gutters(font, x, w, labels, values);

        AttributeInstance[] instances = {health, armor, speed, affin, corrupt, beast};
        for (int i = 0; i < cards.length; i++) {
            double range = cards[i].max() - cards[i].min();
            double frac = range > 0 ? (cards[i].value() - cards[i].min()) / range : 0;
            int rowY = y + i * ATTR_ROW_H;
            drawMeterRow(g, font, x, rowY, w, gut,
                         labels.get(i), values.get(i), frac);
            if (instances[i] != null && hovered(x - 2, rowY, w + 4, ATTR_ROW_H, mouseX, mouseY)) {
                pendingTooltip = attributeTooltip(labels.get(i), instances[i]);
            }
        }
    }

    /**
     * Name, then where the number comes from: the base the character has, and what everything worn, held or
     * cast on them adds — the two halves the bar alone cannot show.
     */
    @NonNull
    private static List<Component> attributeTooltip(@NonNull String name, @NonNull AttributeInstance inst) {
        double base = inst.getBaseValue();
        double modifiers = inst.getValue() - base;
        List<Component> lines = new ArrayList<>(3);
        lines.add(Component.literal(name));
        lines.add(Component.translatable(KEY + "attribute.base", formatAttr(base)));
        if (Math.abs(modifiers) > 1.0e-4) {
            lines.add(Component.translatable(KEY + "attribute.modifiers",
                    (modifiers > 0 ? "+" : "-") + formatAttr(Math.abs(modifiers))));
        }
        return lines;
    }

    @NonNull
    private static String attrName(@NonNull Attribute attribute) {
        return Component.translatable(attribute.getDescriptionId()).getString();
    }

    private static double val(@Nullable AttributeInstance inst) {
        return inst != null ? inst.getValue() : 0.0;
    }

    @NonNull
    private static String formatAttr(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
