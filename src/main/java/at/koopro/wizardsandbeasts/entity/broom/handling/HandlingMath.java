package at.koopro.wizardsandbeasts.entity.broom.handling;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomHandling;
import net.minecraft.util.Mth;

/**
 * The shared arithmetic behind the profiles, so four classes cannot drift apart on what "wander"
 * means.
 *
 * <p>Everything here is a pure function of the entity's tick count and the definition's scalars.
 * Nothing is random, and that is load-bearing rather than tidy: yaw is stepped on both sides of the
 * connection, so a random wander would have the client and the server disagreeing about where the
 * broom is pointing, which the player sees as the nose snapping back every few ticks.
 */
public final class HandlingMath {

    private HandlingMath() {}

    /** Radians of phase per tick for the slow weave. About one cycle every four seconds. */
    public static final float SLOW_DRIFT_RATE = 0.077f;
    /** The faster wobble layered on under boost — roughly three times the rate. */
    public static final float FAST_DRIFT_RATE = 0.23f;
    /**
     * Degrees of wander per unit of {@code yawDrift}.
     *
     * <p>Set so the BALANCED default of 0.02 comes out at half a degree at full speed: present enough
     * that a broom does not feel welded to its heading, small enough that nobody has to fight it. A
     * school broom's 0.06 is one and a half degrees, which is a visible weave.
     */
    public static final float DRIFT_DEGREES = 25.0f;
    /** How much of {@code wobbleAtBoost} becomes heading wander; the rest is the visual roll. */
    public static final float BOOST_WOBBLE_SHARE = 0.5f;
    /** Degrees of roll shudder per unit of {@code wobbleAtBoost}, at the peak of the cycle. */
    public static final float BOOST_ROLL_DEGREES = 9.0f;

    /**
     * The wander every profile starts from: a slow weave that scales with speed, plus a faster
     * shudder while the boost is firing.
     *
     * <p>Scales to nothing at a standstill, so a hovering broom sits still rather than searching
     * around for a heading it was never given.
     */
    public static float defaultWander(int tickCount, BroomHandling handling, float speedRatio,
                                      boolean boosting) {
        float amplitude = handling.yawDrift();
        if (boosting) {
            amplitude += handling.wobbleAtBoost() * BOOST_WOBBLE_SHARE;
        }
        if (amplitude <= 0f || speedRatio <= 0f) {
            return 0f;
        }
        float slow = Mth.sin(tickCount * SLOW_DRIFT_RATE);
        float fast = boosting ? Mth.sin(tickCount * FAST_DRIFT_RATE) * 0.5f : 0f;
        return amplitude * DRIFT_DEGREES * speedRatio * (slow + fast);
    }

    /** The visual roll shudder that pairs with the heading wobble while boosting. */
    public static float boostRoll(int tickCount, BroomHandling handling) {
        float wobble = handling.wobbleAtBoost();
        if (wobble <= 0f) {
            return 0f;
        }
        return wobble * BOOST_ROLL_DEGREES * Mth.sin(tickCount * FAST_DRIFT_RATE);
    }

    /**
     * How close a broom is to its own boosted ceiling, 0 to 1.
     *
     * <p>Its own, not the roster's: normalising against the fastest broom in the game would have a
     * Cleansweep at full tilt reading as a third of a speed ratio and never reaching any threshold
     * keyed off one.
     */
    public static float speedRatio(float currentSpeed, BroomDefinition def) {
        float ceiling = def.maxSpeed() * def.boostMultiplier();
        return ceiling <= 0f ? 0f : Mth.clamp(Math.abs(currentSpeed) / ceiling, 0f, 1f);
    }
}
