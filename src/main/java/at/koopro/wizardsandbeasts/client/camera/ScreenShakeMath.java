package at.koopro.wizardsandbeasts.client.camera;

import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * The pure arithmetic behind {@link ScreenShakeHandler}, split out so the parts that decide whether a
 * spell impact feels weighty or just makes a player motion-sick can be asserted without a render
 * context — the same split {@code BroomCameraMath} uses for broom roll.
 *
 * <p>Three properties matter and none of them are visible in a screenshot: the shake has to be capped
 * however big the burst is, it has to fall off with distance so a fight across the valley does not
 * rattle your view, and it has to reach exactly zero so the camera is handed back to vanilla instead
 * of keeping a permanent hairline wobble.
 */
@NullMarked
public final class ScreenShakeMath {

    /** Hard cap on shake amplitude in degrees. Past roughly this the view stops reading as impact. */
    static final float MAX_AMPLITUDE_DEG = 1.3F;
    /** Blocks past which an impact is not felt at all. */
    static final float FALLOFF_RANGE = 24.0F;
    /** Impacts closer than this are all felt at full strength, so standing on top of one is not a spike. */
    static final float FULL_STRENGTH_RANGE = 3.0F;
    /** Below this amplitude the shake counts as finished and is snapped to zero. */
    static final float SETTLED_EPSILON = 0.02F;
    /** Radians per tick of the shake oscillation. Fast enough to read as a jolt, not a wobble. */
    static final float FREQUENCY = 2.9F;
    /**
     * Pitch runs at a different rate to yaw, not merely a different phase.
     *
     * <p>Equal frequencies a quarter-cycle apart trace a circle, and a camera orbiting its own
     * aim point reads as vertigo rather than as being hit. An irrational-ish ratio keeps the two
     * axes from ever repeating the same figure, so the motion stays unpredictable and short.
     */
    static final float PITCH_FREQUENCY_RATIO = 0.68F;

    private ScreenShakeMath() {}

    /**
     * Peak amplitude in degrees for a burst of {@code particleCount} particles felt at
     * {@code distance} blocks.
     *
     * <p>Particle count is the only magnitude the impact payload already carries, so it stands in for
     * how big the hit was — bigger spells burst more particles. Taking its square root keeps a
     * 256-particle Avada from shaking eight times as hard as a 4-particle cantrip.
     */
    public static float amplitude(int particleCount, double distance) {
        float magnitude = (float) Math.sqrt(Math.max(0, particleCount)) / 8.0F;
        return Mth.clamp(magnitude, 0.0F, 1.0F) * MAX_AMPLITUDE_DEG * falloff(distance);
    }

    /** Fraction of an impact's strength felt at {@code distance} blocks: 1 up close, 0 past the range. */
    public static float falloff(double distance) {
        if (distance <= FULL_STRENGTH_RANGE) {
            return 1.0F;
        }
        if (distance >= FALLOFF_RANGE) {
            return 0.0F;
        }
        float t = (float) ((distance - FULL_STRENGTH_RANGE) / (FALLOFF_RANGE - FULL_STRENGTH_RANGE));
        // Squared rather than linear: sound and shock both fade fast, and a linear ramp leaves
        // distant fights feeling closer than they are.
        return (1.0F - t) * (1.0F - t);
    }

    /**
     * Remaining amplitude after {@code elapsedTicks} of a shake that started at {@code amplitude} and
     * runs for {@code durationTicks}, snapped to zero once it is spent.
     */
    public static float decay(float amplitude, float elapsedTicks, float durationTicks) {
        if (durationTicks <= 0.0F || elapsedTicks >= durationTicks) {
            return 0.0F;
        }
        float remaining = 1.0F - (elapsedTicks / durationTicks);
        float value = amplitude * remaining * remaining;
        return value < SETTLED_EPSILON ? 0.0F : value;
    }

    /**
     * Angular offset in degrees at {@code timeTicks} for one axis of a shake.
     *
     * <p><b>Always starts at zero.</b> An earlier version gave pitch a quarter-cycle phase offset,
     * which meant {@code sin} evaluated to 1 on the very first frame — the camera snapped to full
     * deflection the instant a spell landed, and the "shake" was really a pop followed by a wobble.
     * Driving both axes from {@code sin(0) = 0} means the view is exactly where the player left it
     * at the moment of impact and moves off from there.
     *
     * <p>{@code frequencyScale} separates the axes instead: see {@link #PITCH_FREQUENCY_RATIO}.
     * {@code direction} is the per-impact sign and weight for this axis, so two impacts in a row do
     * not shake along the same line.
     */
    public static float offset(float currentAmplitude, float timeTicks, float frequencyScale,
                               float direction) {
        return currentAmplitude * direction * Mth.sin(timeTicks * FREQUENCY * frequencyScale);
    }
}
