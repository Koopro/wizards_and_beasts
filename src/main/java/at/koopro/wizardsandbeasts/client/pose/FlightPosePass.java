package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Poses a flying player into one of three flight attitudes.
 *
 * <p>Procedural rather than keyframe by ruling R-1: the attitude is a function of live input — the
 * bank driven by yaw change, the cross-fade between two states — and a keyframe table cannot express
 * that without becoming an expression language.
 *
 * <p>Reads its state from {@link ClientPoseState}, which the server syncs, so a second player
 * watching sees the same attitude. Values come from {@link FlightPoseConstants}.
 *
 * <h2>Per player, not per pass</h2>
 *
 * <p>The timers live in a map keyed by entity id rather than in fields. One shared set of timers is
 * the obvious shape and it is wrong the moment two people are in the air together: the pass is a
 * singleton consulted once per rendered player, so every one of them would have been posed with the
 * local player's fade, bank and cross-fade. Single player hides it completely.
 */
@NullMarked
public final class FlightPosePass extends ProceduralPosePass {

    /** Locomotion band. Flight attitude competes with broom attitude, and they belong together. */
    public static final int PRIORITY = 120;

    /** Everything time-varying about one player's flight pose. */
    private static final class Tracked {
        final FadeTimer fade = new FadeTimer(
                FlightPoseConstants.FADE_IN_TICKS, FlightPoseConstants.FADE_OUT_TICKS);
        final PhaseTimer stateBlend = new PhaseTimer(FlightPoseConstants.STATE_BLEND_TICKS);

        @Nullable FlightPoseState fromState;
        @Nullable FlightPoseState toState;

        /** Previous tick's yaw, and whether it has been seeded — the first tick has no delta. */
        float previousYaw;
        boolean yawSeeded;

        /** Smoothed bank, current and previous tick, in degrees. */
        float bank;
        float previousBank;

        float bank(float partialTicks) {
            return Mth.lerp(Mth.clamp(partialTicks, 0f, 1f), previousBank, bank);
        }
    }

    private final Map<Integer, Tracked> tracked = new HashMap<>();

    public FlightPosePass() {
        super("flight", PRIORITY);
    }

    /**
     * Advances every visible player's timers. Called once per client tick, never from a render pass.
     *
     * <p>Separate from {@link #pose} because a render pass can run many times per tick — twice for a
     * first-person arm alone, and once more now that the body half is evaluated in
     * {@code setupRotations} — and a timer advanced from there would run at frame rate.
     */
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            tracked.clear();
            return;
        }

        Set<Integer> present = new HashSet<>();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            present.add(player.getId());
            tick(tracked.computeIfAbsent(player.getId(), id -> new Tracked()),
                    ClientPoseState.get(player.getUUID()),
                    // onGround rather than the flying ability: abilities are only synced for the
                    // local player, and a pose everyone else can see must be gated on something
                    // everyone else can see.
                    !player.onGround(),
                    player.getYRot());
        }
        tracked.keySet().retainAll(present);
    }

    /** One player's tick, with the client pulled out into arguments so a test can drive it. */
    void tick(Tracked state, PoseOverride override, boolean airborne, float yaw) {
        boolean active = override.active() && airborne;
        state.fade.tick(active);

        FlightPoseState desired = override.state().orElse(null);
        // A null desired state is a deactivation, not a state change. Clearing toState here is what
        // made landing snap: pose() returns early on a null toState, so the fade wound down with
        // nobody reading it and the pose vanished on the tick the player touched the ground instead
        // of easing out. The last state is held until the fade has actually finished.
        if (desired != null && desired != state.toState) {
            state.fromState = state.toState != null ? state.toState : desired;
            state.toState = desired;
            state.stateBlend.set(0);
        } else if (desired == null && state.fade.idle()) {
            state.toState = null;
            state.fromState = null;
        }
        state.stateBlend.tick(true);

        tickBank(state, active, yaw);
    }

    /**
     * Tracks the turn rate and smooths it into a bank angle.
     *
     * <p>Smoothed because mouse movement is not smooth at tick resolution — driven off the raw delta
     * the bank strobes on ordinary aiming jitter.
     */
    private static void tickBank(Tracked state, boolean active, float yaw) {
        state.previousBank = state.bank;

        if (!state.yawSeeded) {
            state.previousYaw = yaw;
            state.yawSeeded = true;
            return;
        }

        // Wrapped, or crossing the 180 degree seam reads as a full turn in one tick and slams the
        // bank to its clamp.
        float yawDelta = Mth.wrapDegrees(yaw - state.previousYaw);
        state.previousYaw = yaw;

        float target = active
                ? Mth.clamp(yawDelta * FlightPoseConstants.BANK_PER_YAW,
                        -FlightPoseConstants.BANK_CLAMP, FlightPoseConstants.BANK_CLAMP)
                : 0f;
        state.bank += (target - state.bank) / FlightPoseConstants.BANK_SMOOTH_TICKS;
    }

    @Override
    public void pose(PoseBuilder builder, PoseContext context) {
        Tracked state = tracked.get(context.state().id);
        if (state == null || state.toState == null || state.fade.idle()) {
            return;
        }
        float partial = context.partialTicks();
        float weight = state.fade.value(partial);
        if (weight <= 0f) {
            return;
        }

        if (context.firstPerson().firstPerson()) {
            poseFirstPerson(builder, context, state, weight, partial);
        } else {
            poseThirdPerson(builder, context, state, weight, partial);
        }
    }

    /**
     * The from → to cross-fade.
     *
     * <p>Eased rather than linear: a linear cross-fade between two static poses changes direction
     * abruptly at both ends, which reads as a flinch at the start of every state change.
     */
    private static float blend(Tracked state, float partialTicks) {
        return easeInOut(state.stateBlend.value(partialTicks));
    }

    private static float easeInOut(float t) {
        return t * t * (3f - 2f * t);
    }

    private FlightPoseConstants.FlightPose resolvePose(Tracked state, float partialTicks) {
        FlightPoseConstants.FlightPose target = FlightPoseConstants.thirdPerson(state.toState);
        if (state.fromState == null || state.fromState == state.toState || state.stateBlend.complete()) {
            return target;
        }
        return FlightPoseConstants.lerp(
                FlightPoseConstants.thirdPerson(state.fromState), target, blend(state, partialTicks));
    }

    private void poseThirdPerson(PoseBuilder builder, PoseContext context, Tracked tracking,
                                 float weight, float partial) {
        FlightPoseConstants.FlightPose pose = resolvePose(tracking, partial);
        float bank = pose.banks() ? tracking.bank(partial) : 0f;

        // Up to the pivot, rotate, back down: that is what makes the body pitch about its chest
        // rather than hinge at the ankles. The offsets are one value with opposite signs by
        // construction, so they cannot be left inconsistent. Blocks here, not model units — this is
        // the pose stack.
        builder.get(PlayerModelPart.BODY)
                .multiplier(weight)
                .set(PoseTarget.Y, pose.pivot())
                .setRotDeg(PoseTarget.Z_ROT, bank)
                .setRotDeg(PoseTarget.X_ROT, pose.bodyPitch())
                .set(PoseTarget.Y2, -pose.pivot());

        // Added, not set: it rides on top of the player's real look pitch so they keep aiming where
        // they are pointing. Negative lifts the gaze against the body's forward lean.
        builder.get(PlayerModelPart.HEAD)
                .multiplier(weight)
                .addRotDeg(PoseTarget.X_ROT, pose.headPitch());

        applyRotation(builder, PlayerModelPart.CHEST, pose.chest(), weight);

        // attackTime, not attackArm. `attackArm` is never null — it defaults to RIGHT and only says
        // *which* arm would swing — so testing it for null pinned the main arm at a fraction of the
        // flight pose and left two thirds of vanilla's walk swing on it the whole flight.
        boolean swinging = context.state().attackTime > 0f;
        HumanoidArm attackArm = context.state().attackArm;

        poseArm(builder, PlayerModelPart.RIGHT_ARM, pose.rightArm(), weight, bank,
                bank < 0f, swinging && attackArm == HumanoidArm.RIGHT);
        poseArm(builder, PlayerModelPart.LEFT_ARM, pose.leftArm(), weight, bank,
                bank > 0f, swinging && attackArm == HumanoidArm.LEFT);

        applyRotation(builder, PlayerModelPart.RIGHT_LEG, pose.rightLeg(), weight);
        applyRotation(builder, PlayerModelPart.LEFT_LEG, pose.leftLeg(), weight);
    }

    private static void applyRotation(PoseBuilder builder, PlayerModelPart part,
                                      FlightPoseConstants.PartRotation rotation, float weight) {
        builder.get(part)
                .multiplier(weight)
                .setRotDeg(PoseTarget.X_ROT, rotation.xRot())
                .setRotDeg(PoseTarget.Y_ROT, rotation.yRot())
                .setRotDeg(PoseTarget.Z_ROT, rotation.zRot());
    }

    /**
     * @param outer whether this is the arm on the outside of the turn, which opens further while the
     *              inner one tucks in
     * @param swinging whether this arm is mid-attack-swing
     */
    private static void poseArm(PoseBuilder builder, PlayerModelPart part,
                                FlightPoseConstants.PartRotation rotation,
                                float weight, float bank, boolean outer, boolean swinging) {
        // A swinging arm keeps only a fraction of the flight pose so the swing stays readable.
        float armWeight = swinging ? weight * FlightPoseConstants.ATTACK_ARM_MULTIPLIER : weight;
        float bankShare = bank * (outer
                ? FlightPoseConstants.BANK_OUTER_ARM : FlightPoseConstants.BANK_INNER_ARM);

        builder.get(part)
                .multiplier(armWeight)
                .setRotDeg(PoseTarget.X_ROT, rotation.xRot())
                .setRotDeg(PoseTarget.Y_ROT, rotation.yRot())
                .setRotDeg(PoseTarget.Z_ROT, rotation.zRot() + bankShare);
    }

    /**
     * First person poses only the arm on screen.
     *
     * <p>Body, head and legs are absent rather than zeroed: there is nothing to pose, and queueing
     * ops for parts that do not render would put them in the blend stack for no reason.
     */
    private void poseFirstPerson(PoseBuilder builder, PoseContext context, Tracked tracking,
                                 float weight, float partial) {
        PlayerModelPart part = context.firstPerson().part();
        if (part == null) {
            return;
        }

        FlightPoseConstants.FirstPersonPose pose = FlightPoseConstants.firstPerson(tracking.toState);
        if (tracking.fromState != null && tracking.fromState != tracking.toState
                && !tracking.stateBlend.complete()) {
            pose = FlightPoseConstants.firstPerson(tracking.fromState)
                    .lerp(pose, blend(tracking, partial));
        }

        // The table is written for the right arm; the off arm mirrors in yaw and roll.
        float side = part == PlayerModelPart.RIGHT_ARM ? 1f : -1f;

        builder.get(part)
                .multiplier(weight)
                .addRotDeg(PoseTarget.X_ROT, pose.xRot())
                .addRotDeg(PoseTarget.Y_ROT, pose.yRot() * side)
                .addRotDeg(PoseTarget.Z_ROT, pose.zRot() * side)
                // Blocks to model units, and negated: the table is written Y-up like the body pivot
                // beside it, while a ModelPart's +Y points down.
                .add(PoseTarget.Y, -pose.yOffset() * 16f);
    }

    /** True while the pass still has something to draw for this player — used by the debug readout. */
    public boolean posing(int entityId) {
        Tracked state = tracked.get(entityId);
        return state != null && state.toState != null && !state.fade.idle();
    }
}
