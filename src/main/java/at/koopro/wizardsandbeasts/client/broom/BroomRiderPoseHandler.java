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

    /** Legs hang slightly forward of vertical — astride the shaft, not stiff. */
    private static final float LEG_X_ROT = -0.15F;
    /** Splay, so the legs read as either side of the broom rather than fused together. */
    private static final float LEG_Z_ROT = 0.10F;
    /** Arms reach forward and down onto the handle. */
    private static final float ARM_X_ROT = -1.15F;
    private static final float ARM_Z_ROT = 0.12F;
    /** How much of the broom's forward lean the torso takes on. Under 1 so the rider still reads as
     *  bracing against the broom rather than being welded to it. */
    private static final float TORSO_LEAN_SHARE = 0.45F;
    /** Cap on the torso contribution, in degrees, so a steep dive never folds the rider double. */
    private static final float MAX_TORSO_LEAN_DEG = 25.0F;

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

        humanoid.rightLeg.xRot = LEG_X_ROT;
        humanoid.leftLeg.xRot = LEG_X_ROT;
        humanoid.rightLeg.yRot = 0.0F;
        humanoid.leftLeg.yRot = 0.0F;
        humanoid.rightLeg.zRot = LEG_Z_ROT;
        humanoid.leftLeg.zRot = -LEG_Z_ROT;

        humanoid.rightArm.xRot = ARM_X_ROT;
        humanoid.leftArm.xRot = ARM_X_ROT;
        humanoid.rightArm.yRot = 0.0F;
        humanoid.leftArm.yRot = 0.0F;
        humanoid.rightArm.zRot = ARM_Z_ROT;
        humanoid.leftArm.zRot = -ARM_Z_ROT;

        // The body part is normally left at zero by HumanoidModel; leaning it here tips the torso
        // without moving the head, which keeps the rider looking where the player is aiming.
        float leanDeg = Mth.clamp(ride.forwardLean() * TORSO_LEAN_SHARE,
                -MAX_TORSO_LEAN_DEG, MAX_TORSO_LEAN_DEG);
        humanoid.body.xRot = leanDeg * Mth.DEG_TO_RAD;
    }
}
