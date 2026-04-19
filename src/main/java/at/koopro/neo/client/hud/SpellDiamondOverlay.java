package at.koopro.neo.client.hud;

import at.koopro.neo.Neo;
import at.koopro.neo.data.PlayerSpellData;
import at.koopro.neo.network.ClientSpellDataHolder;
import at.koopro.neo.spell.Spell;
import at.koopro.neo.spell.Spells;
import at.koopro.neo.util.GuiUtils;
import at.koopro.neo.util.TextUtils;
import at.koopro.neo.util.WandHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public class SpellDiamondOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Neo.MODID, "spell_diamond");

    private static final int SLOT_SIZE = 24;
    private static final int DIAMOND_SPACING = 28;
    private static final int SLOT_BG = 0x88000000;
    private static final int SLOT_ACTIVE = 0xAAFFD700;
    private static final int SLOT_EMPTY_TEXT = 0x66FFFFFF;
    private static final int SPELL_TEXT = 0xFFFFFFFF;

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean holdingWand = WandHelper.isHoldingWand(mc.player);
        if (!holdingWand) return;

        PlayerSpellData data = ClientSpellDataHolder.get();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int centerX = screenWidth / 2 + 110;
        int centerY = screenHeight - 45;

        int[][] offsets = {
                {0, -DIAMOND_SPACING},
                {DIAMOND_SPACING, 0},
                {0, DIAMOND_SPACING},
                {-DIAMOND_SPACING, 0}
        };

        for (int i = 0; i < 4; i++) {
            int slotX = centerX + offsets[i][0] - SLOT_SIZE / 2;
            int slotY = centerY + offsets[i][1] - SLOT_SIZE / 2;

            boolean active = data.getActiveSlot() == i;
            int bgColor = active ? SLOT_ACTIVE : SLOT_BG;
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bgColor);

            int borderColor = active ? 0xFFFFD700 : 0xFF444444;
            GuiUtils.drawBorder(graphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, borderColor);

            String spellId = data.getLoadoutSpell(i);
            if (spellId != null) {
                Spell spell = Spells.byId(spellId);
                if (spell != null) {
                    String abbr = TextUtils.abbreviate(spell.getDisplayName(), 4);
                    GuiUtils.drawCenteredInRect(graphics, mc.font, abbr,
                            slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                            spell.getCategory().getColor());
                }
            } else {
                GuiUtils.drawCenteredInRect(graphics, mc.font, "?",
                        slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                        SLOT_EMPTY_TEXT);
            }
        }

        String activeSpellId = data.getActiveSpellId();
        if (activeSpellId != null) {
            Spell activeSpell = Spells.byId(activeSpellId);
            if (activeSpell != null) {
                String name = activeSpell.getDisplayName();
                int textWidth = mc.font.width(name);
                graphics.drawString(mc.font, name,
                        centerX - textWidth / 2,
                        centerY + DIAMOND_SPACING + SLOT_SIZE / 2 + 4,
                        SPELL_TEXT, true);
            }
        }
    }
}
