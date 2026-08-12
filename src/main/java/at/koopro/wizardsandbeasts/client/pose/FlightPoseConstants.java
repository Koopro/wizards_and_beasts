package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * Authored pose values for the three flight states, filling schema §7.3.
 *
 * <p>Transcribed from {@code documentation/FLIGHT_POSE_CONSTANTS.md}. A tuned starting point rather
 * than a final set — they exist so in-game verification tests the blend instead of testing
 * obviously-wrong placeholders — but they are authored, which the previous set explicitly was not.
 *
 * <h2>Conventions</h2>
 *
 * <p>Everything is degrees. Limb {@code xRot} positive pitches a limb backwards, negative swings it
 * forward: an arm at {@code -90} points straight ahead. Arm {@code zRot} mirrors between sides and
 * both sides are written out, because the legs are deliberately asymmetric and a pass that mirrored
 * one side onto the other could not express them.
 *
 * <h2>The one value corrected from the source document</h2>
 *
 * <p><b>{@code headPitch} is negated relative to the table.</b> The document gives {@code +2 / +22 /
 * +58} and states the purpose: "so the player still looks where they are going rather than at the
 * ground". In Minecraft that requires a negative number. Vanilla sets {@code head.xRot = state.xRot *
 * DEG_TO_RAD} and entity pitch is positive looking <em>down</em>, so a positive head rotation tips
 * the gaze further into the ground — on a body already pitched forward, doubling the problem the
 * value is there to solve. The magnitudes are the author's; only the sign is changed, and it is
 * changed to match what the document says the field is for.
 *
 * <p>The trap behind it is that {@code bodyPitch} and the limb rotations are in different frames:
 * the body is a pose stack transform applied in {@code AvatarRenderer.setupRotations}, the limbs are
 * {@code ModelPart}s on the far side of vanilla's {@code scale(-1, -1, 1)}, and that reflection
 * negates the X rotation axis. A head compensation therefore carries the <em>same</em> sign as the
 * body pitch it cancels, which reads as wrong every time.
 */
@NullMarked
public final class FlightPoseConstants {

    private FlightPoseConstants() {}

    /** One part's rotation, in degrees. */
    public record PartRotation(float xRot, float yRot, float zRot) {

        public static final PartRotation NONE = new PartRotation(0f, 0f, 0f);

        public PartRotation lerp(PartRotation to, float t) {
            return new PartRotation(
                    FlightPoseConstants.lerp(xRot, to.xRot, t),
                    FlightPoseConstants.lerp(yRot, to.yRot, t),
                    FlightPoseConstants.lerp(zRot, to.zRot, t));
        }
    }

    /**
     * One state's third-person pose.
     *
     * @param bodyPitch  whole-avatar pitch. Negative leans forward; -90 would be fully prone
     * @param pivot      height above the feet the body pitches about, in <b>blocks</b> — it is a pose
     *                   stack offset, not the sixteenths a {@code ModelPart} uses. Emitted as a
     *                   matched pair either side of the rotation
     * @param headPitch  added to the player's real look pitch. Negative lifts the gaze; see the class
     *                   note on why that is the compensating direction
     * @param banks      whether this state banks into turns. HOVER does not
     */
    public record FlightPose(
            float bodyPitch, float pivot, float headPitch, boolean banks,
            PartRotation chest,
            PartRotation rightArm, PartRotation leftArm,
            PartRotation rightLeg, PartRotation leftLeg) {}

    /**
     * One state's first-person arm, given for the right arm.
     *
     * <p>Not the third-person values: a shoulder rotation that reads correctly in third person puts
     * the hand outside the viewport. {@code yRot} and {@code zRot} mirror for the off arm.
     *
     * @param yOffset drop applied as speed rises so the arm clears the crosshair, in <b>blocks</b>.
     *                The source table gives -0.06 and -0.14, which are far too small to see as model
     *                units; blocks is the only reading under which the field does what its note says
     */
    public record FirstPersonPose(float xRot, float yRot, float zRot, float yOffset) {

        public FirstPersonPose lerp(FirstPersonPose to, float t) {
            return new FirstPersonPose(
                    FlightPoseConstants.lerp(xRot, to.xRot, t),
                    FlightPoseConstants.lerp(yRot, to.yRot, t),
                    FlightPoseConstants.lerp(zRot, to.zRot, t),
                    FlightPoseConstants.lerp(yOffset, to.yOffset, t));
        }
    }

    private static final Map<FlightPoseState, FlightPose> THIRD_PERSON = new EnumMap<>(FlightPoseState.class);
    private static final Map<FlightPoseState, FirstPersonPose> FIRST_PERSON = new EnumMap<>(FlightPoseState.class);

    static {
        // Upright, treading air. The asymmetric legs are intentional: a perfectly symmetrical idle
        // reads as a mannequin, and the offset makes it read as a person holding position.
        THIRD_PERSON.put(FlightPoseState.HOVER, new FlightPose(
                -8f, 0.35f, -2f, false,
                PartRotation.NONE,
                new PartRotation(-12f, 0f, 18f),
                new PartRotation(-12f, 0f, -18f),
                new PartRotation(-14f, 0f, 4f),
                new PartRotation(-4f, 0f, -4f)));

        // Leaning into travel, arms out.
        THIRD_PERSON.put(FlightPoseState.GLIDE, new FlightPose(
                -35f, 0.75f, -22f, true,
                new PartRotation(-6f, 0f, 0f),
                new PartRotation(-38f, -12f, 52f),
                new PartRotation(-38f, 12f, -52f),
                new PartRotation(12f, 0f, 6f),
                new PartRotation(16f, 0f, -6f)));

        // Swept back, committed. The arms go positive here — back along the body, not out front —
        // and the legs run nearly straight and trailing.
        //
        // -78 rather than -90 on purpose: a fully horizontal body reads as a rigid plank, and a few
        // degrees short keeps the head leading and the silhouette alive.
        THIRD_PERSON.put(FlightPoseState.PROPELLED, new FlightPose(
                -78f, 0.75f, -58f, true,
                new PartRotation(-10f, 0f, 0f),
                new PartRotation(28f, -8f, 14f),
                new PartRotation(28f, 8f, -14f),
                new PartRotation(6f, 0f, 3f),
                new PartRotation(9f, 0f, -3f)));

        FIRST_PERSON.put(FlightPoseState.HOVER, new FirstPersonPose(-4f, 0f, 6f, 0f));
        FIRST_PERSON.put(FlightPoseState.GLIDE, new FirstPersonPose(-15f, -6f, 22f, -0.06f));
        FIRST_PERSON.put(FlightPoseState.PROPELLED, new FirstPersonPose(10f, -4f, 9f, -0.14f));
    }

    public static FlightPose thirdPerson(FlightPoseState state) {
        return THIRD_PERSON.get(state);
    }

    public static FirstPersonPose firstPerson(FlightPoseState state) {
        return FIRST_PERSON.get(state);
    }

    /** Blends two third-person poses componentwise, for the state transition. */
    public static FlightPose lerp(FlightPose from, FlightPose to, float t) {
        return new FlightPose(
                lerp(from.bodyPitch(), to.bodyPitch(), t),
                lerp(from.pivot(), to.pivot(), t),
                lerp(from.headPitch(), to.headPitch(), t),
                // Not blended: a boolean has no midpoint. The target state decides, so banking
                // begins the moment a banking state is selected rather than fading in behind it.
                to.banks(),
                from.chest().lerp(to.chest(), t),
                from.rightArm().lerp(to.rightArm(), t),
                from.leftArm().lerp(to.leftArm(), t),
                from.rightLeg().lerp(to.rightLeg(), t),
                from.leftLeg().lerp(to.leftLeg(), t));
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ── blending (§6) ────────────────────────────────────────────────────────

    /** Ticks to cross-fade between two flight states, eased in and out. */
    public static final int STATE_BLEND_TICKS = 6;

    /** Ticks for the pose to ease in when the override activates. */
    public static final int FADE_IN_TICKS = 8;

    /** Ticks to ease out again. Shorter than the fade in: releasing should feel quicker than committing. */
    public static final int FADE_OUT_TICKS = 5;

    // ── bank into turns (§4) ─────────────────────────────────────────────────

    /** Degrees of bank per degree of yaw change per tick. */
    public static final float BANK_PER_YAW = 2.6f;

    /** Bank ceiling, either way. */
    public static final float BANK_CLAMP = 30f;

    /** Share of the bank added to the outer arm. */
    public static final float BANK_OUTER_ARM = 0.35f;

    /** Share of the bank taken off the inner arm. */
    public static final float BANK_INNER_ARM = -0.20f;

    /**
     * Ticks of smoothing on the yaw delta before it drives the bank.
     *
     * <p>Mouse movement is not smooth at tick resolution. Driving the bank off the raw delta makes it
     * strobe on ordinary aiming jitter.
     */
    public static final int BANK_SMOOTH_TICKS = 4;

    /**
     * How much of the flight pose the swinging arm keeps.
     *
     * <p>Reduced rather than suppressed: dropping the arm out of the pose entirely makes it snap to
     * the flight attitude the instant the swing ends, which reads worse than a swing that is merely
     * shallower than it would be on the ground.
     */
    public static final float ATTACK_ARM_MULTIPLIER = 0.35f;
}
