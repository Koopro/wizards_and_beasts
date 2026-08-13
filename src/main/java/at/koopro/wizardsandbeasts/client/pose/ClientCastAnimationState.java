package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * In-flight cast animations, keyed by entity id.
 *
 * <p>The receiving end of {@link SpellCastAnimationS2CPayload}. Keyed by entity id rather than UUID
 * because that is what a render state carries ({@code AvatarRenderState.id}), and the consumer is a
 * pose pass reading it during a render.
 *
 * <p><b>Nothing consumes this yet.</b> {@code KeyframePosePass} is the consumer and is not built —
 * it is the next piece of pose-layer wave 1. Until it exists, casts arrive, are stored, expire on
 * schedule, and are never drawn. Left visibly incomplete rather than faked: the alternative is a
 * client that invents its own timing, which the schema forbids outright.
 */
@NullMarked
public final class ClientCastAnimationState {

    private ClientCastAnimationState() {}

    /**
     * One running cast.
     *
     * @param startTick client tick the cast arrived on, used as the playhead origin
     */
    public record ActiveCast(String spellId, int ticks, float windupEnd, float releaseEnd,
                            long startTick, int holdPhase) {

        /**
         * Progress 0..1 across the whole cast, interpolated across the tick boundary.
         *
         * <p>A held cast pins progress to the midpoint of the held phase instead of advancing, so the
         * debug command can park a pose and let it be looked at. Held casts never expire.
         */
        public float progress(long nowTick, float partialTicks) {
            if (held()) {
                return switch (holdPhase) {
                    case 0 -> windupEnd * 0.999f;
                    case 1 -> (windupEnd + releaseEnd) * 0.5f;
                    default -> (releaseEnd + 1f) * 0.5f;
                };
            }
            float elapsed = (nowTick - startTick) + partialTicks;
            return Math.min(1f, Math.max(0f, elapsed / ticks));
        }

        public boolean held() {
            return holdPhase >= 0;
        }

        public boolean expired(long nowTick) {
            return !held() && nowTick - startTick >= ticks;
        }

        /** Wind-up weight, 0..1, via the same sub-range remap every phase read uses. */
        public float windup(float progress) {
            return PhaseTimer.between(progress, 0f, windupEnd);
        }

        public float release(float progress) {
            return PhaseTimer.between(progress, windupEnd, releaseEnd);
        }

        public float recovery(float progress) {
            return PhaseTimer.between(progress, releaseEnd, 1f);
        }
    }

    private static final Map<Integer, ActiveCast> ACTIVE = new HashMap<>();

    public static void handle(SpellCastAnimationS2CPayload payload, long clientTick) {
        // A zero-tick payload is the command's "off": clear rather than store a degenerate cast.
        if (payload.ticks() <= 0) {
            ACTIVE.remove(payload.entityId());
            return;
        }
        ACTIVE.put(payload.entityId(), new ActiveCast(
                payload.spellId(), payload.ticks(),
                payload.windupEnd(), payload.releaseEnd(), clientTick, payload.holdPhase()));
    }

    /** The running cast for an entity, or null. Expired entries are dropped on read. */
    public static @Nullable ActiveCast get(int entityId, long nowTick) {
        ActiveCast cast = ACTIVE.get(entityId);
        if (cast == null) {
            return null;
        }
        if (cast.expired(nowTick)) {
            ACTIVE.remove(entityId);
            return null;
        }
        return cast;
    }

    /**
     * Drops finished casts.
     *
     * <p>Read-time expiry alone is not enough: an entity that casts and then leaves render distance
     * is never read again, and its entry would sit in the map until the world unloaded.
     */
    public static void tick(long nowTick) {
        ACTIVE.entrySet().removeIf(entry -> entry.getValue().expired(nowTick));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static int size() {
        return ACTIVE.size();
    }

    /** The client's own tick clock, which is the origin every playhead is measured from. */
    public static long clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}
