package at.koopro.wizardsandbeasts.broom.fitting;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomHandling;
import org.jspecify.annotations.NullMarked;

/**
 * The broom a rider is actually flying: its definition, as the server publishes it, changed by its fittings and its
 * condition.
 *
 * <p>Pure and total. {@code BroomEntity.resolveDefinition()} is the one place it is called, and every flight reader
 * — movement, the handling profiles, impacts, the client FX and {@code BroomSpeedGuard} — reads through that, so the
 * client that simulates a fitted broom and the server that polices it cannot disagree about what it is. Results are
 * clamped to the bounds an administrator may tune to ({@code BroomStat}), so no combination of fittings can describe
 * a broom the flight code was not already prepared to fly.
 */
@NullMarked
public final class BroomTune {

    private BroomTune() {}

    /** {@code base} as fitted with {@code fittings} and worn to {@code wear}; {@code base} itself when neither applies. */
    public static BroomDefinition apply(BroomDefinition base, BroomFittings fittings, BroomWear wear) {
        if (fittings.isEmpty() && wear == BroomWear.SOUND) {
            return base;
        }
        BroomHandling h = base.handling();
        float boostExcess = (base.boostMultiplier() - 1.0f) * m(fittings, wear, FittingStat.BOOST);
        float wearLoss = m(fittings, wear, FittingStat.WEAR);
        BroomHandling handling = new BroomHandling(h.profile(),
                clamp(h.yawDrift() * m(fittings, wear, FittingStat.DRIFT), 0.0f, 0.5f),
                h.momentumRetention(),
                clamp(h.wobbleAtBoost() * m(fittings, wear, FittingStat.WOBBLE), 0.0f, 1.0f),
                h.boostFovPunch(),
                clamp(h.crashDamageMultiplier() * m(fittings, wear, FittingStat.CRASH), 0.0f, 4.0f),
                Math.round(h.minorImpactDurabilityLoss() * wearLoss),
                Math.round(h.severeImpactDurabilityLoss() * wearLoss));
        return new BroomDefinition(base.id(), base.displayName(), base.tier(),
                clamp(base.maxSpeed() * m(fittings, wear, FittingStat.MAX_SPEED), 0.1f, 2.0f),
                clamp(base.acceleration() * m(fittings, wear, FittingStat.ACCELERATION), 0.005f, 0.3f),
                clamp(base.deceleration() * m(fittings, wear, FittingStat.DECELERATION), 0.005f, 0.3f),
                clamp(1.0f + boostExcess, 1.0f, 3.0f),
                Math.max(20, Math.round(base.boostDurationTicks() * m(fittings, wear, FittingStat.BOOST_DURATION))),
                base.boostCooldownTicks(), base.weakGravity(), base.lerpFactor(),
                clamp(base.turnSpeed() * m(fittings, wear, FittingStat.TURN), 0.1f, 2.0f),
                base.ascentSpeed(), base.descentSpeed(),
                clamp(base.handlingRating() * m(fittings, wear, FittingStat.HANDLING), 0.0f, 1.0f),
                clamp(base.stabilityRating() * m(fittings, wear, FittingStat.STABILITY), 0.0f, 1.0f),
                base.durability(), base.repairMaterial(), base.loreLines(), base.modelSlots(), base.woodTint(),
                base.assets(), handling, base.audio(), base.seat());
    }

    /** The combined multiplier for {@code stat}: fittings, then condition. */
    public static float m(BroomFittings fittings, BroomWear wear, FittingStat stat) {
        return fittings.multiplier(stat) * wear.multiplier(stat);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
