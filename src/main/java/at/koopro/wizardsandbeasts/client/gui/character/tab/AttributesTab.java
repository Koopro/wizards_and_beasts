package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
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

/** Attributes tab: 6 attribute cards, wand panel, wand affinity panel, currency panel. */
public final class AttributesTab implements CharacterTab {

    private static final int COLOR_SECTION  = 0xFFDDB97A;
    private static final int COLOR_ATTR_BG  = 0xFF1E1408;
    private static final int COLOR_HI       = 0xFF3A2A14;
    private static final int COLOR_SHADOW   = 0xFF0A0500;
    private static final int COLOR_LABEL    = 0xFF887766;
    private static final int COLOR_VALUE    = 0xFFEEDDBB;
    /** Empty-bar track. Was {@code 0xFF0D0905}, near-black on {@link #COLOR_ATTR_BG} — an
     *  attribute sitting at zero looked like a card with no bar at all rather than an
     *  empty one, which is why Armor / Wand Affinity / Beast Resistance read as unfinished. */
    private static final int COLOR_BAR_TRACK = 0xFF3B2A16;
    private static final int COLOR_BAR_FILL  = 0xFF886622;
    /** Clear space kept between a truncated label and its right-aligned value. */
    private static final int LABEL_VALUE_GAP = 4;
    private static final int COLOR_ELIGIBLE   = 0xFF55FF55;
    private static final int COLOR_INELIGIBLE = 0xFFFF5555;
    private static final int COLOR_REASON     = 0xFFAA0000;
    private static final int COLOR_DETAIL     = 0xFFAAAAAA;
    private static final int COLOR_WAND_NAME  = 0xFFFFFFFF;
    private static final int CARD_W         = 88;
    private static final int CARD_H         = 26;
    private static final int CARD_GAP       = 3;
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

        // ── 6 attribute cards in a 2-column grid ──────────────────────────
        g.drawString(font, "Attributes", cx, cy, COLOR_SECTION, false);
        cy += 10;

        drawAttributeCards(g, player, cx, cy, w - 4);
        cy += (CARD_H + CARD_GAP) * 3 + 2;

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

    private void drawAttributeCards(@NonNull GuiGraphics g, @NonNull LocalPlayer player,
                                    int x, int y, int w) {
        record AttrCard(String name, double value, double min, double max) {}

        AttributeInstance health  = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance armor   = player.getAttribute(Attributes.ARMOR);
        AttributeInstance speed   = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance affin   = player.getAttribute(ModAttributes.WAND_AFFINITY);
        AttributeInstance corrupt = player.getAttribute(ModAttributes.DARK_CORRUPTION);
        AttributeInstance beast   = player.getAttribute(ModAttributes.BEAST_RESISTANCE);

        AttrCard[] cards = {
            new AttrCard("Max Health",       val(health),  0,  40),
            new AttrCard("Armor",            val(armor),   0,  30),
            new AttrCard("Speed",            val(speed),   0,  1),
            new AttrCard("Wand Affinity",    val(affin),   0.5, 2),
            new AttrCard("Dark Corruption",  val(corrupt), 0,  100),
            new AttrCard("Beast Resistance", val(beast),   0,  1),
        };

        int cardW = (w - CARD_GAP) / 2;
        for (int i = 0; i < cards.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int cx  = x + col * (cardW + CARD_GAP);
            int cy  = y + row * (CARD_H + CARD_GAP);
            drawAttrCard(g, cx, cy, cardW, CARD_H, cards[i].name(), cards[i].value(),
                         cards[i].min(), cards[i].max());
        }
    }

    private void drawAttrCard(@NonNull GuiGraphics g, int x, int y, int w, int h,
                              String name, double value, double min, double max) {
        Font font = Minecraft.getInstance().font;
        McStylePanel.drawPanel(g, x, y, w, h, COLOR_ATTR_BG, COLOR_HI, COLOR_SHADOW);

        // The value is measured first because the label has to be truncated around it.
        // Truncating to the full card width instead let "Dark Corruption" and "Beast
        // Resistance" run under the right-aligned value and render as "Dark Corrupti100"
        // and "Beast Resistanc0" -- the four shorter labels fit, so nothing caught it.
        String valStr = formatAttr(value);
        int valW = font.width(valStr);

        String nameTrunc = font.plainSubstrByWidth(name, w - 6 - valW - LABEL_VALUE_GAP);
        g.drawString(font, nameTrunc, x + 3, y + 3, COLOR_LABEL, false);
        g.drawString(font, valStr, x + w - 3 - valW, y + 3, COLOR_VALUE, false);

        // progress bar
        int barY = y + 13;
        int barW = w - 6;
        g.fill(x + 3, barY, x + 3 + barW, barY + 4, COLOR_BAR_TRACK);
        double range = max - min;
        if (range > 0) {
            int filled = (int)((value - min) / range * barW);
            filled = Math.max(0, Math.min(filled, barW));
            g.fill(x + 3, barY, x + 3 + filled, barY + 4, COLOR_BAR_FILL);
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
