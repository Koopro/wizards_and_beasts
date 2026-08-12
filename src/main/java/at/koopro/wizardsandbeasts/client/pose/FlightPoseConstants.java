package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pose values for the three flight states.
 *
 * <p><b>THESE NUMBERS ARE PLACEHOLDERS AND ARE NOT AUTHORED ART.</b> Schema §7.3 requires every
 * rotation and offset to be authored against the vanilla player model and exported here as named
 * constants; those values do not exist yet. What is below is a deliberately coarse first
 * approximation whose only job is to make the plumbing visible in-game — the states differ enough to
 * tell apart, and nothing more. Tracked at BLOCKER in {@code AUDIT_PUNCHLIST.md}.
 *
 * <p>They are written in whole and half degrees on purpose. Tuned pose values do not come out as
 * round numbers, so a table of them reads as provisional at a glance rather than being mistaken for
 * something somebody sat down and dialled in.
 *
 * <p>Everything lives in this one class so the authored set can replace it without a single edit to
 * {@link FlightPosePass}. That separation is the deliverable; the numbers are not.
 *
 * <h2>Two sign conventions, and why the head field is a fraction</h2>
 *
 * <p>{@code bodyPitch} is applied to the <em>pose stack</em>, in {@code AvatarRenderer.setupRotations};
 * the limb rotations are applied to {@code ModelPart}s, which live on the far side of vanilla's
 * {@code scale(-1, -1, 1)}. That reflection negates the X and Y rotation axes, so the same visual
 * lean is a negative number in one frame and a positive number in the other.
 *
 * <p>Storing the head's counter-rotation as an angle meant storing it in the opposite sign to the
 * body pitch it cancels — which is exactly the mistake that shipped, and which read in-game as the
 * head cranking down instead of staying level. It is a fraction now: the pass multiplies it by the
 * pitch actually applied, so the two cannot drift apart and there is no sign left to get wrong.
 */
@NullMarked
public final class FlightPoseConstants {

    private FlightPoseConstants() {}

    /**
     * One state's pose.
     *
     * <p>Rotations are degrees. {@code pitchPivot} is <b>blocks</b>, not the sixteenths a
     * {@code ModelPart} uses — it is applied to the pose stack, where one unit is one block.
     *
     * @param bodyPitch   whole-avatar pitch. Negative leans forward, matching vanilla's own elytra
     *                    pitch; -90 would be fully prone
     * @param pitchPivot  height above the feet that the body pitches about, in blocks. The pass
     *                    emits it as a matched offset pair either side of the rotation, so a value
     *                    of 0 pivots at the feet and ~1 pivots at the chest
     * @param headCounter how much of the body pitch the head cancels: 1 keeps the player looking
     *                    exactly where they aim, 0 lets the head go wherever the body puts it
     * @param armPitch    both arms, forward from the shoulder. Negative raises them forward
     * @param armSplay    both arms, outward — mirrored left/right by the pass
     * @param legPitch    both legs, trailing back from the hip
     * @param legSplay    both legs, outward — mirrored left/right by the pass
     */
    public record FlightPose(
            float bodyPitch, float pitchPivot, float headCounter,
            float armPitch, float armSplay,
            float legPitch, float legSplay) {}

    // ── PLACEHOLDER third-person values ──────────────────────────────────────

    private static final Map<FlightPoseState, FlightPose> THIRD_PERSON = new EnumMap<>(FlightPoseState.class);
    private static final Map<FlightPoseState, FlightPose> FIRST_PERSON = new EnumMap<>(FlightPoseState.class);

    /** Roughly chest height on a 1.8-block player: where a body plausibly pivots. */
    private static final float CHEST = 1.0f;

    static {
        // Upright, arms low, legs together — barely different from standing, which is the point of
        // a hover.
        THIRD_PERSON.put(FlightPoseState.HOVER, new FlightPose(
                -10f, CHEST, 1f, -20f, 5f, 5f, 3f));

        // Tilted into travel, arms forward, legs trailing.
        THIRD_PERSON.put(FlightPoseState.GLIDE, new FlightPose(
                -35f, CHEST, 1f, -60f, 8f, 15f, 5f));

        // Near-horizontal, arms extended ahead, legs straight back: the Quidditch dive. The head
        // stops fully counter-rotating here — at this pitch a head held perfectly level reads as
        // detached from the body rather than as looking where it is going.
        THIRD_PERSON.put(FlightPoseState.PROPELLED, new FlightPose(
                -70f, CHEST, 0.85f, -110f, 6f, 25f, 3f));

        // ── PLACEHOLDER first-person values ─────────────────────────────────
        //
        // A separate set rather than a reuse of the above, because §3.5 makes first person a
        // required branch: a shoulder rotation that reads correctly in third person puts the held
        // wand through the camera. The body fields are unused in first person — there is no torso on
        // screen and the pose stack transform never runs for the hand — and are held at zero rather
        // than omitted so the record shape stays one thing.
        FIRST_PERSON.put(FlightPoseState.HOVER, new FlightPose(
                0f, 0f, 0f, -8f, 3f, 0f, 0f));
        FIRST_PERSON.put(FlightPoseState.GLIDE, new FlightPose(
                0f, 0f, 0f, -18f, 5f, 0f, 0f));
        FIRST_PERSON.put(FlightPoseState.PROPELLED, new FlightPose(
                0f, 0f, 0f, -30f, 4f, 0f, 0f));
    }

    public static FlightPose of(FlightPoseState state, boolean firstPerson) {
        return (firstPerson ? FIRST_PERSON : THIRD_PERSON).get(state);
    }

    /** Blends two poses componentwise. Used for the from-state → to-state transition. */
    public static FlightPose lerp(FlightPose from, FlightPose to, float t) {
        return new FlightPose(
                lerp(from.bodyPitch(), to.bodyPitch(), t),
                lerp(from.pitchPivot(), to.pitchPivot(), t),
                lerp(from.headCounter(), to.headCounter(), t),
                lerp(from.armPitch(), to.armPitch(), t),
                lerp(from.armSplay(), to.armSplay(), t),
                lerp(from.legPitch(), to.legPitch(), t),
                lerp(from.legSplay(), to.legSplay(), t));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ── tuning ───────────────────────────────────────────────────────────────

    /** Ticks to cross-fade between two flight states. */
    public static final int STATE_BLEND_TICKS = 6;

    /** Ticks for the whole pose to ease in when the override activates, and out when it clears. */
    public static final int FADE_TICKS = 8;

    /** Ticks for propulsion to ramp while the sprint key is held in flight. */
    public static final int PROPULSION_TICKS = 10;

    /**
     * How much of the flight pose the swinging arm keeps.
     *
     * <p>Reduced rather than suppressed: dropping the arm out of the pose entirely makes it snap to
     * the flight attitude the instant the swing ends, which reads worse than a swing that is merely
     * shallower than it would be on the ground.
     */
    public static final float ATTACK_ARM_MULTIPLIER = 0.35f;
}
