package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.network.pose.PlayerGestureS2CPayload;
import at.koopro.wizardsandbeasts.pose.PlayerGesture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Running gestures by entity id: the receiving end of {@link PlayerGestureS2CPayload}.
 *
 * <p>Holds only what the server said — which gesture and from when. Easing in and out is
 * {@link GesturePosePass}'s job (a {@link KindFade}), so a stop here simply removes the entry and the pass lets the
 * pose go over its fall time.
 */
@NullMarked
public final class ClientGestureState {

    private ClientGestureState() {}

    /** One running gesture. {@code startTick} is the client tick it arrived on: the playhead origin. */
    public record Active(PlayerGesture gesture, long startTick, int ticks) {

        /** 0..1 through the gesture, across the tick boundary. */
        public float progress(double time) {
            return (float) Math.min(1.0, Math.max(0.0, (time - startTick) / ticks));
        }

        public boolean expired(long now) {
            return now - startTick >= ticks;
        }
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();

    public static void handle(PlayerGestureS2CPayload payload, long clientTick) {
        PlayerGesture gesture = PlayerGesture.byId(payload.gestureId());
        if (gesture == null) {
            return;
        }
        if (payload.ticks() <= 0) {
            Active running = ACTIVE.get(payload.entityId());
            if (running != null && running.gesture() == gesture) {
                ACTIVE.remove(payload.entityId());
            }
            return;
        }
        ACTIVE.put(payload.entityId(), new Active(gesture, clientTick, payload.ticks()));
    }

    /** The running gesture for an entity, or null. Expired entries are dropped on read. */
    public static @Nullable Active get(int entityId, long now) {
        Active active = ACTIVE.get(entityId);
        if (active != null && active.expired(now)) {
            ACTIVE.remove(entityId);
            return null;
        }
        return active;
    }

    /** Test and debug seam: starts a gesture without a packet. */
    public static void play(int entityId, PlayerGesture gesture, long clientTick, int ticks) {
        ACTIVE.put(entityId, new Active(gesture, clientTick, ticks));
    }

    public static void tick(long now) {
        ACTIVE.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
