package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.client.broom.BroomRiderRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.NullMarked;

/**
 * Poses the casting arm through a cast's three phases.
 *
 * <p>Casting band, so it stacks on top of flight: a wizard casting from a broom keeps the flight
 * attitude and gets the cast on the arm, which is the whole reason the bands exist.
 *
 * <p>Reads {@link ClientCastAnimationState}, which the server fills over
 * {@code SpellCastAnimationS2CPayload}. Phases come from {@code PhaseTimer.between} over the synced
 * progress — the pass never defines its own timeline, per {@code WAND_CAST_POSE_SCHEMA} D2.
 *
 * <h2>No timers of its own</h2>
 *
 * <p>Unlike {@link FlightPosePass} this pass holds no per-player state at all. A cast has a start
 * tick and a duration that arrived on the wire, so progress is a pure function of the current tick —
 * there is nothing to ramp locally and therefore nothing to keep per player and nothing to leak. The
 * fade at each end is derived from the same progress rather than from a separate timer.
 */
@NullMarked
public final class CastPosePass extends ProceduralPosePass {

    /** Casting band. Above locomotion so a cast reads over a flight attitude, not under it. */
    public static final int PRIORITY = 200;

    public CastPosePass() {
        super("cast", PRIORITY);
    }

    @Override
    public void pose(PoseBuilder builder, PoseContext context) {
        long now = ClientCastAnimationState.clientTick();
        ClientCastAnimationState.Resolved resolved =
                ClientCastAnimationState.resolve(context.state().id, now, now + context.partialTicks());
        if (resolved == null || resolved.weight() <= 0f) {
            return;
        }
        float weight = resolved.weight();
        CastPoseConstants.CastPose pose = resolved.pose();
        HumanoidArm arm = context.state().mainArm;

        if (context.firstPerson().firstPerson()) {
            poseFirstPerson(builder, context, pose, weight);
            return;
        }

        // D7: on a broom the free hand keeps the shaft and the torso twists less, or the rider lets go of the broom
        // to throw a spell with their whole body.
        if (BroomRiderRenderer.getRide(context.state()) != null) {
            pose = pose.mounted(CastPoseConstants.MOUNTED_CHEST_SHARE);
        }

        PlayerModelPart armPart = arm == HumanoidArm.RIGHT
                ? PlayerModelPart.RIGHT_ARM : PlayerModelPart.LEFT_ARM;
        // The table is written for a right-handed cast; a left-hander mirrors yaw and roll.
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;

        double time = now + context.partialTicks();
        float charge = charge(resolved.cast(), time);
        // The charge: a held gather keeps rising a little and starts to tremble, so holding reads as building up.
        float tremor = charge * CastPoseConstants.CHARGE_TREMOR_DEG;
        float raise = charge * CastPoseConstants.CHARGE_RAISE_DEG;
        float shakeX = (float) Math.sin(time * 2.3) * tremor;
        float shakeZ = (float) Math.cos(time * 3.1) * tremor * 0.7f;

        // The look pitch rides on the arm by the pose's aim share, so a strike points where the caster looks
        // (positive pitch looks down, and a larger xRot swings the arm down: the same sign vanilla's bow uses).
        builder.get(armPart)
                .multiplier(weight)
                .setRotDeg(PoseTarget.X_ROT,
                        pose.castingArm().xRot() + pose.aim() * context.state().xRot - raise + shakeX)
                .setRotDeg(PoseTarget.Y_ROT, pose.castingArm().yRot() * side)
                .setRotDeg(PoseTarget.Z_ROT, (pose.castingArm().zRot() + shakeZ) * side);

        // The free arm, for the two-handed sets (guard, Patronus). Its own share, so it can join late and leave
        // early inside one cast.
        if (pose.offShare() > 0f) {
            PlayerModelPart offPart = arm == HumanoidArm.RIGHT
                    ? PlayerModelPart.LEFT_ARM : PlayerModelPart.RIGHT_ARM;
            builder.get(offPart)
                    .multiplier(weight * pose.offShare())
                    .setRotDeg(PoseTarget.X_ROT, pose.offArm().xRot())
                    .setRotDeg(PoseTarget.Y_ROT, pose.offArm().yRot() * side)
                    .setRotDeg(PoseTarget.Z_ROT, pose.offArm().zRot() * side);
        }

        // Added, not set: the torso twist rides on whatever vanilla and the flight pass already did,
        // so a cast while gliding twists the glide rather than replacing it.
        builder.get(PlayerModelPart.CHEST)
                .multiplier(weight)
                .addRotDeg(PoseTarget.Y_ROT, pose.chest().yRot() * side)
                .addRotDeg(PoseTarget.X_ROT, pose.chest().xRot());

        builder.get(PlayerModelPart.HEAD)
                .multiplier(weight)
                .addRotDeg(PoseTarget.X_ROT, pose.headPitch());
    }

    /**
     * 0..1 how long a gather has been held, toward {@link CastPoseConstants#CHARGE_TICKS}. Zero for anything but a
     * held gather: a beam already holds its strike, and a timed cast has its own timeline.
     */
    static float charge(ClientCastAnimationState.@org.jspecify.annotations.Nullable ActiveCast cast, double time) {
        if (cast == null || cast.holdPhase() != 0) {
            return 0f;
        }
        float held = (float) (time - cast.startTick());
        return Math.max(0f, Math.min(1f, held / CastPoseConstants.CHARGE_TICKS));
    }

    /**
     * First person poses the bare arm on screen, and only when it is the casting arm.
     *
     * <p>Only reached when the arm itself is drawn, which is a wandless cast: holding a wand, vanilla draws the item
     * and not the arm, and {@link FirstPersonCast} moves the wand instead. The off hand does not cast, so posing it
     * would animate the wrong arm in the one view where the player is looking straight at it.
     */
    private void poseFirstPerson(PoseBuilder builder, PoseContext context,
                                 CastPoseConstants.CastPose pose, float weight) {
        PlayerModelPart part = context.firstPerson().part();
        if (part == null || !context.firstPerson().mainArm(context.state().mainArm)) {
            return;
        }
        float side = context.state().mainArm == HumanoidArm.RIGHT ? 1f : -1f;

        // Added rather than set, and at a reduced share: a full third-person shoulder rotation swings
        // the hand out of the viewport entirely.
        builder.get(part)
                .multiplier(weight * FIRST_PERSON_SHARE)
                .addRotDeg(PoseTarget.X_ROT, pose.castingArm().xRot())
                .addRotDeg(PoseTarget.Y_ROT, pose.castingArm().yRot() * side)
                .addRotDeg(PoseTarget.Z_ROT, pose.castingArm().zRot() * side);
    }

    /** How much of the third-person arm rotation a bare first-person arm keeps, so the hand stays on screen. */
    private static final float FIRST_PERSON_SHARE = 0.4f;
}
