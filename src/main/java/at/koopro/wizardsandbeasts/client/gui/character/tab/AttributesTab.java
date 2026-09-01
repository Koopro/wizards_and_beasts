package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.client.gui.character.StatIcons;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.stats.ClientStatLevelUps;
import at.koopro.wizardsandbeasts.client.stats.ClientStatsState;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import at.koopro.wizardsandbeasts.stats.PowerBandTable;
import at.koopro.wizardsandbeasts.stats.StatReadout;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.WandEligibility;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Attributes tab: player stats, attributes, wand panel, wand affinity panel, currency panel. */
public final class AttributesTab implements CharacterTab {

    private static final String KEY = "gui.wizards_and_beasts.character_sheet.";

    private static final int COLOR_SECTION  = 0xFFDDB97A;
    private static final int COLOR_LABEL    = 0xFF887766;
    private static final int COLOR_VALUE    = 0xFFEEDDBB;
    /** Empty-bar track. Was {@code 0xFF0D0905}, near-black on the tile these rows replaced — an
     *  attribute sitting at zero looked like a row with no bar at all rather than an empty one,
     *  which is why Armor / Wand Affinity / Beast Resistance read as unfinished. */
    private static final int COLOR_BAR_TRACK = 0xFF3B2A16;
    private static final int COLOR_BAR_FILL  = 0xFF886622;
    /** Training hairline. Deliberately dimmer than {@link #COLOR_BAR_FILL} so the point a player
     *  has earned stays visually louder than the fraction they are working towards. */
    private static final int COLOR_TRAINING_FILL = 0xFF5E4A22;
    private static final int COLOR_PRODIGY   = 0xFFFFD700;
    /** The heritage ceiling, marked on the Power meter. Brass, so it reads as furniture on the bar. */
    private static final int COLOR_CAP_TICK   = 0xFFDBA86D;
    /** Effect-line tones. Off pure red/green to match the effects column: parchment ink, not an LED. */
    private static final int COLOR_EFFECT_GOOD = 0xFF8FBF6A;
    private static final int COLOR_EFFECT_BAD  = 0xFFCC7755;
    /** Clear space kept between a truncated label and its right-aligned value. */
    private static final int LABEL_VALUE_GAP = 4;
    private static final int COLOR_ELIGIBLE   = 0xFF55FF55;
    private static final int COLOR_INELIGIBLE = 0xFFFF5555;
    private static final int COLOR_REASON     = 0xFFAA0000;
    private static final int COLOR_DETAIL     = 0xFFAAAAAA;
    private static final int COLOR_WAND_NAME  = 0xFFFFFFFF;

    /** One attribute row: label line plus its bar. */
    private static final int ATTR_ROW_H = 12;

    /**
     * One stat row: name and value, the meter, the training hairline, and what the stat currently
     * does. Twice an attribute row, because a stat carries twice the information — an attribute is a
     * number the game already explains, where "Precision 37" means nothing without "−2.96% misfire"
     * printed under it.
     */
    private static final int STAT_ROW_H = 26;
    /** Left gutter: the 16px glyph plus its clearance. */
    private static final int STAT_TEXT_X = StatIcons.SIZE + 4;
    /** Right gutter reserved for the row's state mark (heritage padlock, prodigy star). */
    private static final int STAT_STATUS_W = StatIcons.SIZE + 2;

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
        if (ModuleManager.isEnabled(Module.PLAYER_STATS) && ClientStatsState.hasData()) {
            cy = drawStatsSection(g, font, cx, cy, innerW, mouseX, mouseY);
        }

        // ── Attributes: the sum of everything currently modifying the character ──
        section(g, font, cx, cy, "attributes");
        cy += 10;

        drawAttributeRows(g, player, cx, cy, innerW);
        cy += 6 * ATTR_ROW_H + 2;

        // ── Wand panel ────────────────────────────────────────────────────
        ItemStack heldStack = player.getMainHandItem();
        if (heldStack.getItem() instanceof WandItem) {
            cy = drawWandPanel(g, font, cx, cy, innerW, heldStack);
        }

        // ── Wand affinity panel ───────────────────────────────────────────
        cy = drawWandAffinityPanel(g, font, cx, cy, innerW, player);

        // ── Currency panel ────────────────────────────────────────────────
        drawCurrencyPanel(g, font, cx, cy, innerW);
        cy += 10 + 9 * 3;

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
     * <p>Each row answers four questions in the order a player asks them: what is this, how big is it,
     * how close am I to the next point, and what is it doing for me. Only the second of those was on
     * screen before — five bare numbers with no consequence attached to any of them, which is a debug
     * readout rather than a character sheet.
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

        // Declaration order, not a hand-written list: the sheet was one of the places a sixth stat
        // would have had to be remembered, and nothing would have failed if it were not.
        for (PlayerStat stat : PlayerStat.values()) {
            drawStatRow(g, font, x, y, w, stat, stats, powerCap, mouseX, mouseY);
            y += STAT_ROW_H;
        }
        return y + 2;
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

    private void drawStatRow(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                             @NonNull PlayerStat stat, @NonNull PlayerStatsData stats, int powerCap,
                             int mouseX, int mouseY) {
        int value = stats.get(stat);
        String label = stat.displayName().getString();
        String valueText = Integer.toString(value);
        boolean capped = stat.isHeritageCapped() && value >= powerCap;
        boolean prodigy = stats.isProdigy();

        // A point that just landed lifts its own row for a moment, so a player who opens the sheet
        // straight after the toast can see which number moved rather than hunting for it.
        float flash = ClientStatLevelUps.flashStrength(stat);
        if (flash > 0f) {
            int alpha = (int) (flash * 64f) << 24;
            g.fill(x - 2, y - 1, x + w + 2, y + STAT_ROW_H - 2, alpha | 0x00DBA86D);
        }

        StatIcons.drawStat(g, stat, x, y);

        int textX = x + STAT_TEXT_X;
        int statusX = x + w - StatIcons.SIZE;

        // ── name and value ──
        int valueColor = capped ? COLOR_PRODIGY : COLOR_VALUE;
        int valueX = x + w - STAT_STATUS_W - font.width(valueText);
        int labelW = Math.max(0, valueX - textX - LABEL_VALUE_GAP);
        g.drawString(font, font.plainSubstrByWidth(label, labelW), textX, y + 1, COLOR_LABEL, false);
        g.drawString(font, valueText, valueX, y + 1, valueColor, false);

        // ── the state mark, if this row has one ──
        if (capped) {
            StatIcons.drawCapped(g, statusX, y);
        } else if (prodigy && stat == PlayerStat.POWER) {
            StatIcons.drawProdigy(g, statusX, y);
        }

        // ── meter ──
        int barX = textX;
        int barW = w - STAT_TEXT_X - STAT_STATUS_W;
        if (barW > 0) {
            int barY = y + 12;
            g.fill(barX, barY, barX + barW, barY + 3, COLOR_BAR_TRACK);
            int filled = Mth.clamp(Math.round(value / 100.0f * barW), 0, barW);
            g.fill(barX, barY, barX + filled, barY + 3, COLOR_BAR_FILL);

            // Where heritage stops this stat, marked on the bar rather than only stated in the
            // tooltip: a Power meter that stalls two thirds along otherwise looks like a bug.
            if (stat.isHeritageCapped() && powerCap < PlayerStatsData.MAX_VALUE) {
                int tickX = barX + Mth.clamp(Math.round(powerCap / 100.0f * barW), 0, barW - 1);
                g.fill(tickX, barY - 1, tickX + 1, barY + 4, COLOR_CAP_TICK);
            }

            // Training hairline under the main bar: without it the bar sits still for hundreds of
            // casts and training reads as broken.
            if (stat.isTrainable()) {
                float progress = ClientStatsState.trainingProgress(stat);
                int hairY = barY + 4;
                g.fill(barX, hairY, barX + barW, hairY + 1, COLOR_BAR_TRACK);
                int hair = Mth.clamp(Math.round(progress * barW), 0, barW);
                g.fill(barX, hairY, barX + hair, hairY + 1, COLOR_TRAINING_FILL);
            }
        }

        // ── what it is doing right now ──
        String effectLabel = StatReadout.effectLabel(stat).getString();
        String effectValue = StatReadout.effectValue(stat, value).getString();
        int effectY = y + 18;
        int effectValueX = x + w - STAT_STATUS_W - font.width(effectValue);
        g.drawString(font, font.plainSubstrByWidth(effectLabel,
                        Math.max(0, effectValueX - textX - LABEL_VALUE_GAP)),
                textX, effectY, COLOR_LABEL, false);
        g.drawString(font, effectValue, effectValueX, effectY,
                toneColor(StatReadout.effectTone(stat, value)), false);

        // ── hover ──
        if (hovered(x - 2, y - 1, w + 4, STAT_ROW_H - 2, mouseX, mouseY)) {
            pendingTooltip = tooltipFor(stat, value, ClientStatsState.trainingProgress(stat),
                    stat.isHeritageCapped() ? powerCap : PlayerStatsData.MAX_VALUE, prodigy);
        }
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
        g.drawString(font, Component.translatable(KEY + "section." + id).getString(),
                x, y, COLOR_SECTION, false);
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
        g.drawString(font, font.plainSubstrByWidth(label, gut.labelW()), x, y + 1, COLOR_LABEL, false);
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
                                    int x, int y, int w) {
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

        for (int i = 0; i < cards.length; i++) {
            double range = cards[i].max() - cards[i].min();
            double frac = range > 0 ? (cards[i].value() - cards[i].min()) / range : 0;
            drawMeterRow(g, font, x, y + i * ATTR_ROW_H, w, gut,
                         labels.get(i), values.get(i), frac);
        }
    }

    @NonNull
    private static String attrName(@NonNull Attribute attribute) {
        return Component.translatable(attribute.getDescriptionId()).getString();
    }

    private int drawWandPanel(@NonNull GuiGraphics g, @NonNull Font font,
                              int x, int y, int w, @NonNull ItemStack stack) {
        section(g, font, x, y, "wand");
        y += 10;

        @Nullable Identifier wood  = WandComponents.getWood(stack);
        @Nullable Identifier core  = WandComponents.getCore(stack);
        @Nullable WandFlexibility flex = WandComponents.getFlexibility(stack);
        @Nullable Float length = WandComponents.getLength(stack);
        float integrity     = WandComponents.getIntegrity(stack);
        float allegiance    = WandComponents.getAllegianceScore(stack);

        drawKV(g, font, x, y, w, "wand.wood",        idToDisplay(wood));        y += 9;
        drawKV(g, font, x, y, w, "wand.core",        idToDisplay(core));        y += 9;
        drawKV(g, font, x, y, w, "wand.flexibility", flex != null ? flex.getDisplayName() : "—"); y += 9;
        drawKV(g, font, x, y, w, "wand.length",      length != null ? String.format(Locale.ROOT, "%.1f\"", length) : "—"); y += 9;
        drawKV(g, font, x, y, w, "wand.integrity",   String.format(Locale.ROOT, "%.0f%%", integrity * 100f));  y += 9;
        drawKV(g, font, x, y, w, "wand.allegiance",  String.format(Locale.ROOT, "%.0f%%", allegiance * 100f)); y += 11;
        return y;
    }

    private int drawWandAffinityPanel(@NonNull GuiGraphics g, @NonNull Font font,
                                      int x, int y, int w, @NonNull LocalPlayer player) {
        section(g, font, x, y, "wand_affinity");
        y += 10;

        ItemStack wand = WandHelper.getWandStack(player);
        if (wand.isEmpty()) {
            g.drawString(font, Component.translatable("wandcraft.eligibility.no_wand").getString(),
                    x, y, COLOR_LABEL, false);
            return y + 11;
        }

        WandEligibility.Result result = WandEligibility.evaluate(player, wand);

        String name = font.plainSubstrByWidth(wand.getHoverName().getString(), w);
        g.drawString(font, name, x, y, COLOR_WAND_NAME, false);
        y += 9;

        String statusKey = result.eligible()
                ? "wandcraft.eligibility.can_use" : "wandcraft.eligibility.cannot_use";
        int statusColor = result.eligible() ? COLOR_ELIGIBLE : COLOR_INELIGIBLE;
        g.drawString(font, Component.translatable(statusKey).getString(), x, y, statusColor, false);
        y += 9;

        if (!result.eligible() && result.reason() != null) {
            g.drawString(font, font.plainSubstrByWidth(result.reason().getString(), w),
                    x, y, COLOR_REASON, false);
            y += 9;
        }

        for (@Nullable Component detail : new Component[]{
                result.detailLine1(), result.detailLine2(), result.detailLine3()}) {
            if (detail == null) continue;
            g.drawString(font, font.plainSubstrByWidth(detail.getString(), w),
                    x, y, COLOR_DETAIL, false);
            y += 9;
        }
        return y + 2;
    }

    private void drawCurrencyPanel(@NonNull GuiGraphics g, @NonNull Font font,
                                   int x, int y, int w) {
        section(g, font, x, y, "currency");
        y += 10;

        long galleons = ClientVaultDataState.get().getGalleons();
        long sickles  = ClientVaultDataState.get().getSickles();
        long knuts    = ClientVaultDataState.get().getKnuts();

        drawKV(g, font, x, y, w, "currency.galleons", String.valueOf(galleons)); y += 9;
        drawKV(g, font, x, y, w, "currency.sickles",  String.valueOf(sickles));  y += 9;
        drawKV(g, font, x, y, w, "currency.knuts",    String.valueOf(knuts));
    }

    /** @param labelId key suffix under {@code gui.wizards_and_beasts.character_sheet.} */
    private static void drawKV(@NonNull GuiGraphics g, @NonNull Font font,
                                int x, int y, int w,
                                @NonNull String labelId, @NonNull String value) {
        String key = Component.translatable(KEY + labelId).getString();
        g.drawString(font, key + ":", x, y, COLOR_LABEL, false);
        int kw = font.width(key + ": ");
        String val = font.plainSubstrByWidth(value, w - kw);
        g.drawString(font, val, x + kw, y, COLOR_VALUE, false);
    }

    private static double val(@Nullable AttributeInstance inst) {
        return inst != null ? inst.getValue() : 0.0;
    }

    @NonNull
    private static String formatAttr(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    @NonNull
    private static String idToDisplay(@Nullable Identifier id) {
        if (id == null) return "—";
        String path = id.getPath();
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
    }
}
