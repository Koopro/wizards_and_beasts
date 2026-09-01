package at.koopro.wizardsandbeasts.entity.broom;

import org.jspecify.annotations.NullMarked;

/**
 * The arithmetic behind broom impacts, landings and gravity, kept apart from the entity so it can be
 * tested without a level.
 *
 * <p>Three rules a caller must not re-derive:
 *
 * <ul>
 *   <li><b>Severity is measured against the broom's own ceiling.</b> It used to divide by
 *       {@code BroomTuning.MAX_SPEED * BOOST_MULTIPLIER} (1.6675), a fixed number describing no
 *       broom that exists and sitting <em>between</em> the real extremes — so it was wrong in both
 *       directions at once. The starter broom peaks at 0.455, i.e. 0.27 of that, below
 *       {@code MINOR_IMPACT_THRESHOLD}: it could not register an impact at any speed into any wall.
 *       The Firebolt Supreme peaks at 2.625 and crossed {@code SEVERE_IMPACT_THRESHOLD} at 64% of its
 *       own top speed, making its cruise a fatal crash. Dividing by the definition's own
 *       {@code maxSpeed * boostMultiplier} is what makes the thresholds mean "a fraction of what
 *       <em>this</em> broom can do".</li>
 *   <li><b>A vertical impact is judged on descent rate, not forward speed.</b> The old call passed
 *       the forward scalar for both axes, so how hard you hit the ground had nothing to do with how
 *       fast you were falling.</li>
 *   <li><b>Landing gently is not a crash.</b> Every flight ends in a vertical collision. Without an
 *       explicit exemption the only difference between landing and crashing is how fast you happened
 *       to be going, and a slow touchdown still books a durability hit.</li>
 * </ul>
 */
@NullMarked
public final class BroomFlightRules {

    private BroomFlightRules() {}

    /**
     * Fraction of this broom's own maximum capability that an impact represents.
     *
     * @param forwardSpeed  signed forward scalar at the moment of impact
     * @param descentSpeed  downward speed at the moment of impact, positive when falling
     * @param broomTopSpeed {@code maxSpeed * boostMultiplier} for the definition being flown
     */
    public static float impactSeverity(float forwardSpeed, float descentSpeed, float broomTopSpeed,
                                       boolean hitHorizontal, boolean hitVertical) {
        if (broomTopSpeed <= 0f) {
            return 0f;
        }
        float severity = 0f;
        if (hitHorizontal) {
            severity = Math.abs(forwardSpeed) / broomTopSpeed;
        }
        if (hitVertical) {
            // Descent is rated against the same ceiling: a broom that can do 1.19 and hits the ground
            // at 1.19 has fallen as hard as it possibly could.
            float verticalSeverity =
                    (Math.abs(descentSpeed) / broomTopSpeed) * BroomTuning.VERTICAL_IMPACT_WEIGHT;
            // Clipping a wall and the floor in the same tick is worse than either alone, but not the
            // sum of both — a corner should not read as a head-on crash at half speed.
            severity = hitHorizontal
                    ? Math.max(severity, verticalSeverity) * BroomTuning.CORNER_IMPACT_WEIGHT
                    : verticalSeverity;
        }
        return severity;
    }

    /**
     * Whether a vertical collision should be treated as a landing rather than an impact: coming down
     * no faster than a step off a block, and not still flying forward at speed.
     *
     * <p>This is what makes "fly out, come back, put it down" free. Anything faster than a controlled
     * descent still costs — a broom dropped out of the sky from a hundred blocks up is a crash, and
     * reads as one.
     */
    public static boolean isGentleLanding(float forwardSpeed, float descentSpeed, float broomTopSpeed) {
        if (Math.abs(descentSpeed) > BroomTuning.GENTLE_LANDING_MAX_DESCENT) {
            return false;
        }
        if (broomTopSpeed <= 0f) {
            return true;
        }
        return Math.abs(forwardSpeed) / broomTopSpeed <= BroomTuning.GENTLE_LANDING_MAX_SPEED_RATIO;
    }

    /**
     * Vertical velocity for a broom whose rider is asking for neither climb nor dive.
     *
     * <p>{@code weakGravity} has been an authored, range-validated field on every broom definition —
     * and read by nothing — since the definitions landed, so a ridden broom hovered forever. It sinks
     * now, slowly, and never past a terminal rate: a broom is meant to feel like it wants holding up,
     * not like a falling rock.
     *
     * @param currentVerticalVelocity the broom's vertical velocity this tick, negative when sinking
     * @param weakGravity             the definition's per-tick sink acceleration
     */
    public static float applyWeakGravity(float currentVerticalVelocity, float weakGravity) {
        float sunk = currentVerticalVelocity - weakGravity;
        return Math.max(sunk, -BroomTuning.TERMINAL_SINK_SPEED);
    }

    /**
     * Speed as a fraction of this broom's unboosted top speed, clamped to {@code [0, 1]} — the input
     * every speed-scaled client effect reads, so wind, FOV and the tilt all agree on what "fast" means.
     */
    public static float speedRatio(float currentSpeed, float broomMaxSpeed) {
        if (broomMaxSpeed <= 0f) {
            return 0f;
        }
        float ratio = Math.abs(currentSpeed) / broomMaxSpeed;
        return ratio < 0f ? 0f : Math.min(ratio, 1f);
    }

    /**
     * Offsets to try when looking for somewhere to put a dismounting rider, best first.
     *
     * <p>Order is the whole content of this method, so it is stated rather than left to a loop:
     *
     * <ol>
     *   <li><b>Straight down first</b>, increasingly far. A broom is almost always in the air, and a
     *       player who lets go over open ground expects to end up <em>on</em> that ground, not hanging
     *       in the air above it. This is also the only direction that reliably has floor under it.</li>
     *   <li><b>Then sideways</b>, ring by ring, at the broom's own height — for the case the drop is
     *       blocked, typically because the broom is parked inside a doorway or against a ceiling.</li>
     *   <li><b>Up last, and only one block.</b> Pushing a rider upward is how a dismount turns into a
     *       fall; it is here purely so a broom flush against the floor still has an answer.</li>
     * </ol>
     *
     * <p>Vanilla's default does none of this — it hands the rider the vehicle's own position, which is
     * how dismounting inside a wall puts you in the wall. Returned offsets are candidates only; the
     * caller still has to test each for space.
     */
    public static int[][] dismountOffsets() {
        java.util.List<int[]> offsets = new java.util.ArrayList<>();
        for (int down = 1; down <= BroomTuning.DISMOUNT_DROP_SEARCH; down++) {
            offsets.add(new int[] {0, -down, 0});
        }
        offsets.add(new int[] {0, 0, 0});
        for (int ring = 1; ring <= BroomTuning.DISMOUNT_SEARCH_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    // Ring, not filled square: the inner offsets were already tried at a smaller ring.
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    offsets.add(new int[] {dx, 0, dz});
                }
            }
        }
        offsets.add(new int[] {0, 1, 0});
        return offsets.toArray(new int[0][]);
    }
}
