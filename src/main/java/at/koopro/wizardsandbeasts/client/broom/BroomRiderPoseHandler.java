package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * Poses a broom rider. Vanilla puts any passenger in the boat pose — legs folded 81° forward at the
 * hip and both arms raised — which reads as sitting in a chair, not as straddling a shaft. This
 * replaces it with legs hanging near-straight astride the handle and both arms reaching forward to
 * grip it, then leans the torso into the broom's own forward lean so the rider and the broom bank
 * together instead of the rider standing upright on a tilted broom.
 *
 * <p>Called from {@code PlayerModelMixin} at the tail of {@code setupAnim}: the pose has to be
 * written after {@link HumanoidModel} has finished, or the passenger crouch overwrites it. The
 * riding flag arrives on the render state via {@link BroomRiderRenderer#getRide}, never by reading
 * the entity at render time.
 *
 * <p>Nothing has to undo this. The render state is rebuilt from scratch each frame and only carries
 * the ride data while {@code getVehicle()} is a broom, so dismounting, dying, or switching the
 * module off restores the vanilla pose on the very next frame.
 */
@NullMarked
public final class BroomRiderPoseHandler {

    /*
     * The flying crouch (2026-11-12, owner: "I don't like the player's pose on the broom"). The old pose kept the torso
     * upright and let the legs hang straight down past the shaft, so the rider read as standing behind a broom. A rider
     * in flight is a jockey: the torso leaned well forward over the handle, both hands on the handle in front, and the
     * legs swung back so the feet tuck toward the footrests.
     *
     * Arms, head and legs are not children of the torso in a humanoid model, so leaning the torso alone would tear the
     * figure apart. Vanilla's crouch solves this with offsets for a 0.5 rad lean (head down 4.2, torso and arms down
     * 3.2, legs back 4); this uses the same offsets scaled to its own lean.
     */
    /** The crouch's own forward lean of the torso, in radians, before the broom's tilt is added. */
    private static final float BASE_LEAN = 0.62F;
    /** Vanilla's crouch offsets per radian of lean (its 0.5 rad crouch moves the head 4.2 down, and so on). */
    private static final float HEAD_DROP_PER_RAD = 4.2F / 0.5F;
    private static final float BODY_DROP_PER_RAD = 3.2F / 0.5F;
    private static final float LEG_BACK_PER_RAD = 4.0F / 0.5F;
    /** Legs swing back from the hip toward the footrests behind the seat. */
    private static final float LEG_X_ROT = 0.95F;
    /** Splay, so the legs read as either side of the broom rather than fused together. */
    private static final float LEG_Z_ROT = 0.12F;
    /*
     * The grip (2026-11-12, owner: "arms should grab the broom"). The first crouch reached the arms straight forward at
     * shoulder width, so the hands hung beside and above the handle. The handle runs down the middle (x 0) at the seat:
     * a player's vehicle attachment is 0.6 blocks, so the shaft's top is about y 14.4 in model space, and the crouch has
     * already lowered the shoulders to about y 6. An arm reaches about 10 to the hand, so it swings down-forward
     * (cos = 8.5 / 10) and turns inward until the two hands close on the shaft, the wand hand a little ahead of the
     * other, as a broom is held.
     */
    /** Arms swing down and forward from the shoulder onto the handle; the right (wand) hand reaches further. */
    private static final float RIGHT_ARM_X_ROT = -0.62F;
    private static final float LEFT_ARM_X_ROT = -0.52F;
    /** Arms turn inward so both hands close on the shaft at the midline instead of hanging at shoulder width. */
    private static final float RIGHT_ARM_Y_ROT = -0.70F;
    private static final float LEFT_ARM_Y_ROT = 0.62F;
    /** How much of the broom's forward lean the torso takes on, on top of the crouch. */
    private static final float TORSO_LEAN_SHARE = 0.45F;
    /** Cap on the broom's contribution, in degrees, so a steep dive never folds the rider double. */
    private static final float MAX_TORSO_LEAN_DEG = 20.0F;

    private BroomRiderPoseHandler() {}

    public static void applyRidingPose(PlayerModel model, AvatarRenderState state) {
        if (!ModuleManager.isEnabled(Module.BROOM_FLIGHT)) {
            return;
        }
        BroomRiderRenderer.BroomRideData ride = BroomRiderRenderer.getRide(state);
        if (ride == null) {
            return;
        }

        HumanoidModel<?> humanoid = model;

        // forwardLean is negative for a nose-down tuck; a humanoid torso leans forward for a positive xRot.
        float broomLean = Mth.clamp(-ride.forwardLean() * TORSO_LEAN_SHARE, -MAX_TORSO_LEAN_DEG, MAX_TORSO_LEAN_DEG)
                * Mth.DEG_TO_RAD;
        float lean = Math.max(0.0F, BASE_LEAN + broomLean);

        humanoid.body.xRot = lean;
        humanoid.body.y += BODY_DROP_PER_RAD * lean;
        humanoid.head.y += HEAD_DROP_PER_RAD * lean;
        humanoid.rightArm.y += BODY_DROP_PER_RAD * lean;
        humanoid.leftArm.y += BODY_DROP_PER_RAD * lean;
        humanoid.rightLeg.z += LEG_BACK_PER_RAD * lean;
        humanoid.leftLeg.z += LEG_BACK_PER_RAD * lean;

        humanoid.rightLeg.xRot = LEG_X_ROT;
        humanoid.leftLeg.xRot = LEG_X_ROT;
        humanoid.rightLeg.yRot = 0.0F;
        humanoid.leftLeg.yRot = 0.0F;
        humanoid.rightLeg.zRot = LEG_Z_ROT;
        humanoid.leftLeg.zRot = -LEG_Z_ROT;

        humanoid.rightArm.xRot = RIGHT_ARM_X_ROT;
        humanoid.leftArm.xRot = LEFT_ARM_X_ROT;
        humanoid.rightArm.yRot = RIGHT_ARM_Y_ROT;
        humanoid.leftArm.yRot = LEFT_ARM_Y_ROT;
        humanoid.rightArm.zRot = 0.0F;
        humanoid.leftArm.zRot = 0.0F;
    }
}
