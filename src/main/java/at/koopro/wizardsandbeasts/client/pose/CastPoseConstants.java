package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.client.pose.FlightPoseConstants.PartRotation;
import org.jspecify.annotations.NullMarked;

/**
 * Pose values for the three cast phases.
 *
 * <p><b>THESE NUMBERS ARE PLACEHOLDERS AND ARE NOT AUTHORED ART.</b> No cast-pose table has been
 * written — the flight values arrived as {@code documentation/FLIGHT_POSE_CONSTANTS.md} and there is
 * no equivalent for casting. What is below is a coarse first approximation whose only job is to make
 * the phase machinery visible in game, so the command has something to show and the timing can be
 * judged. Tracked at BLOCKER in {@code AUDIT_PUNCHLIST.md}.
 *
 * <p>Written in whole and half degrees on purpose, so a table of them reads as provisional at a
 * glance rather than as something somebody dialled in.
 *
 * <h2>Arm only, by D4</h2>
 *
 * <p>{@code WAND_CAST_POSE_SCHEMA} D4 splits ownership: the pose layer owns the casting arm —
 * shoulder, elbow, hand orientation — and the wand's {@code GeoItem} owns the wand's own bones and
 * tip FX. So these values move the casting arm and give the chest and head only enough to stop the
 * arm looking detached. They never touch the off arm or the legs, and a future wand flourish is not
 * this class's business.
 *
 * <h2>Sign conventions, which differ from the flight table's frame</h2>
 *
 * <p>Limb {@code xRot} negative swings a limb forward — an arm at {@code -90} points straight ahead.
 * These are {@code ModelPart} rotations throughout: unlike the flight pose there is no whole-body
 * transform here, so the {@code scale(-1, -1, 1)} trap that inverts {@code bodyPitch} against the
 * limbs does not arise. Everything in this file is in one frame.
 *
 * <h2>Interim, per R-1</h2>
 *
 * <p>Schema R-1 rules cast poses to be <em>keyframe</em> passes — authored performances, retunable
 * without a rebuild. This table is procedural and is therefore a stand-in. The pass reads phases the
 * way the schema requires either way, so swapping the value source for a sampled clip replaces this
 * class and leaves {@link CastPosePass} alone. That separation is the point.
 */
@NullMarked
public final class CastPoseConstants {

    private CastPoseConstants() {}

    /**
     * One phase's pose, for the casting arm plus a little supporting body.
     *
     * @param castingArm the wand arm — whichever {@code mainArm} reports
     * @param chest      torso twist, small; a cast that moves only the arm reads as a puppet
     * @param headPitch  added to the real look pitch, so the caster keeps aiming where they point
     */
    public record CastPose(PartRotation castingArm, PartRotation chest, float headPitch) {

        public CastPose lerp(CastPose to, float t) {
            return new CastPose(
                    castingArm.lerp(to.castingArm(), t),
                    chest.lerp(to.chest(), t),
                    FlightPoseConstants.lerp(headPitch, to.headPitch(), t));
        }
    }

    /** Neutral. What the pose blends out of and back into. */
    public static final CastPose NEUTRAL =
            new CastPose(PartRotation.NONE, PartRotation.NONE, 0f);

    // ── PLACEHOLDER phase poses ──────────────────────────────────────────────

    /** Arm drawn back and up, body coiled slightly away. The gather before the strike. */
    public static final CastPose WINDUP = new CastPose(
            new PartRotation(-40f, -18f, 12f),
            new PartRotation(0f, -10f, 0f),
            -4f);

    /**
     * Arm thrown forward, past straight-ahead so the flick reads as a flick.
     *
     * <p>The deepest of the three on purpose: this is the frame a viewer actually registers, and the
     * two either side exist to make it land.
     */
    public static final CastPose RELEASE = new CastPose(
            new PartRotation(-95f, 8f, -6f),
            new PartRotation(0f, 12f, 0f),
            2f);

    /** Arm falling back toward rest, still slightly raised. Settles rather than snaps. */
    public static final CastPose RECOVERY = new CastPose(
            new PartRotation(-22f, 4f, 4f),
            new PartRotation(0f, 4f, 0f),
            0f);

    /**
     * Resolves the pose for a point in the cast.
     *
     * <p>Three phases, blended pairwise: neutral → windup across the wind-up window, windup →
     * release across the release window, release → neutral across recovery. Recovery lands on
     * neutral rather than on a fourth pose because the end of a cast <em>is</em> not casting.
     */
    public static CastPose at(ClientCastAnimationState.ActiveCast cast, float progress) {
        float windup = cast.windup(progress);
        if (windup < 1f) {
            return NEUTRAL.lerp(WINDUP, windup);
        }
        float release = cast.release(progress);
        if (release < 1f) {
            return WINDUP.lerp(RELEASE, release);
        }
        return RELEASE.lerp(NEUTRAL, cast.recovery(progress));
    }

    // ── tuning ───────────────────────────────────────────────────────────────

    /**
     * Ticks the whole cast pose eases in over at its start, and out over at its end.
     *
     * <p>Short: a cast is a sharp action, and easing it in over the same eight ticks the flight pose
     * uses would eat most of a thirty-tick cast before the arm had moved.
     */
    public static final int FADE_IN_TICKS = 2;
    public static final int FADE_OUT_TICKS = 3;

    /** Default duration for {@code /wandb debug pose cast play} when none is given. */
    public static final int DEFAULT_TEST_TICKS = 30;
}
