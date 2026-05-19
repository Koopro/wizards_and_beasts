package at.koopro.wizardsandbeasts.client.heritage.hud;

import at.koopro.wizardsandbeasts.client.ui.ObscurialUiModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class ObscurialStatusBadgeRenderer {
    private ObscurialStatusBadgeRenderer() {}

    public static void renderDarkFormBadges(GuiGraphics graphics, Minecraft mc, ObscurialUiModel model, int boxX, int boxWidth, int boxY) {
        if (model.rageActive()) {
            graphics.drawString(mc.font, "Rage Threshold", boxX + boxWidth - 86, boxY + 7, ObscurialHudTheme.EMPHASIS, false);
        }
        if (model.controlTier().equals("Catastrophic")) {
            graphics.drawString(mc.font, "Collapse Risk", boxX + boxWidth - 76, boxY + 28, ObscurialHudTheme.DANGER, false);
        }

        long remainingTicks = Math.max(0L, model.lockoutUntilTick() - mc.level.getGameTime());
        if (remainingTicks > 0L) {
            int sec = (int) Math.ceil(remainingTicks / 20.0);
            graphics.drawString(mc.font, "Lockout " + sec + "s", boxX + boxWidth - 68, boxY + 18, ObscurialHudTheme.DANGER, false);
        }
    }

    public static void renderHumanFormBadges(GuiGraphics graphics, Minecraft mc, ObscurialUiModel model, int x, int y) {
        long now = mc.level.getGameTime();
        if (model.ventCooldownUntilTick() > now) {
            long sec = Math.max(1L, (model.ventCooldownUntilTick() - now) / 20L);
            graphics.drawString(mc.font, "Vent CD " + sec + "s", x + 8, y + 24, ObscurialHudTheme.AUXILIARY, false);
        }
        if (model.stressTier().equals("Volatile")) {
            graphics.drawString(mc.font, "Volatile: Transform Risk", x + 8, y + 12, ObscurialHudTheme.DANGER, false);
        }
    }
}
