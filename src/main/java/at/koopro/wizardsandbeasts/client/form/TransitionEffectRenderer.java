package at.koopro.wizardsandbeasts.client.form;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.ui.ObscurialUiModel;
import at.koopro.wizardsandbeasts.client.ui.UiStateProjection;
import at.koopro.wizardsandbeasts.form.TransformationConfig;
import at.koopro.wizardsandbeasts.client.form.state.ClientTransitionTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Renders screen overlay effects during transformation transitions.
 * Registered as a GUI layer in {@link at.koopro.wizardsandbeasts.WizardsAndBeastsClient}.
 */
public final class TransitionEffectRenderer {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "transition_effect");

    private TransitionEffectRenderer() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (at.koopro.wizardsandbeasts.Config.reduceScreenEffects) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        renderLowControlOverlay(graphics, mc);

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
        // Dark violet smoke that fills from edges with a denser midpoint pulse.
        float intensity;
        if (progress < 0.4f) {
            intensity = progress / 0.4f;
        } else if (progress < 0.7f) {
            intensity = 1.0f;
        } else {
            intensity = (1.0f - progress) / 0.3f;
        }

        int alpha = (int) (intensity * 185);
        int color = (alpha << 24) | 0x180A2B;

        // Edge bands that grow inward
        int edgeSize = (int) (intensity * w / 3.5f);
        graphics.fill(0, 0, edgeSize, h, color);
        graphics.fill(w - edgeSize, 0, w, h, color);
        graphics.fill(0, 0, w, edgeSize, color);
        graphics.fill(0, h - edgeSize, w, h, color);

        // Center pulse to emphasize unstable obscurus surge.
        float centerPulse = progress < 0.5f ? progress / 0.5f : (1.0f - progress) / 0.5f;
        int centerAlpha = (int) ((intensity * 28) + (Math.max(0, centerPulse) * 35));
        int centerColor = (centerAlpha << 24) | 0x25113A;
        graphics.fill(0, 0, w, h, centerColor);
    }

    private static void renderLowControlOverlay(GuiGraphics graphics, Minecraft mc) {
        ObscurialUiModel obscurialUi = UiStateProjection.obscurialHud();
        if (!obscurialUi.obscurialType()) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        if (obscurialUi.isDarkForm()) {
            if (obscurialUi.control() <= 20f) {
                graphics.fill(0, 0, w, h, (42 << 24) | 0x150A23);
            } else if (obscurialUi.control() <= 55f) {
                graphics.fill(0, 0, w, h, (20 << 24) | 0x150A23);
            }
        } else if (obscurialUi.isHumanForm() && obscurialUi.stress() >= 75f) {
            graphics.fill(0, 0, w, h, (18 << 24) | 0x2A123A);
        }
    }
}
