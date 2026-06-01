package at.koopro.wizardsandbeasts.client.hud.stats;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.client.stats.ClientStatsState;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class StatHudOverlay {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "stat_hud");

    private static boolean visible = false;

    private static final int PANEL_BG = 0x88000000;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int POWER_COLOR = 0xFFB8A0FF;
    private static final int PRODIGY_COLOR = 0xFFFFD700;
    private static final int PADDING = 4;
    private static final int ROW_HEIGHT = 10;

    private StatHudOverlay() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.PLAYER_STATS)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (SpellKeyBindings.TOGGLE_STAT_HUD.consumeClick()) {
            visible = !visible;
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!visible) return;
        if (!ModuleManager.isEnabled(Module.PLAYER_STATS)) return;
        if (!ClientStatsState.hasData()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        PlayerStatsData data = ClientStatsState.get();
        if (data == null) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Build rows — power row is drawn in two segments so the prodigy star gets its own colour.
        String powerBase = "Power: " + data.power();
        String precRow   = "Precision: " + data.precision();
        String willRow   = "Willpower: " + data.willpower();
        String reflRow   = "Reflexes: " + data.reflexes();
        String knowRow   = "Knowledge: 0"; // TODO: derive from registries

        String[] rows = { powerBase + (data.isProdigy() ? " ☆" : ""), precRow, willRow, reflRow, knowRow };

        int maxWidth = 0;
        for (String row : rows) {
            int w = font.width(row);
            if (w > maxWidth) maxWidth = w;
        }

        int panelW = maxWidth + PADDING * 2;
        int panelH = rows.length * ROW_HEIGHT + PADDING * 2;
        int panelX = screenWidth - panelW - 4;
        int panelY = 4;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);

        for (int i = 0; i < rows.length; i++) {
            int x = panelX + PADDING;
            int y = panelY + PADDING + i * ROW_HEIGHT;
            if (i == 0) {
                // Power base in accent colour, then star in gold over it
                graphics.drawString(font, powerBase, x, y, POWER_COLOR, false);
                if (data.isProdigy()) {
                    graphics.drawString(font, " ☆", x + font.width(powerBase), y, PRODIGY_COLOR, false);
                }
            } else {
                graphics.drawString(font, rows[i], x, y, TEXT_COLOR, false);
            }
        }
    }
}
