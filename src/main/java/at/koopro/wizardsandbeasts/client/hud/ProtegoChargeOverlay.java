package at.koopro.wizardsandbeasts.client.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.protego.ClientProtegoChargeState;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoTier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The charge you can feel: a vignette in the gathering shape's own colour that closes in as the hold
 * climbs, and flares once each time a threshold is crossed.
 *
 * <p>Drawn as nested one-pixel frames rather than a texture, so the edge falls off smoothly and the
 * middle of the screen is never touched — this has to be readable during a duel, not decorative.
 * Strength comes from the tier (each shape sits deeper than the last) plus the progress towards the
 * next one, so the ramp between chimes is visible and the chime lands on a flash.
 *
 * <p>Honours {@code reduceScreenEffects} through {@link HudOverlays#overlayTarget()}; the sounds and
 * the particles carry the same information for anyone who turns it off.
 */
public final class ProtegoChargeOverlay {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "protego_charge");

    /** How far in from the edge the vignette reaches at full strength, as a fraction of the shorter side. */
    private static final float MAX_DEPTH = 0.18f;
    /** Alpha at the outermost frame when the top shape is held. Deliberately modest. */
    private static final float MAX_ALPHA = 0.42f;
    /** Frames drawn per edge. Enough for a smooth falloff, few enough to be free. */
    private static final int STEPS = 14;
    /** Extra reach and alpha on the flash that marks a threshold. */
    private static final float FLASH_BOOST = 0.5f;

    private ProtegoChargeOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        LocalPlayer player = HudOverlays.overlayTarget();
        if (player == null || !ClientProtegoChargeState.isCharging()) {
            return;
        }

        ProtegoTier tier = ClientProtegoChargeState.tier();
        float flash = ClientProtegoChargeState.stepFlash(delta.getGameTimeDeltaPartialTick(false));

        // Each shape is a step deeper, and the fill towards the next one rides on top of it — so the
        // vignette is still climbing between the chimes rather than sitting still for a second.
        float tierFill = (tier.index() + (ClientProtegoChargeState.isCapped() ? 1.0f
                : ClientProtegoChargeState.progress())) / ProtegoTier.values().length;
        float intensity = Mth.clamp(tierFill, 0.05f, 1.0f) * (1.0f + FLASH_BOOST * flash);

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int depth = Math.max(4, Math.round(Math.min(width, height) * MAX_DEPTH * Mth.clamp(intensity, 0.0f, 1.2f)));
        int rgb = tier.colour() & 0x00FFFFFF;
        float peak = MAX_ALPHA * Mth.clamp(intensity, 0.0f, 1.25f);

        for (int step = 0; step < STEPS; step++) {
            // Squared falloff: dense at the very edge, gone well before the crosshair.
            float t = 1.0f - (step / (float) STEPS);
            int alpha = Mth.clamp(Math.round(peak * t * t * 255.0f), 0, 255);
            if (alpha <= 1) {
                continue;
            }
            int colour = ARGB.color(alpha, ARGB.red(rgb | 0xFF000000), ARGB.green(rgb | 0xFF000000),
                    ARGB.blue(rgb | 0xFF000000));
            int inset = Math.round(depth * (step / (float) STEPS));
            int band = Math.max(1, depth / STEPS);
            graphics.fill(0, inset, width, inset + band, colour);                      // top
            graphics.fill(0, height - inset - band, width, height - inset, colour);    // bottom
            graphics.fill(inset, 0, inset + band, height, colour);                     // left
            graphics.fill(width - inset - band, 0, width - inset, height, colour);     // right
        }
    }
}
