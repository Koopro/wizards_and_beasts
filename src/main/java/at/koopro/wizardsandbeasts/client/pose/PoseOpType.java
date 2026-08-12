package at.koopro.wizardsandbeasts.client.pose;

import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * How an operation combines with the value already accumulated for a target.
 *
 * <p>Every type resolves through the same weighted lerp — {@code current + (target - current) *
 * multiplier} — which is what makes fading a keyframe pass and fading a procedural pass the same
 * operation. The types differ only in what they compute the target from.
 */
@NullMarked
public enum PoseOpType {
    /**
     * Lerp toward an absolute value, raw.
     *
     * <p>Wraps the long way round on angles: lerping 350° to 10° travels backwards through 180°.
     * That is occasionally what you want — a deliberate full spin — and always wrong for anything
     * derived from entity yaw or pitch. Use {@link #SET_SHORTEST} there.
     */
    SET,

    /** Lerp toward an absolute value, taking the shorter arc. The safe choice for angles. */
    SET_SHORTEST,

    /**
     * Offset from the accumulated value.
     *
     * <p>Additive on translations and rotations; on scale targets it is multiplicative around 1.0,
     * so {@code ADD 0.5} on a scale means "half again", not "0.5 bigger". Additive scale otherwise
     * makes stacking two passes produce a part that has vanished.
     */
    ADD,

    /** Lerp back toward the part's initial pose. A blendable "return to default". */
    RESET;

    /**
     * Resolves one operation.
     *
     * @param current    value accumulated so far this frame
     * @param value      the operand
     * @param initial    the part's initial value for this target, used by {@link #RESET}
     * @param multiplier blend weight, 0 leaves {@code current} untouched and 1 lands on the target
     */
    public float apply(PoseTarget target, float current, float value, float initial, float multiplier) {
        float goal = switch (this) {
            case SET -> value;
            case SET_SHORTEST -> target.isRotation()
                    ? current + Mth.wrapDegrees((float) Math.toDegrees(value - current)) * Mth.DEG_TO_RAD
                    : value;
            case ADD -> target.group() == PoseTarget.Group.SCALE
                    ? current * (1.0f + value)
                    : current + value;
            case RESET -> initial;
        };
        return current + (goal - current) * multiplier;
    }
}
