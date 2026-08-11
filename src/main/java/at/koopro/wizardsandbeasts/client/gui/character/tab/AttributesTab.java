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
    /** Label gutter, so every stat's bar starts at the same x and the eye tracks one edge. */
    private static final int LABEL_COL_W    = 52;
    private static final int SCROLLBAR_W    = 4;
    private static final int COLOR_SCROLL_TRACK = 0xFF1A1005;
    private static final int COLOR_SCROLL_THUMB = 0xFF886622;

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

        // ── The character's own five numbers ──────────────────────────────
        // Above the attributes on purpose: these are the character's own numbers, where the block
        // below is the sum of everything currently modifying them.
        if (ModuleManager.isEnabled(Module.PLAYER_STATS) && ClientStatsState.hasData()) {
            cy = drawStatsSection(g, font, cx, cy, w - 4);
        }

        // ── Attributes: the sum of everything currently modifying the character ──
        g.drawString(font, "Attributes", cx, cy, COLOR_SECTION, false);
        cy += 10;

        drawAttributeRows(g, player, cx, cy, w - 4);
        cy += 6 * STAT_ROW_H + 2;

        // ── Wand panel ────────────────────────────────────────────────────
        ItemStack heldStack = player.getMainHandItem();
        if (heldStack.getItem() instanceof WandItem) {
            cy = drawWandPanel(g, font, cx, cy, w - 4, heldStack);
        }

        // ── Wand affinity panel ───────────────────────────────────────────
        cy = drawWandAffinityPanel(g, font, cx, cy, w - 4, player);

        // ── Currency panel ────────────────────────────────────────────────
        drawCurrencyPanel(g, font, cx, cy, w - 4);
        cy += 10 + 9 * 3;

        g.disableScissor();

        lastTotalH = cy - top + 4;

        // Scrollbar (overlaid on the right edge, only when content overflows)
        if (lastTotalH > h) {
            int sbX = x + w - SCROLLBAR_W;
            g.fill(sbX, y, sbX + SCROLLBAR_W, y + h, COLOR_SCROLL_TRACK);
            float thumbPct = (float) h / lastTotalH;
            int thumbH = Math.max(8, (int) (h * thumbPct));
            int thumbY = y + (int) ((scrollOffset / Math.max(1f, lastTotalH - h)) * (h - thumbH));
            g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, COLOR_SCROLL_THUMB);
        }
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

        for (PlayerStat stat : order) {
            drawStatRow(g, font, x, y, w, stat, valueOf(stats, stat),
                        stat.isTrainable() ? stats.trainingProgress().getOrDefault(stat, 0f) : -1f);
            y += STAT_ROW_H;
        }
        return y + 2;
    }

    /**
     * One stat as a row: name, bar, value — read down a column of numbers rather than across a
     * grid of lookalike tiles.
     *
     * <p>These were 2x3 cards of 26px each, which is what a 200px column could hold and no more.
     * A row is 12px, so the same space carries the stats, their training progress and whatever the
     * section below wants, and the values line up in one column instead of alternating sides.
     *
     * @param trainingProgress fraction into the next point, or a negative value to omit the hairline
     */
    private void drawStatRow(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                             @NonNull PlayerStat stat, int value, float trainingProgress) {
        String valStr = Integer.toString(value);
        int valW = font.width(valStr);
        int labelW = LABEL_COL_W;
        int barX = x + labelW + LABEL_VALUE_GAP;
        int barW = w - labelW - LABEL_VALUE_GAP * 2 - valW;

        g.drawString(font, font.plainSubstrByWidth(stat.displayName().getString(), labelW),
                x, y + 1, COLOR_LABEL, false);
        g.drawString(font, valStr, x + w - valW, y + 1, COLOR_VALUE, false);

        if (barW <= 0) return;
        int barY = y + 2;
        g.fill(barX, barY, barX + barW, barY + 4, COLOR_BAR_TRACK);
        int filled = Math.max(0, Math.min(barW, value * barW / 100));
        g.fill(barX, barY, barX + filled, barY + 4, COLOR_BAR_FILL);

        // Training hairline under the main bar: without it the bar sits still for hundreds of
        // casts and training reads as broken.
        if (trainingProgress < 0f) return;
        int hairY = barY + 5;
        g.fill(barX, hairY, barX + barW, hairY + 1, COLOR_BAR_TRACK);
        int hair = Math.max(0, Math.min(barW, (int) (trainingProgress * barW)));
        g.fill(barX, hairY, barX + hair, hairY + 1, COLOR_TRAINING_FILL);
    }

    /** KNOWLEDGE is derived server-side; the client reads the last synced snapshot for all five. */
    private static int valueOf(@NonNull PlayerStatsData stats, @NonNull PlayerStat stat) {
        return switch (stat) {
            case POWER     -> stats.power();
            case PRECISION -> stats.precision();
            case WILLPOWER -> stats.willpower();
            case REFLEXES  -> stats.reflexes();
            case KNOWLEDGE -> stats.knowledge();
        };
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
        for (int i = 0; i < cards.length; i++) {
            drawAttrRow(g, font, x, y + i * STAT_ROW_H, w, cards[i].name(), cards[i].value(),
                        cards[i].min(), cards[i].max());
        }
    }

    /**
     * One attribute as a row, sharing {@link #drawStatRow}'s gutter so stats and attributes line
     * their bars and values up on the same two edges.
     *
     * <p>These were 26px tiles in a 2x3 grid, which is what a 200px column could hold and no more.
     * The value is still measured before the label is truncated: truncating to the full row width
     * instead let "Dark Corruption" and "Beast Resistance" run under the right-aligned value and
     * render as "Dark Corrupti100" and "Beast Resistanc0" — the four shorter labels fit, so nothing
     * caught it.
     */
    private void drawAttrRow(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                             String name, double value, double min, double max) {
        String valStr = formatAttr(value);
        int valW = font.width(valStr);
        int barX = x + LABEL_COL_W + LABEL_VALUE_GAP;
        int barW = w - LABEL_COL_W - LABEL_VALUE_GAP * 2 - valW;

        g.drawString(font, font.plainSubstrByWidth(name, LABEL_COL_W), x, y + 1, COLOR_LABEL, false);
        g.drawString(font, valStr, x + w - valW, y + 1, COLOR_VALUE, false);

        if (barW <= 0) return;
        int barY = y + 2;
        g.fill(barX, barY, barX + barW, barY + 4, COLOR_BAR_TRACK);
        double range = max - min;
        if (range > 0) {
            int filled = (int) ((value - min) / range * barW);
            filled = Math.max(0, Math.min(filled, barW));
            g.fill(barX, barY, barX + filled, barY + 4, COLOR_BAR_FILL);
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
