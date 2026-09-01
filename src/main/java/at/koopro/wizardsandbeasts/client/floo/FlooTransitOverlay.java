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

    /** Deep hearth green for the full-screen wash: {@code FlooCues.EMERALD} at a third value. */
    private static final int WASH_RGB = 0x0E5A22;

    /** The Floo green itself, as bare RGB — a GUI fill packs its own alpha in the top byte. */
    private static final int EDGE_RGB = at.koopro.wizardsandbeasts.floo.FlooCues.EMERALD & 0x00FFFFFF;

    private static long startMs = 0L;
    private static long endMs = 0L;
    private static long sootEndMs = 0L;
    private static long sootStartMs = 0L;

    private FlooTransitOverlay() {
    }

    /**
     * Begins the spin, and arms the soot that follows it.
     *
     * <p>The soot is scheduled from the same instant rather than started when the spin ends, so a
     * dropped frame or a stalled tick cannot leave a gap between them — the two phases are one
     * timeline read from one clock.
     */
    public static void trigger(int durationTicks, int sootTicks) {
        startMs = System.currentTimeMillis();
        endMs = startMs + durationTicks * 50L;
        sootStartMs = endMs;
        sootEndMs = endMs + sootTicks * 50L;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (at.koopro.wizardsandbeasts.Config.reduceScreenEffects) return;
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (now >= endMs) {
            renderSoot(graphics, mc, now);
            return;
        }

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
        graphics.fill(0, 0, w, h, (washAlpha << 24) | WASH_RGB);

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
                    ((int) (env * 120) << 24) | EDGE_RGB);
        }

        // Center darkening to push the periphery flashes (tunnel feel).
        int coreAlpha = (int) (env * 60);
        graphics.fill(0, 0, w, h, (coreAlpha << 24));
    }

    /**
     * Soot on the lens: a grey vignette that fades out over a couple of seconds.
     *
     * <p>Edges only, and no full-screen tint. A traveller arrives with ash on their face, not with a
     * grey filter over the world — darkening the middle would read as damage or as a screen effect
     * rather than as dirt, and the player needs to be able to see the room they have landed in
     * immediately.
     */
    private static void renderSoot(GuiGraphics graphics, Minecraft mc, long now) {
        if (now >= sootEndMs || now < sootStartMs) return;
        float remaining = (float) (sootEndMs - now) / Math.max(1L, sootEndMs - sootStartMs);
        float strength = Mth.clamp(remaining, 0f, 1f);

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int band = Math.max(10, Math.min(w, h) / 5);

        // Four bands, each fading independently towards the middle. Drawn as a handful of stacked
        // strips rather than a gradient texture, which is enough for something this short-lived.
        int steps = 5;
        for (int i = 0; i < steps; i++) {
            float t = 1.0f - (float) i / steps;
            int alpha = (int) (strength * t * 90);
            if (alpha <= 0) continue;
            int colour = (alpha << 24) | 0x14100D;
            int thickness = band * (i + 1) / steps - band * i / steps + 1;
            int top = band * i / steps;
            graphics.fill(0, top, w, top + thickness, colour);
            graphics.fill(0, h - top - thickness, w, h - top, colour);
            graphics.fill(top, 0, top + thickness, h, colour);
            graphics.fill(w - top - thickness, 0, w - top, h, colour);
        }
    }
}
