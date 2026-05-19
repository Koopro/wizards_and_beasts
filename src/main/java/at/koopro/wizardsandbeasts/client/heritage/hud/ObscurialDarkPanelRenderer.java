package at.koopro.wizardsandbeasts.client.heritage.hud;

import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.ui.ObscurialUiModel;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

public final class ObscurialDarkPanelRenderer {
    private ObscurialDarkPanelRenderer() {}

    public static void render(GuiGraphics graphics, Minecraft mc, ObscurialUiModel model) {
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int boxW = 192;
        int boxH = 58;
        int x = (screenW - boxW) / 2;
        int y = screenH - 70;
        int barX = x + 8;
        int barW = boxW - 16;

        graphics.fill(x, y, x + boxW, y + boxH, ObscurialHudTheme.PANEL_BG);
        graphics.drawString(mc.font, "OBSCURUS FORM", x + 8, y + 7, ObscurialHudTheme.TITLE, true);

        int controlY = y + 20;
        drawBar(graphics, mc, "Control", model.controlRatio(), barX, barW, controlY, ObscurialHudTheme.CONTROL_FILL);

        int pressureY = y + 36;
        drawBar(graphics, mc, "Obscurus Pressure", model.pressureRatio(), barX, barW, pressureY, ObscurialHudTheme.PRESSURE_FILL);

        float secondsRemaining = estimateSecondsRemaining(mc.player, model.control(), model.pressure());
        String readout = String.format("%s  %.1fs left  Cntrl %.0f%%  Press %.0f%%",
                model.controlTier(), secondsRemaining, model.control(), model.pressure());
        graphics.drawString(mc.font, readout, barX, y + 49, tierColor(model.controlTier()), false);
        renderAbilityQuickbar(graphics, mc, x + boxW + 6, y + 6);
        ObscurialStatusBadgeRenderer.renderDarkFormBadges(graphics, mc, model, x, boxW, y);
    }

    private static void renderAbilityQuickbar(GuiGraphics graphics, Minecraft mc, int x, int y) {
        int panelW = 122;
        int panelH = 58;
        graphics.fill(x, y, x + panelW, y + panelH, 0x82120B19);
        graphics.drawString(mc.font, "Abilities", x + 6, y + 6, ObscurialHudTheme.TITLE, false);
        renderAbilityLine(graphics, mc, x + 6, y + 20, "N", ObscurialAbility.SURGE);
        renderAbilityLine(graphics, mc, x + 6, y + 34, "M", ObscurialAbility.GRASP);
    }

    private static void renderAbilityLine(GuiGraphics graphics, Minecraft mc, int x, int y, String key, ObscurialAbility ability) {
        var spellData = ClientSpellDataState.get();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean onCooldown = spellData.isOnCooldown(ability.spellId(), now);
        String status;
        int color;
        if (onCooldown) {
            float sec = Math.max(0f, (spellData.getCooldownExpiry(ability.spellId()) - now) / 20f);
            status = String.format("%.1fs", sec);
            color = ObscurialHudTheme.WARN;
        } else {
            status = "Ready";
            color = ObscurialHudTheme.POSITIVE;
        }
        graphics.drawString(mc.font, "[" + key + "] " + ability.displayName(), x, y, ObscurialHudTheme.LABEL, false);
        graphics.drawString(mc.font, status, x + 76, y, color, false);
    }

    private static void drawBar(GuiGraphics graphics, Minecraft mc, String label, float ratio, int x, int width, int y, int fillColor) {
        int barH = 7;
        float clamped = Math.max(0f, Math.min(1f, ratio));
        graphics.drawString(mc.font, label, x, y - 8, ObscurialHudTheme.LABEL, false);
        graphics.fill(x, y, x + width, y + barH, ObscurialHudTheme.TRACK);
        graphics.fill(x, y, x + (int) (width * clamped), y + barH, fillColor);
    }

    private static int tierColor(String tier) {
        return switch (tier) {
            case "Catastrophic" -> ObscurialHudTheme.DANGER;
            case "Fracturing" -> ObscurialHudTheme.WARN;
            default -> ObscurialHudTheme.POSITIVE;
        };
    }

    private static float estimateSecondsRemaining(Player player, float control, float pressure) {
        long dayTime = player.level().getDayTime() % 24000L;
        boolean strained = dayTime >= 0L && dayTime < 12300L && player.level().canSeeSky(player.blockPosition());
        float controlCost = 0.22f + (strained ? 0.30f : 0.0f) + (player.getAbilities().flying ? 0.14f : 0.0f);
        float pressureCost = 0.28f + (strained ? 0.24f : 0.0f) + (player.getAbilities().flying ? 0.12f : 0.0f);
        float limitingTicks = Math.min(control / Math.max(0.0001f, controlCost), pressure / Math.max(0.0001f, pressureCost));
        return Math.max(0.0f, limitingTicks / 20.0f);
    }
}
