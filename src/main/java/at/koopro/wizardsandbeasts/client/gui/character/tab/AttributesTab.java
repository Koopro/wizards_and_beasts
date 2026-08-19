package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.client.stats.ClientStatsState;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.WandEligibility;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
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
    /** Clear space kept between a truncated label and its right-aligned value. */
    private static final int LABEL_VALUE_GAP = 4;
    private static final int COLOR_ELIGIBLE   = 0xFF55FF55;
    private static final int COLOR_INELIGIBLE = 0xFFFF5555;
    private static final int COLOR_REASON     = 0xFFAA0000;
    private static final int COLOR_DETAIL     = 0xFFAAAAAA;
    private static final int COLOR_WAND_NAME  = 0xFFFFFFFF;
    /** One stat row: label line plus its bar and training hairline. */
    private static final int STAT_ROW_H     = 12;

    private float scrollOffset = 0f; // pixels scrolled from top
    private int lastTotalH = 0;      // content height measured last frame

    @Override
    public @NonNull String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.attributes";
    }

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof LocalPlayer player)) return;
        Font font = mc.font;

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
            cy = drawStatsSection(g, font, cx, cy, innerW);
        }

        // ── Attributes: the sum of everything currently modifying the character ──
        g.drawString(font, "Attributes", cx, cy, COLOR_SECTION, false);
        cy += 10;

        drawAttributeRows(g, player, cx, cy, innerW);
        cy += 6 * STAT_ROW_H + 2;

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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float) (delta * 10.0);
        return true;
    }

    // ── private helpers ───────────────────────────────────────────────────

    /**
     * Paints the five {@link PlayerStat} values and returns the new content cursor.
     *
     * <p>A stat row is an attribute row on a fixed 0–100 scale, plus — for the three trainable
     * stats — a hairline showing how far into the next point the player is. Without that second bar
     * the main bar sits still for hundreds of casts and training reads as broken.
     *
     * <p>Section header is a bare literal to match "Attributes", "Wand" and "Currency" below it; the
     * stat <em>names</em> go through {@link PlayerStat#displayName()}, whose keys already ship.
     */
    private int drawStatsSection(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w) {
        PlayerStatsData stats = ClientStatsState.get();

        g.drawString(font, "Stats", x, y, COLOR_SECTION, false);
        if (stats.isProdigy()) {
            String tag = "♦ Prodigy";
            g.drawString(font, tag, x + w - font.width(tag), y, COLOR_PRODIGY, false);
        }
        y += 10;

        PlayerStat[] order = {
            PlayerStat.POWER, PlayerStat.PRECISION,
            PlayerStat.WILLPOWER, PlayerStat.REFLEXES,
            PlayerStat.KNOWLEDGE,
        };

        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (PlayerStat stat : order) {
            labels.add(stat.displayName().getString());
            values.add(Integer.toString(valueOf(stats, stat)));
        }
        RowGutters gut = gutters(font, x, w, labels, values);

        for (int i = 0; i < order.length; i++) {
            PlayerStat stat = order[i];
            drawMeterRow(g, font, x, y, w, gut, labels.get(i), values.get(i),
                         valueOf(stats, stat) / 100.0,
                         stat.isTrainable() ? stats.trainingProgress().getOrDefault(stat, 0f) : -1f);
            y += STAT_ROW_H;
        }
        return y + 2;
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
     * One meter row: label, bar, right-aligned value.
     *
     * <p>These were 26px tiles in a 2x3 grid, which is what a 200px column could hold and no more.
     * A row is 12px, so the same space carries every stat and attribute plus the sections below.
     *
     * @param trainingProgress fraction into the next point, or a negative value to omit the hairline
     */
    private void drawMeterRow(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                              @NonNull RowGutters gut, @NonNull String label, @NonNull String value,
                              double fraction, float trainingProgress) {
        g.drawString(font, font.plainSubstrByWidth(label, gut.labelW()), x, y + 1, COLOR_LABEL, false);
        g.drawString(font, value, x + w - font.width(value), y + 1, COLOR_VALUE, false);

        int barW = gut.barW();
        if (barW <= 0) return;
        int barX = gut.barX();
        int barY = y + 2;
        g.fill(barX, barY, barX + barW, barY + 4, COLOR_BAR_TRACK);
        int filled = Math.max(0, Math.min(barW, (int) (fraction * barW)));
        g.fill(barX, barY, barX + filled, barY + 4, COLOR_BAR_FILL);

        // Training hairline under the main bar: without it the bar sits still for hundreds of
        // casts and training reads as broken.
        if (trainingProgress < 0f) return;
        int hairY = barY + 5;
        g.fill(barX, hairY, barX + barW, hairY + 1, COLOR_BAR_TRACK);
        int hair = Math.max(0, Math.min(barW, (int) (trainingProgress * barW)));
        g.fill(barX, hairY, barX + hair, hairY + 1, COLOR_TRAINING_FILL);
    }

    /**
     * KNOWLEDGE is derived server-side; the client reads the last synced snapshot for every stat alike.
     *
     * <p>This was a switch naming each constant, which made the character sheet one of the places a new
     * stat had to be remembered — and the compiler only caught it because the switch was exhaustive. The
     * stat block is keyed by {@link PlayerStat} now, so a new constant renders here on its own.
     */
    private static int valueOf(@NonNull PlayerStatsData stats, @NonNull PlayerStat stat) {
        return stats.get(stat);
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

        AttrRow[] cards = {
            new AttrRow("Max Health",       val(health),  0,  40),
            new AttrRow("Armor",            val(armor),   0,  30),
            new AttrRow("Speed",            val(speed),   0,  1),
            new AttrRow("Wand Affinity",    val(affin),   0.5, 2),
            new AttrRow("Dark Corruption",  val(corrupt), 0,  100),
            new AttrRow("Beast Resistance", val(beast),   0,  1),
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
            drawMeterRow(g, font, x, y + i * STAT_ROW_H, w, gut,
                         labels.get(i), values.get(i), frac, -1f);
        }
    }

    private int drawWandPanel(@NonNull GuiGraphics g, @NonNull Font font,
                              int x, int y, int w, @NonNull ItemStack stack) {
        g.drawString(font, "Wand", x, y, COLOR_SECTION, false);
        y += 10;

        @Nullable Identifier wood  = WandComponents.getWood(stack);
        @Nullable Identifier core  = WandComponents.getCore(stack);
        @Nullable WandFlexibility flex = WandComponents.getFlexibility(stack);
        @Nullable Float length = WandComponents.getLength(stack);
        float integrity     = WandComponents.getIntegrity(stack);
        float allegiance    = WandComponents.getAllegianceScore(stack);

        drawKV(g, font, x, y,      w, "Wood",        idToDisplay(wood));        y += 9;
        drawKV(g, font, x, y,      w, "Core",        idToDisplay(core));        y += 9;
        drawKV(g, font, x, y,      w, "Flexibility", flex != null ? flex.getDisplayName() : "—"); y += 9;
        drawKV(g, font, x, y,      w, "Length",      length != null ? String.format("%.1f\"", length) : "—"); y += 9;
        drawKV(g, font, x, y,      w, "Integrity",   String.format("%.0f%%", integrity * 100f));  y += 9;
        drawKV(g, font, x, y,      w, "Allegiance",  String.format("%.0f%%", allegiance * 100f)); y += 11;
        return y;
    }

    private int drawWandAffinityPanel(@NonNull GuiGraphics g, @NonNull Font font,
                                      int x, int y, int w, @NonNull LocalPlayer player) {
        g.drawString(font, "Wand Affinity", x, y, COLOR_SECTION, false);
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
        g.drawString(font, "Carried Coin", x, y, COLOR_SECTION, false);
        y += 10;

        long galleons = ClientVaultDataState.get().getGalleons();
        long sickles  = ClientVaultDataState.get().getSickles();
        long knuts    = ClientVaultDataState.get().getKnuts();

        drawKV(g, font, x, y, w, "Galleons", String.valueOf(galleons)); y += 9;
        drawKV(g, font, x, y, w, "Sickles",  String.valueOf(sickles));  y += 9;
        drawKV(g, font, x, y, w, "Knuts",    String.valueOf(knuts));
    }

    private static void drawKV(@NonNull GuiGraphics g, @NonNull Font font,
                                int x, int y, int w,
                                @NonNull String key, @NonNull String value) {
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
