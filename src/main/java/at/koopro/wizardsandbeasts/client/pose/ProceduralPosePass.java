package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;

/**
 * Base for passes whose values are computed in Java rather than sampled from a clip.
 *
 * <p>Procedural is the right mechanism when a pose is a function of live input — flight attitude is
 * head pitch, movement direction and propulsion combined, and no keyframe table can express that
 * without becoming an expression language. Choreography goes the other way, into a clip.
 */
@NullMarked
public abstract class ProceduralPosePass implements PosePass {

    private final String name;
    private final int priority;

    protected ProceduralPosePass(String name, int priority) {
        this.name = name;
        this.priority = priority;
        // Validated here as well as at registration so a bad priority fails at construction, which
        // is where the stack trace names the pass rather than the registry.
        PoseBand.require(priority, name);
    }

    @Override
    public final int priority() {
        return priority;
    }

    @Override
    public final String name() {
        return name;
    }
}
