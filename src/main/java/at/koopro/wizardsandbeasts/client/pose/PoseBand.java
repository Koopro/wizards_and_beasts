package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;

/**
 * The priority bands a pass may register in.
 *
 * <p>Bands exist because keyframe and procedural passes stack against each other and compete for the
 * same parts. Without them, "priority 150" means nothing to the next person adding a pass, and two
 * unrelated features silently fight over an arm.
 *
 * <p>Registering outside every band is a <b>hard error</b>, not a warning. A pass with a nonsense
 * priority still runs — it just runs somewhere arbitrary in the order, which surfaces later as a
 * pose that is subtly wrong in a way nobody connects to a number in a constructor.
 */
@NullMarked
public enum PoseBand {
    /** Posture and idle. */
    POSTURE(0, 99),
    /** Locomotion — flight, broom attitude. */
    LOCOMOTION(100, 199),
    /** Casting — spell phases, wand flicks. */
    CASTING(200, 299),
    /** Transforms — Animagus, Metamorphmagus, Obscurus. */
    TRANSFORM(300, Integer.MAX_VALUE);

    private final int min;
    private final int max;

    PoseBand(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public boolean contains(int priority) {
        return priority >= min && priority <= max;
    }

    /**
     * @throws IllegalArgumentException if no band contains {@code priority}
     */
    public static PoseBand require(int priority, String passName) {
        for (PoseBand band : values()) {
            if (band.contains(priority)) {
                return band;
            }
        }
        throw new IllegalArgumentException(
                "Pose pass '" + passName + "' declares priority " + priority
                        + ", which falls in no band. Valid: POSTURE 0-99, LOCOMOTION 100-199, "
                        + "CASTING 200-299, TRANSFORM 300+.");
    }
}
