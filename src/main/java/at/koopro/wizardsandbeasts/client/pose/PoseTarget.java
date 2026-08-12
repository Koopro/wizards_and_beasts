package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;

/**
 * What a single pose operation addresses.
 *
 * <p>Two translation groups, and the difference between them is the whole reason the second exists.
 * {@link #X}/{@link #Y}/{@link #Z} are applied <em>before</em> rotation, so they move a part in its
 * parent's frame. {@link #X2}/{@link #Y2}/{@link #Z2} are applied <em>after</em>, so they move it in
 * its own rotated frame — which is how a pivot is shifted. Lying flat on a broom needs both: pitch
 * the body, then slide it along its new axis so the player straddles the shaft rather than orbiting
 * the point they were standing on.
 *
 * <p>The second group only exists on the {@code PoseStack} backend. A {@link net.minecraft.client.model.geom.ModelPart}
 * has one translation and one rotation, applied in that fixed order, so a post-rotation offset has
 * nowhere to live. Requesting one on a ModelPart target is a documented no-op.
 */
@NullMarked
public enum PoseTarget {
    X(Group.TRANSLATE, 0f),
    Y(Group.TRANSLATE, 0f),
    Z(Group.TRANSLATE, 0f),

    /** Post-rotation translation. PoseStack backend only. */
    X2(Group.TRANSLATE_POST, 0f),
    Y2(Group.TRANSLATE_POST, 0f),
    Z2(Group.TRANSLATE_POST, 0f),

    X_ROT(Group.ROTATE, 0f),
    Y_ROT(Group.ROTATE, 0f),
    Z_ROT(Group.ROTATE, 0f),

    X_SCALE(Group.SCALE, 1f),
    Y_SCALE(Group.SCALE, 1f),
    Z_SCALE(Group.SCALE, 1f);

    public enum Group { TRANSLATE, TRANSLATE_POST, ROTATE, SCALE }

    private final Group group;
    private final float identity;

    PoseTarget(Group group, float identity) {
        this.group = group;
        this.identity = identity;
    }

    public Group group() {
        return group;
    }

    /** The value that means "unchanged" — 0 for offsets and rotations, 1 for scale. */
    public float identity() {
        return identity;
    }

    /** True for the post-rotation translation group, which no {@code ModelPart} can express. */
    public boolean isPostRotation() {
        return group == Group.TRANSLATE_POST;
    }

    /** True for rotations, which are radians internally and are the only targets that wrap. */
    public boolean isRotation() {
        return group == Group.ROTATE;
    }
}
