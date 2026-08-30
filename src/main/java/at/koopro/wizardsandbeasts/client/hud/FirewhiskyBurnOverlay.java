package at.koopro.wizardsandbeasts.client.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.firewhisky.Firewhisky;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The heat coming off a mouthful of Firewhisky.
 *
 * <p>Drawn as horizontal amber bands that slide and breathe rather than as a true refraction: the mod
 * has no post-process pass wired for a screen-space distortion, and adding one for three seconds of
 * flavour would be a render pipeline in service of a garnish. Bands read as heat haze at a glance and
 * cost a handful of {@code fill} calls.
 *
 * <p>Fades with the effect's own remaining duration, so it thins out as the burn passes and needs no
 * state of its own. Honours {@code reduceScreenEffects}, like every other tint layer in the mod.
 */
public final class FirewhiskyBurnOverlay {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "firewhisky_burn");

    /** Ember amber. */
    private static final int HAZE_RGB = 0xD9541E;

    /** Peak alpha of a band, at the very start of the burn. */
    private static final float MAX_ALPHA = 0.16f;

    /** Bands across the screen. Few and thick: this is a shimmer, not scanlines. */
    private static final int BANDS = 7;

    /** How fast the bands slide, in screen-heights per second. */
    private static final float DRIFT_SPEED = 0.35f;

    private FirewhiskyBurnOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        LocalPlayer player = HudOverlays.overlayTarget();
        if (player == null) {
            return;
        }
        MobEffectInstance burn = player.getEffect(ModEffects.FIREWHISKY_BURN);
        if (burn == null) {
            return;
        }

        // Strongest at the first mouthful, gone by the time it has passed.
        float remaining = Mth.clamp(burn.getDuration() / (float) Firewhisky.BURN_TICKS, 0.0f, 1.0f);
        float alpha = MAX_ALPHA * remaining;
        if (alpha <= 0.002f) {
            return;
        }

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        float seconds = (System.currentTimeMillis() % 100_000L) / 1000.0f;
        int bandHeight = Math.max(2, h / (BANDS * 3));

        for (int band = 0; band < BANDS; band++) {
            // Each band drifts at its own phase, so they shear past each other instead of marching.
            float phase = seconds * DRIFT_SPEED + band * 0.37f;
            float offset = (phase - (float) Math.floor(phase));
            int y = (int) ((band / (float) BANDS + offset / BANDS) * h) % Math.max(1, h);
            // Bands nearer the edges are stronger, so the middle of the screen stays readable.
            float edge = Math.abs((y / (float) h) - 0.5f) * 2.0f;
            int a = Mth.clamp((int) (alpha * (0.45f + 0.55f * edge) * 255.0f), 0, 255);
            if (a <= 1) {
                continue;
            }
            graphics.fill(0, y, w, Math.min(h, y + bandHeight), (a << 24) | HAZE_RGB);
        }
    }
}
