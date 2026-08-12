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
 */
@NullMarked
public final class FlightPoseConstants {

    private FlightPoseConstants() {}

    /**
     * One state's pose, in degrees.
     *
     * @param bodyPitch  whole-avatar pitch — the lie-flat angle
     * @param bodyY      vertical offset applied before the pitch
     * @param bodyY2     vertical offset applied after it, shifting the pivot along the pitched axis
     * @param headPitch  head counter-rotation, so the player keeps looking where the camera points
     * @param armPitch   both arms, forward from the shoulder
     * @param armSplay   both arms, outward — mirrored left/right by the pass
     * @param legPitch   both legs, back from the hip
     * @param legSplay   both legs, outward — mirrored left/right by the pass
     */
    public record FlightPose(
            float bodyPitch, float bodyY, float bodyY2,
            float headPitch,
            float armPitch, float armSplay,
            float legPitch, float legSplay) {}

    // ── PLACEHOLDER third-person values ──────────────────────────────────────

    private static final Map<FlightPoseState, FlightPose> THIRD_PERSON = new EnumMap<>(FlightPoseState.class);
    private static final Map<FlightPoseState, FlightPose> FIRST_PERSON = new EnumMap<>(FlightPoseState.class);

    static {
        // Upright, arms low, legs together — barely different from standing, which is the point of
        // a hover.
        THIRD_PERSON.put(FlightPoseState.HOVER, new FlightPose(
                -10f, 0f, 0f, 10f, -20f, 5f, 5f, 3f));

        // Tilted into travel, arms forward, legs trailing.
        THIRD_PERSON.put(FlightPoseState.GLIDE, new FlightPose(
                -35f, 2f, -2f, 35f, -60f, 8f, 15f, 5f));

        // Near-horizontal, arms extended ahead, legs straight back.
        THIRD_PERSON.put(FlightPoseState.PROPELLED, new FlightPose(
                -70f, 4f, -4f, 65f, -110f, 6f, 25f, 3f));

        // ── PLACEHOLDER first-person values ─────────────────────────────────
        //
        // A separate set rather than a reuse of the above, because §3.5 makes first person a
        // required branch: a shoulder rotation that reads correctly in third person puts the held
        // wand through the camera. Body and head entries are unused in first person — there is no
        // torso or head on screen — and are held at zero rather than omitted so the record shape
        // stays one thing.
        FIRST_PERSON.put(FlightPoseState.HOVER, new FlightPose(
                0f, 0f, 0f, 0f, -8f, 3f, 0f, 0f));
        FIRST_PERSON.put(FlightPoseState.GLIDE, new FlightPose(
                0f, 0f, 0f, 0f, -18f, 5f, 0f, 0f));
        FIRST_PERSON.put(FlightPoseState.PROPELLED, new FlightPose(
                0f, 0f, 0f, 0f, -30f, 4f, 0f, 0f));
    }

    public static FlightPose of(FlightPoseState state, boolean firstPerson) {
        return (firstPerson ? FIRST_PERSON : THIRD_PERSON).get(state);
    }

    /** Blends two poses componentwise. Used for the from-state → to-state transition. */
    public static FlightPose lerp(FlightPose from, FlightPose to, float t) {
        return new FlightPose(
                lerp(from.bodyPitch(), to.bodyPitch(), t),
                lerp(from.bodyY(), to.bodyY(), t),
                lerp(from.bodyY2(), to.bodyY2(), t),
                lerp(from.headPitch(), to.headPitch(), t),
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
