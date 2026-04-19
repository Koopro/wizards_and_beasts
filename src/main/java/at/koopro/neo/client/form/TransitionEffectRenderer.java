package at.koopro.neo.client.form;

import at.koopro.neo.Neo;
import at.koopro.neo.form.TransformationConfig;
import at.koopro.neo.network.ClientTransitionTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Renders screen overlay effects during transformation transitions.
 * Registered as a GUI layer in {@link at.koopro.neo.NeoClient}.
 */
public final class TransitionEffectRenderer {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Neo.MODID, "transition_effect");

    private TransitionEffectRenderer() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ClientTransitionTracker.TransitionState state =
                ClientTransitionTracker.getActive(mc.player.getUUID());
        if (state == null) return;

        float progress = state.progress();
        TransformationConfig.ScreenEffect effect =
                TransformationConfig.ScreenEffect.fromOrdinal(state.screenEffectOrdinal());

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        switch (effect) {
            case DARK_FADE -> renderDarkFade(graphics, screenW, screenH, progress);
            case PARTICLE_BURST -> renderParticleBurst(graphics, screenW, screenH, progress);
            case SMOKE_CLOUD -> renderSmokeCloud(graphics, screenW, screenH, progress);
            case NONE -> {}
        }
    }

    /**
     * Dark fade: screen fades to black at midpoint, then fades back.
     */
    private static void renderDarkFade(GuiGraphics graphics, int w, int h, float progress) {
        // Fade in from 0→0.5, fade out from 0.5→1.0
        float intensity;
        if (progress < 0.5f) {
            intensity = progress * 2.0f; // 0 → 1
        } else {
            intensity = (1.0f - progress) * 2.0f; // 1 → 0
        }

        int alpha = (int) (intensity * 200);
        alpha = Math.min(alpha, 200);
        int color = alpha << 24; // black with variable alpha
        graphics.fill(0, 0, w, h, color);
    }

    /**
     * Particle burst: expanding bright ring effect.
     */
    private static void renderParticleBurst(GuiGraphics graphics, int w, int h, float progress) {
        // Golden flash that peaks at midpoint
        float intensity;
        if (progress < 0.3f) {
            intensity = progress / 0.3f;
        } else {
            intensity = Math.max(0, (1.0f - progress) / 0.7f);
        }

        int alpha = (int) (intensity * 120);
        int color = (alpha << 24) | 0xFFD700; // golden with alpha
        graphics.fill(0, 0, w, h, color);

        // Draw expanding ring at center
        if (progress < 0.6f) {
            float ringProgress = progress / 0.6f;
            int ringRadius = (int) (ringProgress * Math.max(w, h) / 2);
            int ringAlpha = (int) ((1.0f - ringProgress) * 80);
            int ringColor = (ringAlpha << 24) | 0xFFFFAA;

            int cx = w / 2;
            int cy = h / 2;
            int thickness = 3;
            // Top, bottom, left, right edges of the ring (simplified rectangle)
            graphics.fill(cx - ringRadius, cy - thickness, cx + ringRadius, cy + thickness, ringColor);
            graphics.fill(cx - thickness, cy - ringRadius, cx + thickness, cy + ringRadius, ringColor);
        }
    }

    /**
     * Smoke cloud: dark tendrils creeping from edges.
     */
    private static void renderSmokeCloud(GuiGraphics graphics, int w, int h, float progress) {
        // Dark purple smoke that fills from edges
        float intensity;
        if (progress < 0.4f) {
            intensity = progress / 0.4f;
        } else if (progress < 0.7f) {
            intensity = 1.0f;
        } else {
            intensity = (1.0f - progress) / 0.3f;
        }

        int alpha = (int) (intensity * 160);
        int color = (alpha << 24) | 0x110022; // dark purple

        // Edge bands that grow inward
        int edgeSize = (int) (intensity * w / 4);
        graphics.fill(0, 0, edgeSize, h, color);
        graphics.fill(w - edgeSize, 0, w, h, color);
        graphics.fill(0, 0, w, edgeSize, color);
        graphics.fill(0, h - edgeSize, w, h, color);

        // Light overlay in center
        int centerAlpha = (int) (intensity * 40);
        int centerColor = (centerAlpha << 24) | 0x110022;
        graphics.fill(0, 0, w, h, centerColor);
    }
}
