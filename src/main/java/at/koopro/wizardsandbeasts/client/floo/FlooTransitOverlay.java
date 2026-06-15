package at.koopro.wizardsandbeasts.client.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Short fullscreen overlay for Floo transit: an emerald swirl with grate silhouettes
 * flashing past, sold as the dizzy spin of the journey. Time-driven (no tick handler) —
 * {@link #trigger(int)} stamps a start/end, {@link #render} interpolates from wall-clock.
 */
public final class FlooTransitOverlay {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_transit");

    private static long startMs = 0L;
    private static long endMs = 0L;

    private FlooTransitOverlay() {
    }

    /** Begins the overlay for the given duration in ticks (50 ms each). */
    public static void trigger(int durationTicks) {
        startMs = System.currentTimeMillis();
        endMs = startMs + durationTicks * 50L;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        long now = System.currentTimeMillis();
        if (now >= endMs) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float progress = (float) (now - startMs) / Math.max(1L, endMs - startMs);
        progress = Mth.clamp(progress, 0f, 1f);

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // Envelope: quick ramp in, hold, fade out.
        float env = progress < 0.2f ? progress / 0.2f
                : progress > 0.7f ? (1f - progress) / 0.3f : 1f;
        env = Mth.clamp(env, 0f, 1f);

        // Emerald wash over the whole screen.
        int washAlpha = (int) (env * 110);
        graphics.fill(0, 0, w, h, (washAlpha << 24) | 0x0E5A22);

        // Spinning grate silhouettes flashing past the periphery.
        int cx = w / 2;
        int cy = h / 2;
        float spin = progress * Mth.TWO_PI * 3.0f;
        int grates = 6;
        int radius = (int) (Math.min(w, h) * (0.20f + progress * 0.28f));
        int gw = Math.max(10, w / 22);
        int gh = gw * 3 / 2;
        for (int i = 0; i < grates; i++) {
            float ang = spin + (Mth.TWO_PI / grates) * i;
            int gx = cx + (int) (Math.cos(ang) * radius);
            int gy = cy + (int) (Math.sin(ang) * radius);
            int a = (int) (env * 150);
            // Dark grate body with a green-lit mouth.
            graphics.fill(gx - gw / 2, gy - gh / 2, gx + gw / 2, gy + gh / 2, (a << 24) | 0x101418);
            graphics.fill(gx - gw / 2 + 2, gy - gh / 2 + 2, gx + gw / 2 - 2, gy + gh / 2 - 2,
                    ((int) (env * 120) << 24) | 0x21B342);
        }

        // Center darkening to push the periphery flashes (tunnel feel).
        int coreAlpha = (int) (env * 60);
        graphics.fill(0, 0, w, h, (coreAlpha << 24));
    }
}
