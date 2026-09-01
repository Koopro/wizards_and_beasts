package at.koopro.wizardsandbeasts.entity.broom.handling;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;

/**
 * How one family of brooms answers the controls.
 *
 * <p>The numbers on {@link BroomDefinition} say how fast a broom goes; a profile says what it feels
 * like getting there. Eight brooms with eight different {@code maxSpeed} values were still eight
 * copies of the same flying stick, because every one of them accelerated, turned, drifted and
 * stopped by the same arithmetic.
 *
 * <h2>Every hook has a do-nothing default</h2>
 * A profile class is therefore exactly the list of things it does differently, and nothing else —
 * {@link BalancedHandling} overrides one method, {@link TankHandling} five. The alternative, an
 * abstract class with eleven mandatory overrides, buries the two lines that matter in nine that
 * return their own argument.
 *
 * <p>The defaults are not neutral in the abstract: they are the behaviour {@code BroomMovement} had
 * before profiles existed. A definition naming no profile gets {@link HandlingProfileRegistry#BALANCED},
 * which flies the way the broom always did.
 *
 * <h2>Scalars stay on the definition</h2>
 * A profile reads {@code def.handling()} for anything a datapack should be able to retune —
 * {@code yawDrift}, {@code momentumRetention}, {@code crashDamageMultiplier}. What lives in the class
 * is the <em>shape</em> of the response: the heading lock a racing broom has and a school broom does
 * not, the extra sink at speed, the boost cap. Those are not numbers a datapack can express, which is
 * the whole reason this is code and not more JSON.
 *
 * <h2>Threading</h2>
 * Implementations are stateless singletons, called from the movement tick on both sides. Anything a
 * profile needs to remember belongs on the entity, where it is already per-broom and already synced.
 */
public interface BroomHandlingProfile {

    /** The id this profile answers to in {@code handlingProfile}. */
    String profileId();

    /**
     * Adjusts the speed the broom is currently trying to reach, after boost has been folded in.
     *
     * <p>Returns the value rather than mutating a parameter: the brief's {@code void} form cannot
     * change a local {@code float}, and the only way to make it work — a mutable carrier object —
     * would allocate once per broom per tick in the movement hot path.
     */
    default float modifyTargetSpeed(float targetSpeed, BroomEntity broom, BroomDefinition def,
                                    boolean boosting) {
        return targetSpeed;
    }

    /** How fast the broom closes on {@link #modifyTargetSpeed}. Lower is heavier. */
    default float modifyAcceleration(float acceleration, BroomEntity broom, BroomDefinition def,
                                     boolean boosting) {
        return acceleration;
    }

    /**
     * How fast the broom bleeds speed with nothing held.
     *
     * <p>Scaled against {@code momentumRetention} by the default implementation, normalised so
     * {@link at.koopro.wizardsandbeasts.broom.BroomHandling#NEUTRAL_MOMENTUM} is a no-op. Normalised
     * rather than raw {@code 1 / momentumRetention} on purpose: the raw form would make every broom
     * in the game stop 11% faster than it used to, which is a silent retune of eight hand-tuned
     * definitions dressed up as a new feature.
     */
    default float modifyDeceleration(float deceleration, BroomEntity broom, BroomDefinition def) {
        return deceleration * (at.koopro.wizardsandbeasts.broom.BroomHandling.NEUTRAL_MOMENTUM
                / def.handling().momentumRetention());
    }

    /** Degrees of yaw the nose may swing this tick. */
    default float modifyTurnRate(float baseTurnRate, BroomEntity broom, BroomDefinition def,
                                 boolean boosting) {
        return baseTurnRate;
    }

    /**
     * Degrees the heading wanders off the one the rider is holding, this tick.
     *
     * @param speedRatio how close to this broom's boosted ceiling it is, 0 to 1
     * @param steering   whether the rider is actively turning. A profile that locks its heading only
     *                   does so when they are not — a lock that fought the controls would read as
     *                   the broom refusing to turn.
     */
    default float yawWander(BroomEntity broom, BroomDefinition def, float speedRatio,
                            boolean boosting, boolean steering) {
        return HandlingMath.defaultWander(broom.tickCount, def.handling(), speedRatio, boosting);
    }

    /** How hard the broom sinks with no vertical input held. */
    default float modifyWeakGravity(float weakGravity, BroomEntity broom, BroomDefinition def) {
        return weakGravity;
    }

    /** Visual bank angle. Cosmetic: {@code rollTilt} is a render value and steers nothing. */
    default float modifyRollTilt(float rollTilt, BroomEntity broom, BroomDefinition def) {
        return rollTilt;
    }

    /**
     * Last word on the broom's velocity for this tick, after everything else has run.
     *
     * <p>The one hook that mutates rather than returning, because it exists for effects that are not
     * a modification of any single scalar — a racing broom's nose-heaviness at speed is a change to
     * the finished vertical velocity, not to gravity or to lift.
     */
    default void afterVelocityComputed(BroomEntity broom, BroomDefinition def) {
    }

    /** Fired the tick a boost actually starts firing — not when the key is pressed. */
    default void onBoostStart(BroomEntity broom, BroomDefinition def) {
    }

    /** Multiplier on rider damage from a severe impact. Below 1 is forgiving. */
    default float crashDamageMultiplier(BroomDefinition def) {
        return def.handling().crashDamageMultiplier();
    }

    /** Durability taken by a glancing knock. Severe impacts deliberately have no such hook. */
    default int minorImpactDurabilityLoss(int base, BroomDefinition def) {
        return base;
    }
}
