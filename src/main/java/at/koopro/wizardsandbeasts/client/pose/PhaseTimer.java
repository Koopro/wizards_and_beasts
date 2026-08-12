package at.koopro.wizardsandbeasts.client.pose;

import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * A tick counter that ramps toward a maximum while a condition holds and back down when it releases.
 *
 * <p>The shared driver for every time-varying pose input, procedural or keyframe. Keeping the
 * previous tick's value is the whole point: a pose interpolated from an int that only changes twenty
 * times a second visibly steps, so every read takes a frame fraction and lerps across the tick
 * boundary.
 *
 * <p>Not thread-safe and not meant to be. One timer belongs to one pass.
 */
@NullMarked
public final class PhaseTimer {

    private final int startValue;
    private final int maxValue;

    private int value;
    private int prevValue;

    public PhaseTimer(int maxValue) {
        this(0, maxValue);
    }

    public PhaseTimer(int startValue, int maxValue) {
        if (maxValue <= startValue) {
            throw new IllegalArgumentException(
                    "PhaseTimer needs maxValue > startValue, got " + startValue + ".." + maxValue);
        }
        this.startValue = startValue;
        this.maxValue = maxValue;
        this.value = startValue;
        this.prevValue = startValue;
    }

    /**
     * Advances one tick, toward the max while {@code condition} holds and back to the start when it
     * does not. Call exactly once per tick — calling it twice in a frame double-speeds the ramp.
     */
    public void tick(boolean condition) {
        prevValue = value;
        if (condition) {
            if (value < maxValue) value++;
        } else if (value > startValue) {
            value--;
        }
    }

    /** Progress 0..1 across the whole ramp, interpolated across the tick boundary. */
    public float value(float partialTicks) {
        return raw(partialTicks) / (float) maxValue;
    }

    /** The interpolated tick count itself — the playhead for a keyframe clip. */
    public float raw(float partialTicks) {
        return Mth.lerp(Mth.clamp(partialTicks, 0f, 1f), prevValue, value);
    }

    /**
     * Remaps a raw value onto 0..1 across an arbitrary window.
     *
     * <p>So one timer can sequence several phases — wind-up 0–8, hold 8–20, release 20–26 — instead
     * of allocating three timers that then have to be kept in step with each other.
     */
    public static float between(float progress, float start, float end) {
        if (end <= start) {
            return progress >= end ? 1f : 0f;
        }
        return Mth.clamp((progress - start) / (end - start), 0f, 1f);
    }

    /** True once the ramp has fully wound down — the pass has nothing left to contribute. */
    public boolean idle() {
        return value == startValue && prevValue == startValue;
    }

    public boolean complete() {
        return value == maxValue;
    }

    public int rawValue() {
        return value;
    }

    public int maxValue() {
        return maxValue;
    }

    /** Snaps both current and previous, for a hard cut with no interpolation across the jump. */
    public void set(int newValue) {
        this.value = Mth.clamp(newValue, startValue, maxValue);
        this.prevValue = this.value;
    }
}
