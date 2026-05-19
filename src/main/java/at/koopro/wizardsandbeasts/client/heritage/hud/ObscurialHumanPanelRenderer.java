package at.koopro.wizardsandbeasts.client.heritage.hud;

import at.koopro.wizardsandbeasts.client.ui.ObscurialUiModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;

public final class ObscurialHumanPanelRenderer {
    private ObscurialHumanPanelRenderer() {}

    public static void render(GuiGraphics graphics, Minecraft mc, ObscurialUiModel model) {
        int screenW = mc.getWindow().getGuiScaledWidth();

        int hudW = ObscurialStressMeterLayout.HUD_DEST_W;
        int hudH = Math.round(hudW * (float) ObscurialStressMeterLayout.TEX_H / ObscurialStressMeterLayout.TEX_W);
        int hudX = screenW - hudW - 10;
        int hudY = 10;

        float scale = hudW / (float) ObscurialStressMeterLayout.TEX_W;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ObscurialStressMeterLayout.TEXTURE,
                hudX,
                hudY,
                0.0F,
                0.0F,
                hudW,
                hudH,
                ObscurialStressMeterLayout.TEX_W,
                ObscurialStressMeterLayout.TEX_H,
                ObscurialStressMeterLayout.TEX_W,
                ObscurialStressMeterLayout.TEX_H);

        if (mc.player instanceof AbstractClientPlayer clientPlayer) {
            int skinX = hudX + Math.round(ObscurialStressMeterLayout.SKIN_SRC_X * scale);
            int skinY = hudY + Math.round(ObscurialStressMeterLayout.SKIN_SRC_Y * scale);
            int skinW = Math.round(ObscurialStressMeterLayout.SKIN_SRC_W * scale);
            int skinH = Math.round(ObscurialStressMeterLayout.SKIN_SRC_H * scale);
            ObscurialStressMeterSkinRenderer.renderFrontPortrait(graphics, clientPlayer, skinX, skinY, skinW, skinH);
        }

        int barX = hudX + Math.round(ObscurialStressMeterLayout.BAR_SRC_X * scale);
        int barY = hudY + Math.round(ObscurialStressMeterLayout.BAR_SRC_Y * scale);
        int barW = Math.round(ObscurialStressMeterLayout.BAR_SRC_W * scale);
        int barH = Math.round(ObscurialStressMeterLayout.BAR_SRC_H * scale);

        float ratio = Math.max(0.0f, Math.min(1.0f, model.stressRatio()));
        int fillW = Math.max(0, Math.round(barW * ratio));

        if (fillW > 0) {
            graphics.enableScissor(barX, barY, barX + fillW, barY + barH);
            graphics.fill(barX, barY, barX + barW, barY + barH, ObscurialHudTheme.STRESS_FILL_OVERLAY);
            graphics.disableScissor();
        }

        int titleX = hudX + Math.round(50.0f * scale);
        graphics.drawString(mc.font, "OBSCURUS STRAIN", titleX, hudY + 2, ObscurialHudTheme.TITLE, true);

        graphics.drawString(mc.font, String.format("%s  %.0f%%", model.stressTier(), model.stress()),
                hudX + hudW - 78, hudY + 4, tierColor(model.stressTier()), false);

        ObscurialStatusBadgeRenderer.renderHumanFormBadges(graphics, mc, model, hudX, hudY + hudH + 2);
    }

    private static int tierColor(String tier) {
        return switch (tier) {
            case "Volatile" -> ObscurialHudTheme.DANGER;
            case "Agitated" -> ObscurialHudTheme.WARN;
            default -> ObscurialHudTheme.POSITIVE;
        };
    }
}
