package at.koopro.wizardsandbeasts.visual.hud;

import java.util.Locale;

/**
 * Pure time math for HUD timers — a cooldown, a potion, the Elixir of Life's day — so the fill and the readout of a
 * duration bar can be tested without a screen. Ticks are game ticks (20 per second).
 */
public final class HudTime {

    private HudTime() {}

    /** Remaining share 0..1 of a {@code total}-tick timer with {@code remaining} ticks left. */
    public static float fraction(long remaining, long total) {
        if (total <= 0 || remaining <= 0) {
            return 0f;
        }
        return Math.min(1f, remaining / (float) total);
    }

    /**
     * A readout that stays short: {@code 45s} under a minute, {@code 3:07} under an hour, {@code 23:59:12} beyond —
     * whole seconds, rounded up so a timer never shows {@code 0s} while it is still running.
     */
    public static String readout(long remainingTicks) {
        if (remainingTicks <= 0) {
            return "0s";
        }
        long seconds = (remainingTicks + 19) / 20;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return String.format(Locale.ROOT, "%d:%02d", minutes, seconds % 60);
        }
        return String.format(Locale.ROOT, "%d:%02d:%02d", minutes / 60, minutes % 60, seconds % 60);
    }
}
