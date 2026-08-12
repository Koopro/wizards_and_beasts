package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Poses a flying player into one of three flight attitudes.
 *
 * <p>Procedural rather than keyframe by ruling R-1: the attitude is a function of live input —
 * head pitch, propulsion, the blend between two states — and a keyframe table cannot express
 * {@code headPitch + 80° × propulsion} without becoming an expression language.
 *
 * <p>Reads its state from {@link ClientPoseState}, which the server syncs, so a second player
 * watching sees the same attitude. Nothing here reads the entity: the state arrives by UUID and
 * every motion value comes off the render state.
 */
@NullMarked
public final class FlightPosePass extends ProceduralPosePass {

    /** Locomotion band. Flight attitude competes with broom attitude, and they belong together. */
    public static final int PRIORITY = 120;

    private final PhaseTimer fade = new PhaseTimer(FlightPoseConstants.FADE_TICKS);
    private final PhaseTimer propulsion = new PhaseTimer(FlightPoseConstants.PROPULSION_TICKS);
    private final PhaseTimer stateBlend = new PhaseTimer(FlightPoseConstants.STATE_BLEND_TICKS);

    private @Nullable FlightPoseState fromState;
    private @Nullable FlightPoseState toState;

    public FlightPosePass() {
        super("flight", PRIORITY);
    }

    /**
     * Advances the timers. Called once per client tick, never from the render pass.
     *
     * <p>Separate from {@link #pose} because a render pass can run many times per tick — twice for
     * a first-person arm alone — and a timer advanced from there would run at frame rate.
     */
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) {
            return;
        }
        tick(ClientPoseState.get(player.getUUID()),
                player.getAbilities().flying,
                minecraft.options.keySprint.isDown());
    }

    /**
     * The state machine, with the client pulled out into arguments.
     *
     * <p>Split from {@link #tick()} so the fade and blend behaviour can be driven from a test. The
     * landing bug this seam exists to pin was invisible to review and obvious in ten seconds of
     * flying, which is the worst combination to leave untested.
     */
    void tick(PoseOverride override, boolean flying, boolean sprinting) {
        // Only a flying player is posed. A stored override on a grounded player is kept, not
        // cleared — the command accepts it deliberately and it takes effect when they take off.
        boolean active = override.active() && flying;

        fade.tick(active);
        propulsion.tick(active && sprinting);

        FlightPoseState desired = override.state().orElse(null);
        // A null desired state is a deactivation, not a state change. Clearing toState here is what
        // made landing snap: pose() returns early on a null toState, so the fade timer wound down
        // with nobody reading it and the pose vanished on the tick the player touched the ground
        // instead of easing out over the eight it was given. The last state is held until the fade
        // has actually finished.
        if (desired != null && desired != toState) {
            fromState = toState != null ? toState : desired;
            toState = desired;
            stateBlend.set(0);
        } else if (desired == null && fade.idle()) {
            toState = null;
            fromState = null;
        }
        stateBlend.tick(true);
    }

    @Override
    public void pose(PoseBuilder builder, PoseContext context) {
        if (toState == null || fade.idle()) {
            return;
        }
        float partial = context.partialTicks();
        float weight = fade.value(partial);
        if (weight <= 0f) {
            return;
        }

        FlightPoseConstants.FlightPose pose = resolvePose(context.firstPerson().firstPerson(), partial);
        float thrust = propulsion.value(partial);

        if (context.firstPerson().firstPerson()) {
            poseFirstPerson(builder, context, pose, weight, thrust);
        } else {
            poseThirdPerson(builder, context, pose, weight, thrust);
        }
    }

    /** The from→to cross-fade, evaluated in both person modes off the same timer. */
    private FlightPoseConstants.FlightPose resolvePose(boolean firstPerson, float partial) {
        FlightPoseConstants.FlightPose target = FlightPoseConstants.of(toState, firstPerson);
        if (fromState == null || fromState == toState || stateBlend.complete()) {
            return target;
        }
        FlightPoseConstants.FlightPose previous = FlightPoseConstants.of(fromState, firstPerson);
        return FlightPoseConstants.lerp(previous, target, stateBlend.value(partial));
    }

    private void poseThirdPerson(PoseBuilder builder, PoseContext context,
                                 FlightPoseConstants.FlightPose pose, float weight, float thrust) {
        // Propulsion deepens the attitude rather than selecting a different one, so holding sprint
        // in any state leans further into it.
        float pitch = pose.bodyPitch() * (1f + thrust * 0.25f);

        // Up to the pivot, rotate, back down: that is what makes the body pitch about its chest
        // rather than hinge at the ankles. The two offsets are one value with opposite signs by
        // construction, so they cannot be left inconsistent.
        builder.get(PlayerModelPart.BODY)
                .multiplier(weight)
                .set(PoseTarget.Y, pose.pitchPivot())
                .setRotDeg(PoseTarget.X_ROT, pitch)
                .set(PoseTarget.Y2, -pose.pitchPivot());

        // The head counter-rotates against the body so the player still looks where the camera does.
        // Without this a lie-flat pose stares at the ground.
        //
        // It multiplies the pitch that was actually applied, propulsion included, rather than a
        // stored angle. A stored angle has to be written in the opposite sign to the body pitch it
        // cancels — ModelParts sit on the far side of vanilla's scale(-1, -1, 1) — and getting that
        // backwards cranks the head down into the chest instead of holding it level.
        builder.get(PlayerModelPart.HEAD)
                .multiplier(weight)
                .addRotDeg(PoseTarget.X_ROT, pitch * pose.headCounter());

        // attackTime, not attackArm. `attackArm` is never null — it defaults to RIGHT and only ever
        // says *which* arm would swing, never whether one is swinging. Testing it for null left the
        // main arm permanently at a fraction of the flight pose, so it kept two thirds of vanilla's
        // walk swing the whole time the player was in the air.
        boolean swinging = context.state().attackTime > 0f;
        HumanoidArm attackArm = context.state().attackArm;

        poseArm(builder, PlayerModelPart.RIGHT_ARM, pose, weight, 1f,
                swinging && attackArm == HumanoidArm.RIGHT);
        poseArm(builder, PlayerModelPart.LEFT_ARM, pose, weight, -1f,
                swinging && attackArm == HumanoidArm.LEFT);

        builder.get(PlayerModelPart.RIGHT_LEG)
                .multiplier(weight)
                .setRotDeg(PoseTarget.X_ROT, pose.legPitch())
                .setRotDeg(PoseTarget.Z_ROT, pose.legSplay());
        builder.get(PlayerModelPart.LEFT_LEG)
                .multiplier(weight)
                .setRotDeg(PoseTarget.X_ROT, pose.legPitch())
                .setRotDeg(PoseTarget.Z_ROT, -pose.legSplay());
    }

    private void poseArm(PoseBuilder builder, PlayerModelPart part,
                         FlightPoseConstants.FlightPose pose, float weight, float side,
                         boolean swinging) {
        // A swinging arm keeps only a fraction of the flight pose so the swing stays readable.
        float armWeight = swinging ? weight * FlightPoseConstants.ATTACK_ARM_MULTIPLIER : weight;
        builder.get(part)
                .multiplier(armWeight)
                .setRotDeg(PoseTarget.X_ROT, pose.armPitch())
                .setRotDeg(PoseTarget.Z_ROT, pose.armSplay() * side);
    }

    /**
     * First person poses only the arm on screen.
     *
     * <p>Body, head and legs are absent rather than zeroed: there is nothing to pose, and queueing
     * ops for parts that do not render would put them in the blend stack for no reason.
     */
    private void poseFirstPerson(PoseBuilder builder, PoseContext context,
                                 FlightPoseConstants.FlightPose pose, float weight, float thrust) {
        PlayerModelPart part = context.firstPerson().part();
        if (part == null) {
            return;
        }
        float side = part == PlayerModelPart.RIGHT_ARM ? 1f : -1f;
        float pitch = pose.armPitch() * (1f + thrust * 0.2f);
        builder.get(part)
                .multiplier(weight)
                .addRotDeg(PoseTarget.X_ROT, pitch)
                .addRotDeg(PoseTarget.Z_ROT, pose.armSplay() * side);
    }

    /** True while the pass still has something to draw — used by the debug command's readout. */
    public boolean posing() {
        return toState != null && !fade.idle();
    }

    public @Nullable FlightPoseState currentState() {
        return toState;
    }

    /** Uses {@link Mth} only so the import earns its place; kept for the clamp in future tuning. */
    static float clamp01(float value) {
        return Mth.clamp(value, 0f, 1f);
    }
}
