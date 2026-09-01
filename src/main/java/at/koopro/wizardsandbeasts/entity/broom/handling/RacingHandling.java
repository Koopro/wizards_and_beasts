package at.koopro.wizardsandbeasts.entity.broom.handling;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;

/**
 * Racing brooms — the Nimbus pair and the Firebolt family.
 *
 * <p>Two things define the feel, and they pull against each other. It <b>holds a line</b>: let go of
 * the stick and the heading locks, where a school broom keeps searching. And it gets
 * <b>nose-heavy at speed</b>: past seventy per cent of its ceiling the turn tightens up and the
 * broom starts wanting the ground, so the thing that makes it fast is also the thing that makes it
 * dangerous. The Firebolts pay for that with the highest crash multipliers in the game, which is
 * authored in their JSON rather than here — the profile is the shape, the numbers are the tier.
 */
public final class RacingHandling implements BroomHandlingProfile {

    /** Speed ratio past which the handling starts to bite. */
    private static final float HIGH_SPEED_RATIO = 0.7f;
    /** Turn rate retained above that ratio. */
    private static final float HIGH_SPEED_TURN_PENALTY = 0.85f;
    /** How much heading wander survives when the rider is holding a heading. Near zero: a lock. */
    private static final float HEADING_LOCK = 0.08f;
    /** Blocks per tick of extra sink at the ceiling on the least stable broom. */
    private static final float SINK_AT_LIMIT = 0.01f;
    /** Stability is subtracted from this, so even a perfectly stable racing broom sinks a little. */
    private static final float SINK_STABILITY_BASE = 1.1f;

    @Override
    public String profileId() {
        return "racing";
    }

    @Override
    public float modifyTurnRate(float baseTurnRate, BroomEntity broom, BroomDefinition def,
                                boolean boosting) {
        return turnRateAt(baseTurnRate, HandlingMath.speedRatio(broom.getCurrentSpeed(), def));
    }

    /** The turn curve on its own, so the threshold can be tested without flying a broom. */
    public static float turnRateAt(float baseTurnRate, float speedRatio) {
        return speedRatio > HIGH_SPEED_RATIO ? baseTurnRate * HIGH_SPEED_TURN_PENALTY : baseTurnRate;
    }

    /** The heading lock on its own: full wander while steering, almost none while holding a line. */
    public static float lockedWander(float wander, boolean steering) {
        return steering ? wander : wander * HEADING_LOCK;
    }

    /** Extra downward velocity at a given speed and stability. Zero below the threshold. */
    public static float sinkAt(float speedRatio, float stabilityRating) {
        return speedRatio <= HIGH_SPEED_RATIO
                ? 0f
                : SINK_AT_LIMIT * speedRatio * (SINK_STABILITY_BASE - stabilityRating);
    }

    /**
     * Damps the wander to almost nothing while the rider is holding a heading.
     *
     * <p>Only while they are <em>not</em> steering. Damping it during a turn would fight the
     * controls, and a broom that resists being pointed does not read as stable — it reads as broken.
     */
    @Override
    public float yawWander(BroomEntity broom, BroomDefinition def, float speedRatio,
                           boolean boosting, boolean steering) {
        return lockedWander(
                BroomHandlingProfile.super.yawWander(broom, def, speedRatio, boosting, steering),
                steering);
    }

    /**
     * Nose-heaviness at speed: the faster it goes, the more it wants the ground.
     *
     * <p>Applied to the finished vertical velocity rather than to {@code weakGravity}, because it
     * must bite even while the rider is holding ascend — that is the point of it. Gravity is skipped
     * entirely when a vertical key is held, so routing it there would make the effect vanish exactly
     * when a rider is trying to pull out of a dive.
     */
    @Override
    public void afterVelocityComputed(BroomEntity broom, BroomDefinition def) {
        float sink = sinkAt(HandlingMath.speedRatio(broom.getCurrentSpeed(), def), def.stabilityRating());
        if (sink > 0f) {
            broom.setVerticalVelocity(broom.getVerticalVelocity() - sink);
        }
    }
}
