package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.entity.broom.BroomTuning;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * The pure arithmetic behind {@link BroomCameraHandler}, split out so the parts that decide whether
 * flight makes a player queasy can be asserted in a unit test — the look has to be judged in-game,
 * but the caps, the sign and the fact that the camera actually settles back to neutral are all
 * checkable without a render context.
 */
@NullMarked
public final class BroomCameraMath {

    /** Share of the broom's bank the camera takes. Well under 1: the horizon tipping as far as the
     *  broom does is what makes flight cameras nauseating. */
    static final float ROLL_SHARE = 0.35F;
    /** Hard cap on camera roll in degrees, independent of {@link BroomTuning#MAX_ROLL_TILT}. */
    static final float MAX_ROLL_DEG = 12.0F;
    /** Extra third-person camera distance, in blocks, at full boosted speed. */
    static final float MAX_PULL_BACK = 1.6F;
    /** Per-frame smoothing toward the target. Low enough that a hard turn eases rather than snaps. */
    static final float SMOOTHING = 0.12F;
    /** Below this the smoothed value counts as fully settled and is snapped to zero, so a resting
     *  camera stops writing to events it has no reason to touch instead of chasing zero forever. */
    static final float SETTLED_EPSILON = 0.01F;

    private BroomCameraMath() {}

    /** Camera roll, in degrees, for a broom banked {@code broomRollDeg}. */
    public static float rollTarget(float broomRollDeg) {
        return Mth.clamp(broomRollDeg * ROLL_SHARE, -MAX_ROLL_DEG, MAX_ROLL_DEG);
    }

    /**
     * Extra third-person camera distance, in blocks, for a broom moving at {@code speed}, measured
     * against that broom's own boosted ceiling.
     *
     * <p>{@code ceiling} is a parameter rather than a constant because the old fixed denominator
     * ({@code MAX_SPEED * BOOST_MULTIPLIER}, 1.6675) described no broom that exists: a starter broom
     * peaking at 0.455 never pulled the camera past a quarter of {@link #MAX_PULL_BACK}, so its top
     * speed looked like a crawl, while a Firebolt Supreme peaking at 2.625 sat pinned at the cap for
     * most of its range and so had no visual sense of accelerating at all. Per-broom, the pull-back
     * spans its full travel on every broom in the game.
     */
    public static float pullBackTarget(float speed, float ceiling) {
        if (ceiling <= 0.0F) {
            return 0.0F;
        }
        return Mth.clamp(Math.abs(speed) / ceiling, 0.0F, 1.0F) * MAX_PULL_BACK;
    }

    /** As above, against the fastest broom that ships — for a caller with no definition in hand. */
    public static float pullBackTarget(float speed) {
        return pullBackTarget(speed, BroomTuning.REFERENCE_TOP_SPEED);
    }

    /**
     * Advances a smoothed camera value one frame toward {@code target}, snapping to exactly zero once
     * it is close enough. The snap is what makes dismounting, dying and a mid-flight module toggle
     * hand the camera back to vanilla cleanly: the target becomes zero, the value eases down, and
     * then it stops being applied at all rather than leaving a permanent hairline offset.
     */
    public static float advance(float current, float target) {
        float next = Mth.lerp(SMOOTHING, current, target);
        return Math.abs(next) < SETTLED_EPSILON ? 0.0F : next;
    }
}
