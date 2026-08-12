package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;

/**
 * One contributor to the pose stack.
 *
 * <p>Passes run in ascending priority, each weighted-lerping the accumulated state toward its own
 * pose, so a later pass blends over an earlier one rather than replacing it. The layer does not know
 * or care whether a pass computes its values in Java or samples them from a keyframe file — both
 * emit the same ops.
 */
@NullMarked
public interface PosePass {

    /** Must fall inside a {@link PoseBand}; validated at registration. */
    int priority();

    /** Stable name, used in registration errors and debug output. */
    String name();

    /**
     * Queue this pass's contribution. A pass with nothing to say returns without touching the
     * builder — an empty op list is free.
     */
    void pose(PoseBuilder builder, PoseContext context);
}
