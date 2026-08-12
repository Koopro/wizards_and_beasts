package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;

/**
 * Which arm, if any, the current pass invocation is posing for the first-person view.
 *
 * <p>A required branch, not an optional one. A shoulder rotation that reads correctly in third
 * person puts the held wand through the camera in first person, so a pass that ignores this is a
 * defect rather than an unfinished feature — hence the deliberate absence of any "apply to both"
 * convenience that would let one be written by accident.
 */
@NullMarked
public enum FirstPersonContext {
    /** Third person, or any whole-model pass. */
    NONE,
    RIGHT_ARM,
    LEFT_ARM;

    public boolean firstPerson() {
        return this != NONE;
    }

    /** True when posing the arm holding the main-hand item. */
    public boolean mainArm(net.minecraft.world.entity.HumanoidArm mainArm) {
        return firstPerson() && (this == RIGHT_ARM) == (mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT);
    }

    public boolean offArm(net.minecraft.world.entity.HumanoidArm mainArm) {
        return firstPerson() && !mainArm(mainArm);
    }

    /** The model part this context addresses, or null in third person. */
    public @org.jspecify.annotations.Nullable PlayerModelPart part() {
        return switch (this) {
            case NONE -> null;
            case RIGHT_ARM -> PlayerModelPart.RIGHT_ARM;
            case LEFT_ARM -> PlayerModelPart.LEFT_ARM;
        };
    }
}
