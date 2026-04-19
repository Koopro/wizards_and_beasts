package at.koopro.neo.client.gui;

import at.koopro.neo.type.WizSubtype;
import at.koopro.neo.type.WizType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

/**
 * Drawing for {@link TypeSelectionScreen} (heritage / subtype / confirm phases).
 */
public final class TypeSelectionRenderHelper {

    public static final int COL_BG = 0xDD1E1610;
    public static final int COL_BORDER = 0xFF6B5B4E;
    public static final int COL_DIVIDER = 0xFF4A3F35;
    public static final int COL_GOLD = 0xFFFFD700;
    public static final int COL_PARCHMENT = 0xFFE8D5B3;
    public static final int COL_DIM = 0xFF998877;
    public static final int COL_GREEN = 0xFF88FF88;
    public static final int COL_RED = 0xFFFF6666;

    private TypeSelectionRenderHelper() {}

    public static void renderTypeList(GuiGraphics g, Font font, int px, int py, int panelW, int panelH,
                                      int mouseX, int mouseY) {
        g.drawCenteredString(font, "Choose Your Heritage", px + panelW / 2, py + 8, COL_GOLD);
        g.fill(px + 10, py + 22, px + panelW - 10, py + 23, COL_DIVIDER);

        WizType[] types = WizType.values();
        int listY = py + 30;
        for (int i = 0; i < types.length; i++) {
            WizType type = types[i];
            int btnY = listY + i * 24;
            int btnH = 22;

            String src = type.getMagicSource().getDisplayName();
            int srcW = font.width(src);
            g.drawString(font, src, px + panelW - 16 - srcW, btnY + 6, COL_DIM, false);

            if (mouseX >= px + 10 && mouseX <= px + panelW - 10
                    && mouseY >= btnY && mouseY <= btnY + btnH) {
                String desc = type.getDescription();
                int tipW = font.width(desc) + 8;
                int tipX = mouseX + 12;
                int tipY = mouseY - 14;
                g.fill(tipX - 2, tipY - 2, tipX + tipW, tipY + 12, 0xEE1A1A2E);
                g.fill(tipX - 3, tipY - 3, tipX + tipW + 1, tipY - 2, COL_BORDER);
                g.fill(tipX - 3, tipY + 12, tipX + tipW + 1, tipY + 13, COL_BORDER);
                g.drawString(font, desc, tipX + 2, tipY, COL_PARCHMENT, false);
            }
        }
    }

    public static void renderSubtypeList(GuiGraphics g, Font font, int px, int py, int panelW,
                                        @Nullable WizType selectedType) {
        if (selectedType == null) return;

        g.drawCenteredString(font, selectedType.getDisplayName(), px + panelW / 2, py + 8, COL_GOLD);
        g.fill(px + 10, py + 22, px + panelW - 10, py + 23, COL_DIVIDER);

        int infoY = py + 28;
        g.drawString(font, selectedType.getDescription(), px + 12, infoY, COL_PARCHMENT, false);

        g.drawString(font, String.format("Base: HP %+.0f  SPD %+.3f  ARM %+.0f",
                        selectedType.getBaseHealth(), selectedType.getBaseSpeed(), selectedType.getBaseArmor()),
                px + 12, infoY + 14, COL_DIM, false);

        String info = "Wand: " + (selectedType.canUseWand() ? "Yes" : "No")
                + "  |  Magic: " + selectedType.getMagicSource().getDisplayName()
                + "  |  Size: " + selectedType.getSizeCategory().getDisplayName();
        g.drawString(font, info, px + 12, infoY + 26, COL_DIM, false);

        g.fill(px + 10, py + 82, px + panelW - 10, py + 83, COL_DIVIDER);

        g.drawString(font, "Choose Subtype:", px + 12, py + 86, COL_PARCHMENT, false);

        var subtypes = selectedType.getSubtypes();
        int listY = py + 100;
        for (int i = 0; i < subtypes.size(); i++) {
            WizSubtype sub = subtypes.get(i);
            int y = listY + i * 24 + 6;
            if (!sub.getTags().isEmpty()) {
                String tags = String.join(", ", sub.getTags());
                String tagStr = tags.length() > 30 ? tags.substring(0, 27) + "..." : tags;
                g.drawString(font, tagStr, px + panelW - 10 - font.width(tagStr), y, COL_DIM, false);
            }
        }
    }

    public static void renderConfirmation(GuiGraphics g, Font font, int px, int py, int panelW, int panelH,
                                          @Nullable WizType selectedType, @Nullable WizSubtype selectedSubtype) {
        if (selectedType == null || selectedSubtype == null) return;

        g.drawCenteredString(font, "Confirm Your Choice", px + panelW / 2, py + 8, COL_GOLD);
        g.fill(px + 10, py + 22, px + panelW - 10, py + 23, COL_DIVIDER);

        int y = py + 30;
        int x = px + 20;

        g.drawString(font, selectedType.getDisplayName() + " - " + selectedSubtype.getDisplayName(),
                x, y, selectedType.getColor(), false);
        y += 16;

        g.drawString(font, selectedType.getDescription(), x, y, COL_PARCHMENT, false);
        y += 12;
        g.drawString(font, selectedSubtype.getDescription(), x, y, COL_DIM, false);
        y += 20;

        g.fill(px + 10, y, px + panelW - 10, y + 1, COL_DIVIDER);
        y += 8;

        g.drawString(font, "Stat Preview:", x, y, COL_GOLD, false);
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
                totalArm, totalArm), x, y, totalArm > 0 ? COL_GREEN : COL_PARCHMENT, false);
        y += 12;
        g.drawString(font, "Size: " + selectedType.getSizeCategory().getDisplayName(),
                x, y, COL_PARCHMENT, false);
        y += 20;

        g.fill(px + 10, y, px + panelW - 10, y + 1, COL_DIVIDER);
        y += 8;

        g.drawString(font, "Can use wands: " + (selectedType.canUseWand() ? "Yes" : "No"),
                x, y, selectedType.canUseWand() ? COL_GREEN : COL_RED, false);
        y += 12;
        g.drawString(font, "Magic source: " + selectedType.getMagicSource().getDisplayName(),
                x, y, COL_PARCHMENT, false);
        y += 16;

        if (!selectedSubtype.getTags().isEmpty()) {
            g.drawString(font, "Traits: " + String.join(", ", selectedSubtype.getTags()),
                    x, y, COL_DIM, false);
        }

        g.drawCenteredString(font, "This choice is permanent!",
                px + panelW / 2, py + panelH - 48, COL_RED);
    }
}
