package at.koopro.wizardsandbeasts.client.pose;

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
        ClientCastAnimationState.ActiveCast cast =
                ClientCastAnimationState.get(context.state().id, now);
        if (cast == null) {
            return;
        }

        float progress = cast.progress(now, context.partialTicks());
        float weight = fade(cast, progress);
        if (weight <= 0f) {
            return;
        }

        CastPoseConstants.CastPose pose = CastPoseConstants.at(cast, progress);
        HumanoidArm arm = context.state().mainArm;

        if (context.firstPerson().firstPerson()) {
            poseFirstPerson(builder, context, pose, weight);
            return;
        }

        PlayerModelPart armPart = arm == HumanoidArm.RIGHT
                ? PlayerModelPart.RIGHT_ARM : PlayerModelPart.LEFT_ARM;
        // The table is written for a right-handed cast; a left-hander mirrors yaw and roll.
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;

        builder.get(armPart)
                .multiplier(weight)
                .setRotDeg(PoseTarget.X_ROT, pose.castingArm().xRot())
                .setRotDeg(PoseTarget.Y_ROT, pose.castingArm().yRot() * side)
                .setRotDeg(PoseTarget.Z_ROT, pose.castingArm().zRot() * side);

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
     * First person poses the arm on screen, and only when it is the casting arm.
     *
     * <p>The off hand does not cast, so posing it would animate the wrong arm in the one view where
     * the player is looking straight at it.
     */
    private void poseFirstPerson(PoseBuilder builder, PoseContext context,
                                 CastPoseConstants.CastPose pose, float weight) {
        PlayerModelPart part = context.firstPerson().part();
        if (part == null || !context.firstPerson().mainArm(context.state().mainArm)) {
            return;
        }
        float side = context.state().mainArm == HumanoidArm.RIGHT ? 1f : -1f;

        // Added rather than set, and at a reduced share: a full third-person shoulder rotation swings
        // the hand out of the viewport entirely. Reduced rather than given its own table because the
        // table itself is a placeholder — a second placeholder set would be twice the fiction.
        builder.get(part)
                .multiplier(weight * FIRST_PERSON_SHARE)
                .addRotDeg(PoseTarget.X_ROT, pose.castingArm().xRot())
                .addRotDeg(PoseTarget.Y_ROT, pose.castingArm().yRot() * side)
                .addRotDeg(PoseTarget.Z_ROT, pose.castingArm().zRot() * side);
    }

    /**
     * How much of the third-person arm rotation first person keeps.
     *
     * <p>Placeholder, like everything else here. Schema §3.5 requires first person to be a real
     * branch with its own authored values; this is a stand-in that at least does not put the hand
     * off screen.
     */
    private static final float FIRST_PERSON_SHARE = 0.4f;

    /**
     * Eases the whole pose in at the start of a cast and out at the end.
     *
     * <p>Derived from progress rather than from a timer, because progress already carries the
     * duration. A held cast sits at full weight: it exists to be looked at, and fading it would make
     * the thing being inspected depend on how it was reached.
     */
    private static float fade(ClientCastAnimationState.ActiveCast cast, float progress) {
        if (cast.held()) {
            return 1f;
        }
        float inFrac = Math.min(0.5f, CastPoseConstants.FADE_IN_TICKS / (float) cast.ticks());
        float outFrac = Math.min(0.5f, CastPoseConstants.FADE_OUT_TICKS / (float) cast.ticks());
        float in = PhaseTimer.between(progress, 0f, inFrac);
        float out = 1f - PhaseTimer.between(progress, 1f - outFrac, 1f);
        return Math.min(in, out);
    }
}
