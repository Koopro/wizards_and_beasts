package at.koopro.wizardsandbeasts.client.hud.stats;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.character.StatStyle;
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
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The toggleable corner readout of the five stats.
 *
 * <p>Rows are built from {@link PlayerStat#values()} and named through their own lang keys. They used
 * to be five concatenated English literals, which meant the HUD said "Power" in every language and a
 * sixth stat would simply not have appeared here.
 */
public final class StatHudOverlay {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "stat_hud");

    private static boolean visible = false;

    /**
     * 2026-11-18: a parchment slip, each stat in its {@link StatStyle} ink with a swatch, as on the sheet's Measure.
     * The old panel was a translucent black box with grey text and only Power coloured (lilac).
     */
    private static final int PRODIGY_COLOR = WizardsPalette.GILT_DARK;
    /** Training percentage: dimmer than the stat, which is the number that actually matters. */
    private static final int TRAINING_COLOR = WizardsPalette.PAGE_INK_3;
    /** Inside the slip's torn edge. */
    private static final int PADDING = 7;
    private static final int ROW_HEIGHT = 10;
    private static final int SWATCH = 4;
    private static final int SWATCH_GAP = 3;

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

        PlayerStat[] stats = PlayerStat.values();
        String[] rows = new String[stats.length];
        String[] suffixes = new String[stats.length];
        int maxWidth = 0;
        for (int i = 0; i < stats.length; i++) {
            PlayerStat stat = stats[i];
            rows[i] = Component.translatable("gui.wizards_and_beasts.stat_hud.row",
                    stat.displayName(), data.get(stat)).getString();
            // The training fraction rides along as a dim suffix rather than a second row: it is the
            // only thing that moves between two spell hits, and without it the panel looks frozen.
            suffixes[i] = suffixFor(stat, data);
            maxWidth = Math.max(maxWidth, font.width(rows[i]) + font.width(suffixes[i]));
        }

        int textX0 = PADDING + SWATCH + SWATCH_GAP;
        int panelW = maxWidth + textX0 + PADDING;
        int panelH = rows.length * ROW_HEIGHT + PADDING * 2 - 2;
        int panelX = screenWidth - panelW - 4;
        int panelY = 4;

        McStylePanel.drawThemedSlip(graphics, panelX, panelY, panelW, panelH);

        for (int i = 0; i < rows.length; i++) {
            int x = panelX + PADDING;
            int y = panelY + PADDING + i * ROW_HEIGHT;
            int ink = StatStyle.ink(stats[i]);
            graphics.fill(x, y + 2, x + SWATCH, y + 2 + SWATCH, ink);
            int tx = panelX + textX0;
            graphics.drawString(font, rows[i], tx, y, ink, false);
            if (!suffixes[i].isEmpty()) {
                int suffixColour = stats[i] == PlayerStat.POWER ? PRODIGY_COLOR : TRAINING_COLOR;
                graphics.drawString(font, suffixes[i], tx + font.width(rows[i]), y, suffixColour, false);
            }
        }
    }

    /** The prodigy star on POWER, the training percentage on a trainable stat, nothing otherwise. */
    private static String suffixFor(PlayerStat stat, PlayerStatsData data) {
        if (stat == PlayerStat.POWER) {
            return data.isProdigy() ? " ☆" : "";
        }
        if (!stat.isTrainable()) return "";
        int percent = Math.round(Mth.clamp(ClientStatsState.trainingProgress(stat), 0f, 1f) * 100f);
        return Component.translatable("gui.wizards_and_beasts.stat_hud.training", percent).getString();
    }
}
