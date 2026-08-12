package at.koopro.wizardsandbeasts.client.pose;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The parts a pose pass may address.
 *
 * <p>Six map onto a real {@link ModelPart}. {@link #BODY} does not: it is <b>virtual</b>, resolving
 * against the {@code PoseStack} instead. Rotating a whole player — lying flat along a broom — by
 * rotating six limbs individually cannot work, because each limb would spin about its own pivot
 * rather than the body's. The virtual part exists so a pass can say "pitch the whole avatar" in the
 * same op vocabulary as "raise the left arm".
 *
 * <p>Overlay parts — hat, jacket, sleeves, trouser layers — are never addressed, and need no copy
 * step. In 1.21.11 they are <em>children</em> of their base part
 * ({@code leftArm.getChild("left_sleeve")}, {@code body.getChild("jacket")}), so they inherit the
 * transform through the hierarchy. Older versions built them as siblings and required an explicit
 * copy; if a future version reverts to that, the copy belongs here.
 */
@NullMarked
public enum PlayerModelPart {
    HEAD(false),
    /** The torso's own {@code ModelPart}. Distinct from {@link #BODY}, which moves the whole avatar. */
    CHEST(false),
    RIGHT_ARM(false),
    LEFT_ARM(false),
    RIGHT_LEG(false),
    LEFT_LEG(false),
    /** Whole-avatar transform. Has no backing {@code ModelPart} — resolves against the PoseStack. */
    BODY(true);

    private final boolean virtual;

    PlayerModelPart(boolean virtual) {
        this.virtual = virtual;
    }

    /** True when this part has no backing {@link ModelPart} and must be applied to a PoseStack. */
    public boolean isVirtual() {
        return virtual;
    }

    /** The backing model part, or null for {@link #BODY}. */
    public @Nullable ModelPart resolve(HumanoidModel<?> model) {
        return switch (this) {
            case HEAD -> model.head;
            case CHEST -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
            case BODY -> null;
        };
    }

}
