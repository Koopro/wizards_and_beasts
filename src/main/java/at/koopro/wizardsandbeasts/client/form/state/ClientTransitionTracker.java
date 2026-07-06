package at.koopro.wizardsandbeasts.client.form.state;

import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side tracker for active transformation transitions.
 * Used by the transition effect renderer to know when to display effects.
 */
public final class ClientTransitionTracker {

    private static final Map<UUID, TransitionState> ACTIVE = new ConcurrentHashMap<>();

    public static void startTransition(UUID playerUUID, String fromFormId, String toFormId,
                                        int durationTicks, int screenEffectOrdinal) {
        ACTIVE.put(playerUUID, new TransitionState(
                fromFormId, toFormId, durationTicks, screenEffectOrdinal,
                System.currentTimeMillis(), 0));
    }

    public static void endTransition(UUID playerUUID) {
        ACTIVE.remove(playerUUID);
    }

    /**
     * Called each client tick to advance transition progress.
     */
    public static void tick() {
        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            TransitionState state = entry.getValue();
            int newTick = state.elapsedTicks + 1;
            if (newTick >= state.durationTicks) {
                it.remove();
            } else {
                entry.setValue(new TransitionState(
                        state.fromFormId, state.toFormId, state.durationTicks,
                        state.screenEffectOrdinal, state.startTimeMs, newTick));
            }
        }
    }

    @Nullable
    public static TransitionState getActive(UUID playerUUID) {
        return ACTIVE.get(playerUUID);
    }

    public static boolean isTransitioning(UUID playerUUID) {
        return ACTIVE.containsKey(playerUUID);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private ClientTransitionTracker() {}

    public record TransitionState(
            String fromFormId,
            String toFormId,
            int durationTicks,
            int screenEffectOrdinal,
            long startTimeMs,
            int elapsedTicks
    ) {
        public float progress() {
            return durationTicks > 0 ? (float) elapsedTicks / durationTicks : 1.0f;
        }
    }
}
