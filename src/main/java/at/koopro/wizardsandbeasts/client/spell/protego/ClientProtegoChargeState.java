package at.koopro.wizardsandbeasts.client.spell.protego;

import at.koopro.wizardsandbeasts.spell.protego.ProtegoTier;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * What the local player's wand is currently gathering, as last reported by the server.
 *
 * <p>Read by the charge vignette and by the wand's own tint. Kept deliberately dumb: one snapshot and
 * the tick it arrived on.
 *
 * <p><b>It expires rather than being cleared.</b> A charge that stops being reported is a charge that
 * ended — released, interrupted, died, disconnected — and treating silence as the end means no
 * "charge over" packet can go missing and leave the screen tinted.
 */
public final class ClientProtegoChargeState {

    /** Ticks of silence after which the charge is considered over. The server reports every other tick. */
    private static final long STALE_AFTER_TICKS = 5L;

    private static int tier;
    private static float progress;
    private static boolean planting;
    private static boolean capped;
    private static long lastUpdateTick = Long.MIN_VALUE;
    /** Client tick the tier last went up, for the flash that punctuates a threshold. */
    private static long lastStepTick = Long.MIN_VALUE;

    private ClientProtegoChargeState() {}

    public static void accept(int newTier, float newProgress, boolean newPlanting, boolean newCapped) {
        long now = clientTick();
        if (newTier > tier || !isCharging()) {
            lastStepTick = now;
        }
        tier = newTier;
        progress = Mth.clamp(newProgress, 0.0f, 1.0f);
        planting = newPlanting;
        capped = newCapped;
        lastUpdateTick = now;
    }

    public static boolean isCharging() {
        return clientTick() - lastUpdateTick <= STALE_AFTER_TICKS;
    }

    public static ProtegoTier tier() {
        return ProtegoTier.byIndex(tier);
    }

    public static float progress() {
        return progress;
    }

    public static boolean isPlanting() {
        return planting;
    }

    public static boolean isCapped() {
        return capped;
    }

    /** 1 immediately after a threshold, falling to 0 over half a second — the punch on a tier step. */
    public static float stepFlash(float partialTick) {
        float age = (clientTick() - lastStepTick) + partialTick;
        if (age < 0.0f || age > 10.0f) {
            return 0.0f;
        }
        return 1.0f - (age / 10.0f);
    }

    /** Forgets everything — the level is going away and the next charge starts from nothing. */
    public static void clear() {
        lastUpdateTick = Long.MIN_VALUE;
        lastStepTick = Long.MIN_VALUE;
        tier = 0;
        progress = 0.0f;
        planting = false;
        capped = false;
    }

    private static long clientTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? Long.MIN_VALUE / 2 : mc.level.getGameTime();
    }
}
