package at.koopro.wizardsandbeasts.client.gui.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Util;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Staggered ink-reveal for config screens: each registered widget becomes visible after
 * {@code staggerIndex * staggerDelayMs} milliseconds from the reveal clock start. Elements
 * that are not yet revealed are fully absent (visible = false) — no fade, no partial draw.
 * Manually drawn (non-widget) elements can share the same clock via {@link #isRevealed}.
 */
public final class InkRevealRenderer {
    private final List<Entry> entries = new ArrayList<>();
    private long openTimestamp;

    private record Entry(AbstractWidget widget, int staggerIndex, int staggerDelayMs) {}

    public InkRevealRenderer() {
        start();
    }

    /** Captures the reveal clock origin; call from {@code init()}. */
    public void start() {
        this.openTimestamp = Util.getMillis();
    }

    /** Clears registered widgets and restarts the clock for screen re-open or resize. */
    public void reset() {
        entries.clear();
        start();
    }

    public void register(AbstractWidget widget, int staggerIndex, int staggerDelayMs) {
        entries.add(new Entry(widget, staggerIndex, staggerDelayMs));
        widget.visible = isRevealed(staggerIndex, staggerDelayMs);
    }

    /** Call once per frame before widgets render to flip visibility as reveal times pass. */
    public void update() {
        for (Entry entry : entries) {
            entry.widget().visible = isRevealed(entry.staggerIndex(), entry.staggerDelayMs());
        }
    }

    public boolean isRevealed(int staggerIndex, int staggerDelayMs) {
        return Util.getMillis() - openTimestamp >= (long) staggerIndex * staggerDelayMs;
    }
}
