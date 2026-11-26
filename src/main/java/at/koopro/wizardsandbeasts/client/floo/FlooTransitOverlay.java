package at.koopro.wizardsandbeasts.client.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.hud.HudOverlays;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Floo journey, drawn over the screen (remade 2026-11-26; the owner wanted "something new and special" in place of
 * the green wash with boxes). Straight from the book: "He was spinning very fast now... the whirl of green flames made
 * him feel sick... he glimpsed blurred fireplaces and snatches of the rooms beyond... something hard knocked his
 * elbow... he fell, face forward, onto cold stone" (CoS ch. 4).
 *
 * <ol>
 *   <li><b>The flue</b> (the spin): a tunnel of the hearth's own green fire, rings of flame whirling round the middle
 *   of the screen and rushing outward past you, alternate rings turning against each other; other hearths' openings,
 *   with a lit room beyond each, fly out of the middle and past; one hard knock part way.</li>
 *   <li><b>The ash</b> (after): a moment's dark as you land face first, then soot drifting down the screen and the
 *   last of it at the top and bottom clearing.</li>
 * </ol>
 *
 * <p>Time-driven from the wall clock: {@link #trigger} stamps the phases, {@link #render} reads them; nothing ticks.
 * Everything is the animated fire strip, three glimpse sprites (tools/floo_journey_art.py) and gradients: no boxes.
 */
public final class FlooTransitOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_transit");

    private static final Identifier[] FIRE = {tex("floor_fire_0"), tex("floor_fire_1")};
    private static final Identifier[] GLIMPSE = {tex("glimpse_0"), tex("glimpse_1"), tex("glimpse_2")};
    private static final int FIRE_FRAMES = 32;
    private static final int GLIMPSE_W = 48, GLIMPSE_H = 32;
    /** Rings of flame in the tunnel at any moment. */
    private static final int RINGS = 7;
    /** Hearths glimpsed on the way past. */
    private static final int GLIMPSES = 5;
    /** The soot that drifts down after landing. */
    private static final int FLAKES = 170;

    private static long startMs;
    private static long endMs;
    private static long sootEndMs;
    private static long seed;

    private FlooTransitOverlay() {
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/floo/" + name + ".png");
    }

    /** Begins the journey, and arms the ash that follows it on the same clock (no gap between them). */
    public static void trigger(int durationTicks, int sootTicks) {
        startMs = System.currentTimeMillis();
        endMs = startMs + durationTicks * 50L;
        sootEndMs = endMs + sootTicks * 50L;
        seed = startMs;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (HudOverlays.overlayTarget() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        if (now < endMs) {
            renderFlue(graphics, w, h, Mth.clamp((now - startMs) / (float) Math.max(1L, endMs - startMs), 0.0f, 1.0f), now);
        } else if (now < sootEndMs) {
            renderAsh(graphics, w, h, (now - endMs) / (float) Math.max(1L, sootEndMs - endMs), now - endMs);
        }
    }

    // ── the flue ────────────────────────────────────────────────────────────

    private static void renderFlue(GuiGraphics g, int w, int h, float p, long now) {
        float in = Math.min(1.0f, p / 0.08f);
        float out = p > 0.9f ? (1.0f - p) / 0.1f : 1.0f;
        float env = Math.min(in, out);
        float cx = w / 2.0f, cy = h / 2.0f;
        float reach = (float) Math.hypot(cx, cy) * 1.15f;

        // The soot-black of the chimney, and a green light at the far end of it.
        g.fill(0, 0, w, h, argb(env * 0.92f, 0x0B0806));
        g.fillGradient(0, 0, w, h / 2, argb(env * 0.0f, 0x0B3A18), argb(env * 0.35f, 0x0F5A24));
        g.fillGradient(0, h / 2, w, h, argb(env * 0.35f, 0x0F5A24), argb(env * 0.0f, 0x0B3A18));

        // "Something hard knocked his elbow": one sharp jolt just past the middle.
        float knock = p > 0.55f && p < 0.62f ? (float) Math.sin((p - 0.55f) / 0.07f * Math.PI) : 0.0f;
        g.pose().pushMatrix();
        g.pose().translate(knock * 6.0f, knock * -3.0f);

        long frame = now / 50L;
        float spin = p * 14.0f;
        // Rings rushing outward: each ring's place along the tunnel loops, growing faster as it nears the screen
        // (perspective), its flames tilted to stream toward the far end, alternate rings turning against each other.
        for (int k = 0; k < RINGS; k++) {
            float along = (p * 2.2f + k / (float) RINGS) % 1.0f;
            float radius = reach * along * along;
            float size = 6.0f + radius * 0.32f;
            float alpha = env * Math.min(1.0f, along * 3.0f) * (along > 0.85f ? (1.0f - along) / 0.15f : 1.0f);
            if (alpha <= 0.02f || radius < 2.0f) {
                continue;
            }
            int count = Math.max(6, (int) (Mth.TWO_PI * radius / (size * 0.8f)));
            float turn = (k % 2 == 0 ? spin : -spin * 0.8f) + k * 0.7f;
            for (int i = 0; i < count; i++) {
                float a = turn + Mth.TWO_PI * i / count;
                g.pose().pushMatrix();
                g.pose().translate(cx + Mth.cos(a) * radius, cy + Mth.sin(a) * radius);
                g.pose().rotate(a - Mth.HALF_PI);
                int f = (int) Math.floorMod(frame + i * 5L + k * 11L, FIRE_FRAMES);
                g.blit(RenderPipelines.GUI_TEXTURED, FIRE[(i + k) & 1], Math.round(-size / 2), Math.round(-size / 2),
                        0.0f, f * 16.0f, Math.round(size), Math.round(size), 16, 16, 16, 16 * FIRE_FRAMES,
                        argb(alpha, 0xFFFFFF));
                g.pose().popMatrix();
            }
        }

        // Fireplaces glimpsed on the way past: out of the middle, along a turning path, growing as they near.
        for (int i = 0; i < GLIMPSES; i++) {
            float t = (p - (0.06f + i * 0.16f)) / 0.34f;
            if (t <= 0.0f || t >= 1.0f) {
                continue;
            }
            float angle = (float) (hash(seed, i) * Mth.TWO_PI) + t * 1.4f;
            float dist = reach * 0.85f * t * t;
            float scale = 0.25f + t * t * 3.2f;
            float alpha = env * Math.min(1.0f, t * 4.0f) * (t > 0.8f ? (1.0f - t) / 0.2f : 1.0f);
            int which = (int) (hash(seed, i + 17) * GLIMPSE.length) % GLIMPSE.length;
            g.pose().pushMatrix();
            g.pose().translate(cx + Mth.cos(angle) * dist, cy + Mth.sin(angle) * dist);
            g.pose().rotate((t - 0.5f) * 0.5f);
            g.pose().scale(scale, scale);
            g.blit(RenderPipelines.GUI_TEXTURED, GLIMPSE[which], -GLIMPSE_W / 2, -GLIMPSE_H / 2, 0.0f, 0.0f,
                    GLIMPSE_W, GLIMPSE_H, GLIMPSE_W, GLIMPSE_H, GLIMPSE_W, GLIMPSE_H, argb(alpha, 0xFFFFFF));
            g.pose().popMatrix();
        }
        g.pose().popMatrix();
    }

    // ── the ash ─────────────────────────────────────────────────────────────

    private static void renderAsh(GuiGraphics g, int w, int h, float t, long sinceLanding) {
        float strength = 1.0f - t;
        // "He fell, face forward": a moment of dark as you land, lifting fast.
        float landing = Math.max(0.0f, 1.0f - sinceLanding / 260.0f);
        if (landing > 0.0f) {
            g.fill(0, 0, w, h, argb(landing * 0.85f, 0x0B0806));
        }
        // Soot clinging to the top and bottom of the view, clearing.
        g.fillGradient(0, 0, w, h * 2 / 5, argb(strength * 0.8f, 0x17120F), argb(0.0f, 0x17120F));
        g.fillGradient(0, h - h * 2 / 5, w, h, argb(0.0f, 0x17120F), argb(strength * 0.85f, 0x17120F));
        // Ash drifting down across the view, swaying, the larger flakes nearer.
        float seconds = sinceLanding / 1000.0f;
        for (int i = 0; i < FLAKES; i++) {
            double r = hash(seed, i + 101);
            int size = r < 0.2 ? 4 : r < 0.55 ? 3 : 2;
            float speed = 18.0f + size * 16.0f + (float) hash(seed, i + 202) * 20.0f;
            float x = (float) (hash(seed, i + 303) * w) + Mth.sin(seconds * 2.0f + i) * 6.0f * size;
            float y = ((float) (hash(seed, i + 404) * h) + seconds * speed) % (h + 8) - 4;
            int colour = size == 4 ? 0x1E1916 : size == 3 ? 0x2E2722 : 0x4A423C;
            int xi = Math.round(x), yi = Math.round(y);
            g.fill(xi, yi, xi + size, yi + size, argb(Math.min(1.0f, strength * 1.3f), colour));
        }
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** A fixed [0, 1) number per journey and index, so a journey's glimpses and ash do not jump between frames. */
    private static double hash(long seed, int i) {
        long x = seed * 6364136223846793005L + i * 1442695040888963407L;
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        return (x >>> 11) * 0x1.0p-53;
    }
}
