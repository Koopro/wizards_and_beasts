package at.koopro.wizardsandbeasts.client.spell.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.ModTextures;
import at.koopro.wizardsandbeasts.client.spell.ui.SpellHudUiModel;
import at.koopro.wizardsandbeasts.client.ui.UiStateProjection;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class SpellDiamondOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_diamond");
    private static final int HUD_TEX_SIZE = 256;
    private static final int HUD_ON_SCREEN_SIZE = 96;
    private static final int ICON_TEX_SIZE = 92;
    private static final int EDGE_MARGIN = 10;

    private static final int[][] SLOT_CENTERS = {
            {128, 80},
            {176, 128},
            {128, 176},
            {80, 128}
    };

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        SpellHudUiModel spellHudUi = UiStateProjection.spellHud(mc);
        if (!spellHudUi.canRenderSpellHud()) return;
        var data = spellHudUi.spellData();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int hudX = screenWidth - HUD_ON_SCREEN_SIZE - EDGE_MARGIN;
        int hudY = screenHeight - HUD_ON_SCREEN_SIZE - EDGE_MARGIN;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ModTextures.WAND_HUD_BG,
                hudX,
                hudY,
                0.0F,
                0.0F,
                HUD_ON_SCREEN_SIZE,
                HUD_ON_SCREEN_SIZE,
                HUD_TEX_SIZE,
                HUD_TEX_SIZE,
                HUD_TEX_SIZE,
                HUD_TEX_SIZE);

        for (int i = 0; i < 4; i++) {
            String spellId = data.getLoadoutSpell(i);
            if (spellId != null) {
                int iconSize = scalePx(ICON_TEX_SIZE);
                int iconX = hudX + scalePx(SLOT_CENTERS[i][0]) - iconSize / 2;
                int iconY = hudY + scalePx(SLOT_CENTERS[i][1]) - iconSize / 2;
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        ModTextures.resolveWandHudSpellIcon(mc.getResourceManager(), spellId),
                        iconX,
                        iconY,
                        0.0F,
                        0.0F,
                        iconSize,
                        iconSize,
                        ICON_TEX_SIZE,
                        ICON_TEX_SIZE,
                        ICON_TEX_SIZE,
                        ICON_TEX_SIZE);
            }
        }

        int activeSlot = Mth.clamp(data.getActiveSlot(), 0, 3);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ModTextures.WAND_HUD_OVERLAY,
                hudX,
                hudY,
                0.0F,
                0.0F,
                HUD_ON_SCREEN_SIZE,
                HUD_ON_SCREEN_SIZE,
                HUD_TEX_SIZE,
                HUD_TEX_SIZE,
                HUD_TEX_SIZE,
                HUD_TEX_SIZE);

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ModTextures.resolveWandHudSelectedOverlay(mc.getResourceManager(), activeSlot),
                hudX,
                hudY,
                0.0F,
                0.0F,
                HUD_ON_SCREEN_SIZE,
                HUD_ON_SCREEN_SIZE,
                HUD_TEX_SIZE,
                HUD_TEX_SIZE,
                HUD_TEX_SIZE,
                HUD_TEX_SIZE);

        String activeSpellId = data.getLoadoutSpell(activeSlot);
        if (activeSpellId != null) {
            Spell activeSpell = Spells.byId(activeSpellId);
            if (activeSpell != null) {
                String name = clampTextToWidth(mc.font, activeSpell.getDisplayName(), HUD_ON_SCREEN_SIZE + 24);
                int textWidth = mc.font.width(name);
                int textX = Mth.clamp(
                        hudX + HUD_ON_SCREEN_SIZE / 2 - textWidth / 2,
                        EDGE_MARGIN,
                        Math.max(EDGE_MARGIN, screenWidth - EDGE_MARGIN - textWidth));
                graphics.drawString(mc.font, name,
                        textX,
                        hudY - 11,
                        0xFFE8D2B4, true);
            }
            if (ModuleManager.isEnabled(Module.PROFICIENCY)) {
                renderProficiencyPips(graphics, hudX, hudY, data.getSpellProficiency(activeSpellId));
            }
        }
    }

    private static void renderProficiencyPips(GuiGraphics graphics, int hudX, int hudY, float proficiency) {
        int pipSize = 4;
        int gap = 2;
        int totalWidth = 28;
        int startX = hudX + (HUD_ON_SCREEN_SIZE / 2) - (totalWidth / 2);
        int y = hudY + HUD_ON_SCREEN_SIZE + 2;
        int filled = proficiency >= 1.0f ? 5 : Math.max(0, Math.min(4, (int) Math.floor(proficiency * 5.0f)));
        for (int i = 0; i < 5; i++) {
            int x = startX + (i * (pipSize + gap));
            int color = i < filled ? 0xFFD700 : 0x444444;
            graphics.fill(x, y, x + pipSize, y + pipSize, color);
        }
    }

    private static int scalePx(int sourcePx) {
        return Math.max(1, Math.round((sourcePx / (float) HUD_TEX_SIZE) * HUD_ON_SCREEN_SIZE));
    }

    private static String clampTextToWidth(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(8, maxWidth - font.width("..."))) + "...";
    }
}
