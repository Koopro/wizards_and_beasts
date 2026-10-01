package at.koopro.wizardsandbeasts.client.hud.widget;

import at.koopro.wizardsandbeasts.visual.hud.HudTime;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * One timed HUD row: a label, a bar that empties as the time runs out, and the time left. The building block for a
 * potion or elixir timer (the Philosopher's Stone's Elixir of Life lasts a day) and any later timed status — the
 * Control Center's HUD preview draws it with sample timers, so it is judged on screen before a live HUD uses it.
 *
 * <p>Stateless: every call takes the timer's current numbers. Colours are the caller's (a HUD sits on the world, not on
 * parchment, so it does not borrow the admin theme).
 */
public final class HudDurationBar {

    public static final int HEIGHT = 12;
    private static final int TRACK = 0xAA101014;
    private static final int TEXT = 0xFFE8E0D0;

    private HudDurationBar() {}

    /**
     * @param accent the bar's fill colour (ARGB)
     * @return the height drawn
     */
    public static int render(GuiGraphics g, Font font, int x, int y, int width, Component label, long remainingTicks,
                             long totalTicks, int accent) {
        String time = HudTime.readout(remainingTicks);
        int timeW = font.width(time);
        g.fill(x, y, x + width, y + HEIGHT, TRACK);
        int fill = Math.round((width - 2) * HudTime.fraction(remainingTicks, totalTicks));
        g.fill(x + 1, y + HEIGHT - 3, x + 1 + fill, y + HEIGHT - 1, accent | 0xFF000000);
        String name = font.plainSubstrByWidth(label.getString(), Math.max(0, width - timeW - 10));
        g.drawString(font, name, x + 3, y + 2, TEXT, true);
        g.drawString(font, time, x + width - 3 - timeW, y + 2, TEXT, true);
        return HEIGHT;
    }
}
