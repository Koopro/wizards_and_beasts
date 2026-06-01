package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Attributes tab: 6 attribute cards, wand panel, currency panel. */
public final class AttributesTab implements CharacterTab {

    private static final int COLOR_SECTION  = 0xFFDDB97A;
    private static final int COLOR_ATTR_BG  = 0xFF1E1408;
    private static final int COLOR_HI       = 0xFF3A2A14;
    private static final int COLOR_SHADOW   = 0xFF0A0500;
    private static final int COLOR_LABEL    = 0xFF887766;
    private static final int COLOR_VALUE    = 0xFFEEDDBB;
    private static final int CARD_W         = 88;
    private static final int CARD_H         = 26;
    private static final int CARD_GAP       = 3;

    @Override
    public @NonNull String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.attributes";
    }

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof LocalPlayer player)) return;
        Font font = mc.font;

        int cx = x + 2;
        int cy = y + 2;

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

        // ── Currency panel ────────────────────────────────────────────────
        drawCurrencyPanel(g, font, cx, cy, w - 4);
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

        String nameTrunc = font.plainSubstrByWidth(name, w - 4);
        g.drawString(font, nameTrunc, x + 3, y + 3, COLOR_LABEL, false);

        // progress bar
        int barY = y + 13;
        int barW = w - 6;
        g.fill(x + 3, barY, x + 3 + barW, barY + 4, 0xFF0D0905);
        double range = max - min;
        if (range > 0) {
            int filled = (int)((value - min) / range * barW);
            filled = Math.max(0, Math.min(filled, barW));
            g.fill(x + 3, barY, x + 3 + filled, barY + 4, 0xFF886622);
        }

        // value text (right-aligned)
        String valStr = formatAttr(value);
        int valW = font.width(valStr);
        g.drawString(font, valStr, x + w - 3 - valW, y + 3, COLOR_VALUE, false);
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
