package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * The operations one pass wants applied to one part, plus the weight they apply at.
 *
 * <p><b>A queue, not mutation.</b> A pass never writes a rotation onto a model part. It records what
 * it wants and the layer resolves the whole stack afterwards, which is what lets a later pass blend
 * against an earlier one instead of overwriting it. It also means a pass can be run, inspected and
 * unit-tested without a model or a render pass in sight.
 *
 * <p>One scalar {@link #multiplier(float)} governs every operation queued here. That is the entire
 * blend model — no channels, no timelines. A pass fading out turns its multiplier down and every op
 * it queued fades together.
 */
@NullMarked
public final class PartPoseData {

    /** One queued operation. Radians for rotations; see {@link PoseTarget}. */
    public record Op(PoseTarget target, PoseOpType type, float value) {}

    private final List<Op> ops = new ArrayList<>(4);
    private float multiplier = 1.0f;

    /** Blend weight for every op queued on this part. Clamped to 0..1. */
    public PartPoseData multiplier(float multiplier) {
        this.multiplier = Math.max(0.0f, Math.min(1.0f, multiplier));
        return this;
    }

    public float multiplier() {
        return multiplier;
    }

    /**
     * Sugar for {@code multiplier(easing.applyAsDouble(progress))}.
     *
     * <p>Takes the easing as a function so callers can pass GeckoLib's static
     * {@code EasingType.easeInOut(...)} results directly — the mod does not reimplement easing
     * curves it already ships.
     */
    public PartPoseData animate(java.util.function.DoubleUnaryOperator easing, float progress) {
        return multiplier((float) easing.applyAsDouble(Math.max(0.0f, Math.min(1.0f, progress))));
    }

    public PartPoseData op(PoseTarget target, PoseOpType type, float value) {
        ops.add(new Op(target, type, value));
        return this;
    }

    // ── shorthand ────────────────────────────────────────────────────────────

    public PartPoseData set(PoseTarget target, float value) {
        return op(target, PoseOpType.SET, value);
    }

    /** Absolute rotation in degrees, taking the shorter arc. The safe default for angles. */
    public PartPoseData setRotDeg(PoseTarget target, float degrees) {
        return op(target, PoseOpType.SET_SHORTEST, degrees * net.minecraft.util.Mth.DEG_TO_RAD);
    }

    public PartPoseData add(PoseTarget target, float value) {
        return op(target, PoseOpType.ADD, value);
    }

    public PartPoseData addRotDeg(PoseTarget target, float degrees) {
        return op(target, PoseOpType.ADD, degrees * net.minecraft.util.Mth.DEG_TO_RAD);
    }

    /** Blend this target back toward the part's initial pose. */
    public PartPoseData reset(PoseTarget target) {
        return op(target, PoseOpType.RESET, 0.0f);
    }

    public List<Op> ops() {
        return ops;
    }

    public boolean isEmpty() {
        return ops.isEmpty();
    }
}
