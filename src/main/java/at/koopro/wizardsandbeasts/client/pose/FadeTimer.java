package at.koopro.wizardsandbeasts.client.pose;

import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * A 0..1 ramp with independent rise and fall rates.
 *
 * <p>Separate from {@link PhaseTimer} rather than a flag on it. That timer counts whole ticks
 * because its raw value is the playhead a keyframe clip is sampled at, and a fractional playhead is
 * meaningless; this one is a blend weight and has no reason to be an integer. Merging them would
 * make one of the two lie about what its value means.
 *
 * <p>Exists because releasing should feel quicker than committing — {@code FLIGHT_POSE_CONSTANTS}
 * §6 asks for an 8 tick fade in against a 5 tick fade out, which a symmetric ramp cannot express.
 */
@NullMarked
public final class FadeTimer {

    private final float riseStep;
    private final float fallStep;

    private float value;
    private float prevValue;

    public FadeTimer(int riseTicks, int fallTicks) {
        if (riseTicks <= 0 || fallTicks <= 0) {
            throw new IllegalArgumentException(
                    "FadeTimer needs positive durations, got rise=" + riseTicks + " fall=" + fallTicks);
        }
        this.riseStep = 1f / riseTicks;
        this.fallStep = 1f / fallTicks;
    }

    /** Advances one tick. Call exactly once per tick — twice in a frame double-speeds the ramp. */
    public void tick(boolean condition) {
        prevValue = value;
        value = Mth.clamp(value + (condition ? riseStep : -fallStep), 0f, 1f);
    }

    /** The weight, interpolated across the tick boundary so it does not step at high frame rates. */
    public float value(float partialTicks) {
        return Mth.lerp(Mth.clamp(partialTicks, 0f, 1f), prevValue, value);
    }

    /**
     * True once the ramp has fully wound down.
     *
     * <p>Deliberately one tick behind: on the tick the value first reaches zero the previous value is
     * still above it, so a frame drawn mid-boundary would render part of the pose. Calling that frame
     * idle is what makes a pose pop out of existence instead of easing.
     */
    public boolean idle() {
        return value <= 0f && prevValue <= 0f;
    }

    public boolean complete() {
        return value >= 1f;
    }

    public float rawValue() {
        return value;
    }
}
