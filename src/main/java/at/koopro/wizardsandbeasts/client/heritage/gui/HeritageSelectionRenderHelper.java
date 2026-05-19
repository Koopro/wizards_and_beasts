package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.ScreenLayoutScaler;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Drawing for {@link HeritageSelectionScreen} (heritage / variant / confirm phases).
 */
public final class HeritageSelectionRenderHelper {

    public static final int COL_BG = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_BG;
    public static final int COL_BORDER_LIGHT = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_BORDER_LIGHT;
    public static final int COL_BORDER_DARK = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_BORDER_DARK;
    public static final int COL_INNER_BG = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_INNER_BG;
    public static final int COL_CARD_BG = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_CARD_BG;
    public static final int COL_CYAN = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_CYAN;
    public static final int COL_TEXT = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_TEXT;
    public static final int COL_DIM = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_DIM;
    public static final int COL_GREEN = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_GREEN;
    public static final int COL_RED = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_RED;
    public static final int COL_INK = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_INK;
    public static final int COL_ACCENT = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_ACCENT;
    public static final int COL_DIVIDER = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_DIVIDER;
    public static final int COL_PARCHMENT_BRIGHT = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_PARCHMENT_BRIGHT;
    public static final int COL_PARCHMENT_DARK = WizardsAndBeastsUiTokens.HeritageSelection.COLOR_PARCHMENT_DARK;
    private static final Identifier ICON_FALLBACK =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/type_icons/placeholder.png");
    /** Pixel size of type icon sheets under textures/gui/type_icons (standardized to 64x64). */
    private static final int ICON_TEXTURE_SIZE = 64;

    private HeritageSelectionRenderHelper() {}

    public static void renderTypeCard(GuiGraphics g, Font font, int px, int py, int panelW, int panelH,
                                      @Nullable Heritage selectedType, ScreenLayoutScaler layout) {
        Heritage resolved = selectedType == null ? Heritage.values()[0] : selectedType;

        drawFrame(g, px, py, panelW, panelH);

        int cardX = px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_X);
        int cardY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_Y);
        int cardW = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_W);
        int cardH = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_H);
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, COL_CARD_BG);
        drawOutline(g, cardX, cardY, cardW, cardH, COL_BORDER_LIGHT, COL_BORDER_DARK);

        int headerX = px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.HEADER_X);
        int headerY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.HEADER_Y);
        int headerW = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.HEADER_W);
        int headerH = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.HEADER_H);
        g.fill(headerX, headerY, headerX + headerW, headerY + headerH, COL_PARCHMENT_DARK);
        g.fill(headerX + 1, headerY + 1, headerX + headerW - 1, headerY + headerH - 1, COL_PARCHMENT_BRIGHT);
        drawOutline(g, headerX, headerY, headerW, headerH, COL_ACCENT, COL_BORDER_DARK);

        int iconX = px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.TYPE_ICON_X);
        int iconY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.TYPE_ICON_Y);
        drawTypeIcon(g, resolved, iconX, iconY, layout);
        int headerTextX = iconX + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.TYPE_ICON_SIZE)
                + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.HEADER_TEXT_GAP);
        g.drawString(font, normalizeName(resolved.getDisplayName()),
                headerTextX, py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.TITLE_Y), COL_INK, false);
        List<FormattedCharSequence> descLines = font.split(Component.literal(resolved.getDescription()),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.HEADER_W - WizardsAndBeastsUiTokens.HeritageSelection.TYPE_ICON_SIZE - 20));
        int descY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.HEADER_DESC_Y);
        for (int i = 0; i < Math.min(WizardsAndBeastsUiTokens.HeritageSelection.HEADER_DESC_MAX_LINES, descLines.size()); i++) {
            g.drawString(font, descLines.get(i), headerTextX, descY, COL_DIM);
            descY += font.lineHeight;
        }

        int textX = px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.BODY_X);
        int y = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.BODY_START_Y);
        int lineH = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.LINE_H);

        g.drawString(font, "Core Stats", textX, y, COL_ACCENT, false);
        y += lineH;
        g.drawString(font, "Health: " + String.format("%.0f", 20 + resolved.getBaseHealth()), textX, y, COL_TEXT, false);
        y += lineH;
        g.drawString(font, "Speed: " + String.format("%.0f", resolved.getBaseSpeed() * 100), textX, y, COL_TEXT, false);
        y += lineH;
        g.drawString(font, "Armor: " + String.format("%.0f", Math.max(0, resolved.getBaseArmor())), textX, y, COL_TEXT, false);
        y += lineH + 4;

        g.fill(textX, y - 4, px + panelW - layout.s(WizardsAndBeastsUiTokens.HeritageSelection.INFO_RIGHT_PAD), y - 3, COL_DIVIDER);
        g.drawString(font, "Magic Profile", textX, y, COL_ACCENT, false);
        y += lineH;
        g.drawString(font, "Source: " + resolved.getMagicSource().getDisplayName(), textX, y, COL_DIM, false);
        y += lineH;
        g.drawString(font, "Wand Use: " + (resolved.canUseWand() ? "Allowed" : "Restricted"), textX, y,
                resolved.canUseWand() ? COL_GREEN : COL_RED, false);
        y += lineH + 4;

        g.fill(textX, y - 4, px + panelW - layout.s(WizardsAndBeastsUiTokens.HeritageSelection.INFO_RIGHT_PAD), y - 3, COL_DIVIDER);
        g.drawString(font, "Physical Profile", textX, y, COL_ACCENT, false);
        y += lineH;
        String profile = "Size: " + normalizeName(resolved.getSizeCategory().getDisplayName());
        List<FormattedCharSequence> wrapped = font.split(Component.literal(profile), cardW - layout.s(22));
        for (FormattedCharSequence line : wrapped) {
            g.drawString(font, line, textX, y, COL_TEXT, false);
            y += lineH;
        }

        if (!resolved.isAlphaAvailable()) {
            g.drawCenteredString(font,
                    Component.translatable("ui.wizards_and_beasts.type_selection.coming_soon"),
                    px + panelW / 2,
                    py + panelH - layout.s(18),
                    COL_RED);
        }
    }

    public static void renderSubtypeList(GuiGraphics g, Font font, int px, int py, int panelW,
                                         @Nullable Heritage selectedType, ScreenLayoutScaler layout) {
        if (selectedType == null) return;

        drawFrame(g, px, py, panelW, layout.panelH());
        int cardX = px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_X);
        int cardY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_Y);
        int cardW = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_W);
        int cardH = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_H);
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, COL_CARD_BG);
        drawOutline(g, cardX, cardY, cardW, cardH, COL_BORDER_LIGHT, COL_BORDER_DARK);

        int iconX = px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.SUBTYPE_ICON_X);
        int iconY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.SUBTYPE_ICON_Y);
        drawTypeIcon(g, selectedType, iconX, iconY, layout);

        g.drawString(font, normalizeName(selectedType.getDisplayName()), iconX + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.TYPE_ICON_SIZE + 10), py + layout.s(20), COL_INK, false);
        g.fill(px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.DIVIDER_LEFT), py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.DIVIDER_Y),
                px + panelW - layout.s(WizardsAndBeastsUiTokens.HeritageSelection.DIVIDER_RIGHT), py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.DIVIDER_Y) + 1,
                COL_DIVIDER);

        int infoY = py + layout.s(34);
        int subtypeHeaderTextX = iconX + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.TYPE_ICON_SIZE + 10);
        List<FormattedCharSequence> subtypeDesc = font.split(Component.literal(selectedType.getDescription()),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.HEADER_W - WizardsAndBeastsUiTokens.HeritageSelection.TYPE_ICON_SIZE - 22));
        int subtypeDescY = infoY + layout.s(10);
        for (int i = 0; i < Math.min(2, subtypeDesc.size()); i++) {
            g.drawString(font, subtypeDesc.get(i), subtypeHeaderTextX, subtypeDescY, COL_TEXT, false);
            subtypeDescY += font.lineHeight;
        }

        g.drawString(font, String.format("Base: HP %+.0f  SPD %+.3f  ARM %+.0f",
                        selectedType.getBaseHealth(), selectedType.getBaseSpeed(), selectedType.getBaseArmor()),
                px + layout.s(12), infoY + layout.s(42), COL_DIM, false);

        String info = "Wand: " + (selectedType.canUseWand() ? "Yes" : "No")
                + "  |  Magic: " + selectedType.getMagicSource().getDisplayName()
                + "  |  Size: " + selectedType.getSizeCategory().getDisplayName();
        g.drawString(font, info, px + layout.s(12), infoY + layout.s(55), COL_DIM, false);

        g.fill(px + layout.s(10), py + layout.s(110), px + panelW - layout.s(10), py + layout.s(111), COL_DIVIDER);

        g.drawString(font, "Choose Subtype:", px + layout.s(12), py + layout.s(116), COL_INK, false);

        var subtypes = selectedType.getSubtypes();
        int listY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.SUBTYPE_LIST_Y);
        for (int i = 0; i < subtypes.size(); i++) {
            HeritageVariant sub = subtypes.get(i);
            int y = listY + i * (layout.s(WizardsAndBeastsUiTokens.HeritageSelection.LIST_BUTTON_HEIGHT) + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.LIST_BUTTON_GAP)) + layout.s(6);
            if (!sub.getTags().isEmpty()) {
                String tags = String.join(", ", sub.getTags());
                String tagStr = tags.length() > 30 ? tags.substring(0, 27) + "..." : tags;
                g.drawString(font, tagStr, px + panelW - layout.s(10) - font.width(tagStr), y, COL_DIM, false);
            }
        }
    }

    public static void renderConfirmation(GuiGraphics g, Font font, int px, int py, int panelW, int panelH,
                                          @Nullable Heritage selectedType, @Nullable HeritageVariant selectedSubtype, ScreenLayoutScaler layout) {
        if (selectedType == null || selectedSubtype == null) return;

        drawFrame(g, px, py, panelW, panelH);
        int cardX = px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_X);
        int cardY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_Y);
        int cardW = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_W);
        int cardH = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_H);
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, COL_CARD_BG);
        drawOutline(g, cardX, cardY, cardW, cardH, COL_BORDER_LIGHT, COL_BORDER_DARK);

        int iconX = px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CONFIRM_ICON_X);
        int iconY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CONFIRM_ICON_Y);
        drawTypeIcon(g, selectedType, iconX, iconY, layout);

        g.drawString(font, "Confirm Your Choice", px + layout.s(102), py + layout.s(21), COL_INK, false);
        g.fill(px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.DIVIDER_LEFT), py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.DIVIDER_Y),
                px + panelW - layout.s(WizardsAndBeastsUiTokens.HeritageSelection.DIVIDER_RIGHT), py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.DIVIDER_Y) + 1,
                COL_DIVIDER);

        int y = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.DIVIDER_Y) + layout.s(8);
        int x = px + layout.s(18);

        String typeTitle = normalizeName(selectedType.getDisplayName());
        String dashMid = " - ";
        String subTitle = normalizeName(selectedSubtype.getDisplayName());
        g.drawString(font, typeTitle, x, y, selectedType.getColor(), false);
        int midX = x + font.width(typeTitle);
        g.drawString(font, dashMid, midX, y, COL_DIM, false);
        g.drawString(font, subTitle, midX + font.width(dashMid), y, selectedSubtype.getUiColor(), false);
        y += 16;

        List<FormattedCharSequence> typeDescLines = font.split(Component.literal(selectedType.getDescription()),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CARD_W - 28));
        for (int i = 0; i < Math.min(2, typeDescLines.size()); i++) {
            g.drawString(font, typeDescLines.get(i), x, y, COL_TEXT, false);
            y += font.lineHeight;
        }
        g.drawString(font, selectedSubtype.getDescription(), x, y, COL_DIM, false);
        y += 16;

        g.fill(px + layout.s(10), y, px + panelW - layout.s(10), y + 1, 0xFF3A3A3A);
        y += 8;

        g.drawString(font, "Stat Preview:", x, y, COL_ACCENT, false);
        y += 14;

        double totalHp = selectedSubtype.getTotalHealth();
        double totalSpd = selectedSubtype.getTotalSpeed();
        double totalArm = selectedSubtype.getTotalArmor();

        g.drawString(font, String.format("Health: %.0f (%+.0f from base 20)",
                20 + totalHp, totalHp), x, y, totalHp >= 0 ? COL_GREEN : COL_RED, false);
        y += 12;
        g.drawString(font, String.format("Speed: %.3f (%+.3f from base 0.100)",
                0.1 + totalSpd, totalSpd), x, y, totalSpd >= 0 ? COL_GREEN : COL_RED, false);
        y += 12;
        g.drawString(font, String.format("Armor: %.0f (%+.0f)",
                totalArm, totalArm), x, y, totalArm > 0 ? COL_GREEN : COL_TEXT, false);
        y += 12;
        g.drawString(font, "Size: " + normalizeName(selectedType.getSizeCategory().getDisplayName()), x, y, COL_TEXT, false);
        y += 20;

        g.fill(px + layout.s(10), y, px + panelW - layout.s(10), y + 1, COL_DIVIDER);
        y += 8;

        g.drawString(font, "Can use wands: " + (selectedType.canUseWand() ? "Yes" : "No"),
                x, y, selectedType.canUseWand() ? COL_GREEN : COL_RED, false);
        y += 12;
        g.drawString(font, "Magic source: " + selectedType.getMagicSource().getDisplayName(), x, y, COL_TEXT, false);
        y += 16;

        if (!selectedSubtype.getTags().isEmpty()) {
            g.drawString(font, "Traits: " + String.join(", ", selectedSubtype.getTags()), x, y, COL_DIM, false);
        }

        g.drawCenteredString(font, "This choice is permanent!", px + panelW / 2, py + panelH - layout.s(52), COL_RED);
    }

    private static Identifier resolveTypeIcon(Heritage type) {
        Identifier icon = Identifier.fromNamespaceAndPath(
                WizardsAndBeastsMod.MODID, "textures/gui/type_icons/" + type.getId() + ".png");
        Minecraft mc = Minecraft.getInstance();
        if (mc.getResourceManager().getResource(icon).isPresent()) {
            return icon;
        }
        return ICON_FALLBACK;
    }

    private static void drawTypeIcon(GuiGraphics g, Heritage type, int x, int y, ScreenLayoutScaler layout) {
        int size = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.TYPE_ICON_SIZE);
        g.fill(x - 3, y - 3, x + size + 3, y + size + 3, 0xAA2E2012);
        drawOutline(g, x - 3, y - 3, size + 6, size + 6, COL_ACCENT, COL_BORDER_DARK);
        g.blit(RenderPipelines.GUI_TEXTURED, resolveTypeIcon(type), x, y, 0.0F, 0.0F, size, size,
                ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE,
                ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
    }

    private static void drawFrame(GuiGraphics g, int x, int y, int w, int h) {
        McStylePanel.drawTexturedPanel(g, x, y, w, h);
    }

    private static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int topLeft, int bottomRight) {
        g.fill(x, y, x + w, y + 1, topLeft);
        g.fill(x, y, x + 1, y + h, topLeft);
        g.fill(x, y + h - 1, x + w, y + h, bottomRight);
        g.fill(x + w - 1, y, x + w, y + h, bottomRight);
    }

    private static String normalizeName(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.length() == 1) return raw.toUpperCase();
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
