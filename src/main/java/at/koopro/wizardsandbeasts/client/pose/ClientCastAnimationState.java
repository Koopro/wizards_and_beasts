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
 * <p>Read by {@link CastPosePass} (the body) and {@link FirstPersonCast} (the held wand on screen). The server
 * sends every phase change; the client never invents its own timing, which the schema forbids outright.
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
         * <p>A held cast pins progress instead of advancing: phase 0 at the end of the wind-up (the gather, held
         * while the wand is held), phase 1 at the end of the release (the strike, held through a channel), phase 2
         * in the middle of recovery. Held casts never expire.
         */
        public float progress(long nowTick, float partialTicks) {
            if (held()) {
                return switch (holdPhase) {
                    case 0 -> windupEnd * 0.999f;
                    case 1 -> releaseEnd * 0.999f;
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

    /**
     * What the arm was doing when the last change arrived, per entity: the crossfade origin.
     *
     * <p>Exists because every change used to be a cut. A held cast pinned its progress at the end of the wind-up with
     * full weight, so the wand was behind the head in the frame the key went down; an "off" deleted the entry, so an
     * interrupted gather dropped the arm to vanilla in one frame. The blend snapshots the pose on screen at the moment
     * of the change and eases from it, which also covers a new cast arriving while the last one is still settling.
     */
    private static final Map<Integer, Blend> BLENDS = new HashMap<>();

    /**
     * One entity's crossfade.
     *
     * @param from       the third-person pose on screen when the change arrived
     * @param fpFrom     the first-person wand offset on screen then
     * @param fromWeight the weight it was shown at; zero when the arm was free
     * @param changeTime client tick plus partial tick of the change
     * @param ticks      how long the crossfade runs
     */
    record Blend(CastPoseConstants.CastPose from, CastPoseConstants.FirstPersonPose fpFrom, float fromWeight,
                 double changeTime, int ticks) {

        float progress(double time) {
            return smoothstep((float) ((time - changeTime) / ticks));
        }

        boolean finished(double time) {
            return time - changeTime >= ticks;
        }
    }

    /**
     * What an entity's casting arm shows right now, blended.
     *
     * @param pose   third-person pose to apply
     * @param fp     first-person wand offset to apply
     * @param weight 0..1 share of the pose over vanilla
     * @param cast   the running cast, or null while an interrupted cast lets go
     */
    public record Resolved(CastPoseConstants.CastPose pose, CastPoseConstants.FirstPersonPose fp, float weight,
                           @Nullable ActiveCast cast) {}

    /** Ticks a held cast takes to rise into its gather, and an interrupted one to let go. */
    public static final int HOLD_BLEND_TICKS = 4;
    public static final int OFF_BLEND_TICKS = 4;
    /** A strike is sharp: the crossfade into it is short so it still lands on the frame the spell leaves. */
    public static final int STRIKE_BLEND_TICKS = 2;

    public static void handle(SpellCastAnimationS2CPayload payload, long clientTick) {
        handle(payload, clientTick, clientTick + partialTick());
    }

    /** Test seam: the change stamped at an explicit time instead of the live partial tick. */
    static void handle(SpellCastAnimationS2CPayload payload, long clientTick, double time) {
        int id = payload.entityId();
        Resolved shown = resolve(id, clientTick, time);
        CastPoseConstants.CastPose from = shown == null ? CastPoseConstants.NEUTRAL : shown.pose();
        CastPoseConstants.FirstPersonPose fpFrom = shown == null ? CastPoseConstants.FP_NEUTRAL : shown.fp();
        float fromWeight = shown == null ? 0f : shown.weight();

        // A zero-tick payload is "off": the cast goes, but the arm lets go over a few ticks instead of dropping.
        if (payload.ticks() <= 0) {
            ACTIVE.remove(id);
            if (fromWeight > 0f) {
                BLENDS.put(id, new Blend(from, fpFrom, fromWeight, time, OFF_BLEND_TICKS));
            } else {
                BLENDS.remove(id);
            }
            return;
        }
        ActiveCast cast = new ActiveCast(
                payload.spellId(), payload.ticks(),
                payload.windupEnd(), payload.releaseEnd(), clientTick, payload.holdPhase());
        ACTIVE.put(id, cast);
        BLENDS.put(id, new Blend(from, fpFrom, fromWeight, time,
                cast.held() ? HOLD_BLEND_TICKS : STRIKE_BLEND_TICKS));
    }

    /**
     * The blended pose for an entity, or null when nothing is posing its arm.
     *
     * @param time client tick plus partial tick, the clock {@link #handle} stamps each change with
     */
    public static @Nullable Resolved resolve(int entityId, long nowTick, double time) {
        ActiveCast cast = get(entityId, nowTick);
        Blend blend = BLENDS.get(entityId);
        if (cast == null) {
            if (blend == null || blend.finished(time)) {
                BLENDS.remove(entityId);
                return null;
            }
            // Letting go: hold the last pose and let its share fall.
            return new Resolved(blend.from(), blend.fpFrom(), blend.fromWeight() * (1f - blend.progress(time)), null);
        }

        float progress = cast.progress(nowTick, (float) (time - nowTick));
        CastPoseConstants.CastPose target = CastPoseConstants.at(cast, progress);
        CastPoseConstants.FirstPersonPose fpTarget = CastPoseConstants.firstPersonAt(cast, progress);
        float targetWeight = endFade(cast, progress);
        if (blend == null) {
            return new Resolved(target, fpTarget, targetWeight, cast);
        }
        float c = blend.progress(time);
        if (blend.fromWeight() <= 0f) {
            // Rising from a free arm: the pose is the target and only its share grows, so the arm travels from
            // vanilla's hold straight toward the gather instead of detouring through the neutral row.
            return new Resolved(target, fpTarget, targetWeight * c, cast);
        }
        return new Resolved(blend.from().lerp(target, c), blend.fpFrom().lerp(fpTarget, c),
                blend.fromWeight() + (targetWeight - blend.fromWeight()) * c, cast);
    }

    /**
     * A running cast's own fade at its end; a held cast stays at full weight.
     *
     * <p>Only the end. The start is the crossfade's job, which also knows what the arm was doing before.
     */
    static float endFade(ActiveCast cast, float progress) {
        if (cast.held()) {
            return 1f;
        }
        float outFrac = Math.min(0.5f, CastPoseConstants.FADE_OUT_TICKS / (float) cast.ticks());
        return 1f - PhaseTimer.between(progress, 1f - outFrac, 1f);
    }

    /** 0..1 with zero slope at both ends, so an ease reads as a motion rather than a ramp. */
    static float smoothstep(float t) {
        float c = Math.min(1f, Math.max(0f, t));
        return c * c * (3f - 2f * c);
    }

    private static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
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
        // A let-go older than its crossfade has nothing left to show.
        BLENDS.entrySet().removeIf(entry -> !ACTIVE.containsKey(entry.getKey()) && entry.getValue().finished(nowTick));
    }

    public static void clear() {
        ACTIVE.clear();
        BLENDS.clear();
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
