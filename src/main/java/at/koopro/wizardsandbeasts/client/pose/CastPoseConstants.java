package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.client.pose.FlightPoseConstants.PartRotation;
import org.jspecify.annotations.NullMarked;

/**
 * Pose values for the three cast phases, in third person and in first person.
 *
 * <p>Authored 2026-11-04 and judged in a running client ({@code EquipmentShowcaseCapture}, mode {@code cast}:
 * each phase parked by the same payload a real cast sends, from three sides and in first person). They replace a
 * placeholder table whose three phases were too alike to read as a gather, a strike and a settle.
 *
 * <h2>The performance</h2>
 *
 * <ul>
 *   <li><b>Wind-up</b> — held for as long as the player holds the wand: the arm drawn up and back over the
 *       shoulder, wand raised, chest coiled away. Unmistakably "about to cast" from any side.</li>
 *   <li><b>Release</b> — the strike: the arm thrown out to point where the caster is looking, chest turned into
 *       it. A channelled spell (a beam) holds this pose for the whole channel.</li>
 *   <li><b>Recovery</b> — the arm settling down and forward, back into the vanilla hold.</li>
 * </ul>
 *
 * <p>No movement here is book canon except where a per-spell override says so ({@code WAND_CAST_POSE_SCHEMA} §3);
 * the three phases are a game gesture.
 *
 * <h2>Arm only, by D4</h2>
 *
 * <p>The pose layer owns the casting arm; the wand's {@code GeoItem} owns the wand's own bones and tip FX. These
 * values move the casting arm and give the chest and head only enough to stop the arm looking detached.
 *
 * <h2>Sign conventions</h2>
 *
 * <p>{@code ModelPart} rotations in degrees, written for a right-handed cast (the pass mirrors yaw and roll for a
 * left hand). Limb {@code xRot} negative swings forward: {@code -90} points straight ahead, {@code -180} straight
 * up. {@link CastPose#aim} is the share of the caster's look pitch added to the arm's {@code xRot}, so a strike
 * points where the player looks, as vanilla's bow aim does.
 *
 * <h2>First person</h2>
 *
 * <p>Holding a wand, vanilla draws the item and not the arm, so first person moves the held wand instead
 * ({@link FirstPersonCast}): a translation in screen blocks and a pitch, per phase.
 */
@NullMarked
public final class CastPoseConstants {

    private CastPoseConstants() {}

    /**
     * One phase's third-person pose.
     *
     * @param castingArm the wand arm — whichever {@code mainArm} reports
     * @param chest      torso twist; a cast that moves only the arm reads as a puppet
     * @param headPitch  added to the real look pitch
     * @param aim        0..1 share of the look pitch added to the arm's {@code xRot}
     */
    public record CastPose(PartRotation castingArm, PartRotation chest, float headPitch, float aim) {

        public CastPose lerp(CastPose to, float t) {
            return new CastPose(
                    castingArm.lerp(to.castingArm(), t),
                    chest.lerp(to.chest(), t),
                    FlightPoseConstants.lerp(headPitch, to.headPitch(), t),
                    FlightPoseConstants.lerp(aim, to.aim(), t));
        }
    }

    /**
     * One phase's first-person wand offset: where the held wand moves on screen.
     *
     * @param up      blocks up (+) in the hand's frame
     * @param forward blocks away from the camera (+)
     * @param pitch   degrees about the hand; negative lifts the point up and back toward the caster
     */
    public record FirstPersonPose(float up, float forward, float pitch) {

        public FirstPersonPose lerp(FirstPersonPose to, float t) {
            return new FirstPersonPose(
                    FlightPoseConstants.lerp(up, to.up(), t),
                    FlightPoseConstants.lerp(forward, to.forward(), t),
                    FlightPoseConstants.lerp(pitch, to.pitch(), t));
        }
    }

    /** Neutral. What the pose blends out of and back into. */
    public static final CastPose NEUTRAL =
            new CastPose(PartRotation.NONE, PartRotation.NONE, 0f, 0f);

    // ── third person ─────────────────────────────────────────────────────────

    /** The gather: arm up and back over the shoulder, wand raised, chest coiled away. */
    public static final CastPose WINDUP = new CastPose(
            new PartRotation(-150f, -25f, 10f),
            new PartRotation(0f, -18f, 0f),
            -6f, 0f);

    /** The strike, and the channel: arm thrown out along the look, chest turned into it. */
    public static final CastPose RELEASE = new CastPose(
            new PartRotation(-90f, 6f, 0f),
            new PartRotation(0f, 14f, 0f),
            0f, 1f);

    /** The settle: arm lowering forward, chest unwinding. */
    public static final CastPose RECOVERY = new CastPose(
            new PartRotation(-38f, 6f, 0f),
            new PartRotation(0f, 5f, 0f),
            0f, 0.3f);

    // ── per-spell overrides ──────────────────────────────────────────────────

    /** Gather, strike and settle for one spell or for the default. */
    public record PoseSet(CastPose windup, CastPose release, CastPose recovery) {}

    public static final PoseSet DEFAULT = new PoseSet(WINDUP, RELEASE, RECOVERY);

    /**
     * Wingardium Leviosa's "swish and flick" — the one wand movement the books describe (<i>Philosopher's Stone</i>
     * ch. 10), so the one per-spell override ({@code WAND_CAST_POSE_SCHEMA} G-1, {@code gesture_source: book}). A
     * channelled spell holds its strike, so the swish is held while the object floats, and the flick plays as the
     * wand is let go.
     */
    public static final PoseSet LEVIOSA = new PoseSet(
            new CastPose(new PartRotation(-100f, -30f, 0f), new PartRotation(0f, -10f, 0f), 0f, 0.5f),
            new CastPose(new PartRotation(-82f, 34f, 0f), new PartRotation(0f, 12f, 0f), 0f, 1f),
            new CastPose(new PartRotation(-125f, 18f, 0f), new PartRotation(0f, 6f, 0f), -4f, 0.4f));

    /** The pose set a cast plays: its spell's override, or the default. */
    public static PoseSet setFor(String spellId) {
        return spellId.endsWith(":wingardium_leviosa") || spellId.equals("wingardium_leviosa") ? LEVIOSA : DEFAULT;
    }

    // ── first person ─────────────────────────────────────────────────────────

    public static final FirstPersonPose FP_NEUTRAL = new FirstPersonPose(0f, 0f, 0f);
    /** The wand rises and draws back toward the shoulder, point tipped up. */
    public static final FirstPersonPose FP_WINDUP = new FirstPersonPose(0.26f, -0.06f, -22f);
    /** Thrust out to the centre of the view, point level. */
    public static final FirstPersonPose FP_RELEASE = new FirstPersonPose(0.06f, 0.28f, -4f);
    /** Settling back toward the hold. */
    public static final FirstPersonPose FP_RECOVERY = new FirstPersonPose(0.02f, 0.08f, 0f);

    /**
     * Resolves the third-person pose for a point in the cast.
     *
     * <p>Neutral → wind-up across the wind-up window, wind-up → release across the release window, release →
     * recovery → neutral across recovery. Recovery ends on neutral because the end of a cast <em>is</em> not
     * casting.
     */
    public static CastPose at(ClientCastAnimationState.ActiveCast cast, float progress) {
        PoseSet set = setFor(cast.spellId());
        float windup = cast.windup(progress);
        if (windup < 1f) {
            return NEUTRAL.lerp(set.windup(), windup);
        }
        float release = cast.release(progress);
        if (release < 1f) {
            return set.windup().lerp(set.release(), release);
        }
        float recovery = cast.recovery(progress);
        return recovery < 0.5f
                ? set.release().lerp(set.recovery(), recovery * 2f)
                : set.recovery().lerp(NEUTRAL, (recovery - 0.5f) * 2f);
    }

    /** The first-person counterpart of {@link #at}, over the same phases. */
    public static FirstPersonPose firstPersonAt(ClientCastAnimationState.ActiveCast cast, float progress) {
        float windup = cast.windup(progress);
        if (windup < 1f) {
            return FP_NEUTRAL.lerp(FP_WINDUP, windup);
        }
        float release = cast.release(progress);
        if (release < 1f) {
            return FP_WINDUP.lerp(FP_RELEASE, release);
        }
        float recovery = cast.recovery(progress);
        return recovery < 0.5f
                ? FP_RELEASE.lerp(FP_RECOVERY, recovery * 2f)
                : FP_RECOVERY.lerp(FP_NEUTRAL, (recovery - 0.5f) * 2f);
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
