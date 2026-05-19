package at.koopro.wizardsandbeasts.client.form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only tracker that smoothly interpolates player visual scale
 * between size changes. The server applies Attributes.SCALE instantly
 * (for correct hitbox), but the client lerps the visual over ~10 ticks.
 */
public final class SizeLerpTracker {

    private static final int LERP_DURATION_TICKS = 10; // 0.5 seconds
    private static final Map<UUID, LerpState> STATES = new ConcurrentHashMap<>();

    /**
     * Called when a FormSyncS2CPayload arrives with new scale data.
     * Records the transition from current visual scale to target.
     */
    public static void onScaleChanged(UUID playerUUID, float newScaleY) {
        LerpState existing = STATES.get(playerUUID);
        float currentVisual = existing != null ? existing.currentVisual() : 1.0f;
        STATES.put(playerUUID, new LerpState(currentVisual, newScaleY, 0));
    }

    /**
     * Called each client tick to advance all lerp states.
     */
    public static void tick() {
        var it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            LerpState state = entry.getValue();
            int newTick = state.elapsedTicks + 1;
            if (newTick >= LERP_DURATION_TICKS) {
                // Lerp complete — keep final state for reference
                entry.setValue(new LerpState(state.targetScale, state.targetScale, LERP_DURATION_TICKS));
            } else {
                float progress = (float) newTick / LERP_DURATION_TICKS;
                float visual = state.previousScale + (state.targetScale - state.previousScale) * progress;
                entry.setValue(new LerpState(state.previousScale, state.targetScale, newTick, visual));
            }
        }
    }

    /**
     * Returns the visual scale factor for a player, accounting for lerp.
     * If no lerp is active, returns 1.0 (no compensation needed).
     *
     * @param partialTick sub-tick interpolation
     * @return the visual scale compensation factor (visualScale / serverScale)
     */
    public static float getVisualCompensation(UUID playerUUID, float serverScale, float partialTick) {
        LerpState state = STATES.get(playerUUID);
        if (state == null || serverScale == 0) return 1.0f;

        float progress = Math.min(1.0f,
                (state.elapsedTicks + partialTick) / LERP_DURATION_TICKS);
        float visualScale = state.previousScale
                + (state.targetScale - state.previousScale) * progress;

        return serverScale != 0 ? visualScale / serverScale : 1.0f;
    }

    /**
     * Returns the absolute visual scale for a player, accounting for lerp.
     * Unlike {@link #getVisualCompensation}, this returns the raw interpolated
     * scale value — used by the Mixin where vanilla scale is NOT pre-applied.
     *
     * @param targetScale the final target scale (from the size profile)
     * @param partialTick sub-tick interpolation
     * @return the interpolated visual scale
     */
    public static float getVisualScale(UUID playerUUID, float targetScale, float partialTick) {
        LerpState state = STATES.get(playerUUID);
        if (state == null) return targetScale;

        float progress = Math.min(1.0f,
                (state.elapsedTicks + partialTick) / LERP_DURATION_TICKS);
        return state.previousScale + (state.targetScale - state.previousScale) * progress;
    }

    /**
     * Returns whether a player is currently mid-lerp.
     */
    public static boolean isLerping(UUID playerUUID) {
        LerpState state = STATES.get(playerUUID);
        return state != null && state.elapsedTicks < LERP_DURATION_TICKS;
    }

    public static void remove(UUID playerUUID) {
        STATES.remove(playerUUID);
    }

    public static void clear() {
        STATES.clear();
    }

    private SizeLerpTracker() {}

    private record LerpState(float previousScale, float targetScale, int elapsedTicks, float currentVisual) {
        LerpState(float previousScale, float targetScale, int elapsedTicks) {
            this(previousScale, targetScale, elapsedTicks, previousScale);
        }
    }
}
