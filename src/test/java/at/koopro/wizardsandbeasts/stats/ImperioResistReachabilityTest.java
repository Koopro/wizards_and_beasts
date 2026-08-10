package at.koopro.wizardsandbeasts.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that the Imperius Curse stays breakable once WILLPOWER scales the roll.
 *
 * <p>Routing the resist through a trait made it possible to build a player who can never escape —
 * the exact failure {@code WandBondReachabilityTest} exists to prevent elsewhere, and one that
 * would surface in play as "the curse never wears off" with nothing logged. The worst case is a
 * brand-new player: WILLPOWER 0, and zero Imperius proficiency because they have never cast it.
 *
 * <p>Mirrors {@code ImperioServerLogic.tickVictim}'s arithmetic. Keep the two in step.
 */
class ImperioResistReachabilityTest {

    private static final float EPS = 1.0e-4f;

    /** {@code charge × proficiency term × trait scalar}, as the tick handler computes it. */
    private static float resistChance(float resolve, int willTrait, float imperioProficiency) {
        float charge = resolve / StatEffects.maxResolve(willTrait);
        return charge * (imperioProficiency * 0.5f + 0.5f) * StatEffects.resistScalar(willTrait);
    }

    @Test
    void aBrandNewPlayerCanThrowOffTheCurse() {
        float chance = resistChance(StatEffects.maxResolve(0), 0, 0f);
        assertEquals(0.20f, chance, EPS);
        assertTrue(chance > 0.05f,
                "trait-0 resist chance " + chance + " per second is too low to ever escape");
    }

    @Test
    void trainingWillpowerMakesEscapeMeaningfullyEasier() {
        float untrained = resistChance(StatEffects.maxResolve(0), 0, 0f);
        float trained = resistChance(StatEffects.maxResolve(100), 100, 0f);
        assertEquals(0.50f, trained, EPS);
        assertTrue(trained > untrained * 2f,
                "maxing Willpower should more than double the resist chance");
    }

    @Test
    void everyPlayerGetsTheSameNumberOfAttemptsBeforeExhaustion() {
        // The reason the drain is a fraction of the ceiling rather than a flat amount. With a flat
        // 15 against a trait-scaled ceiling, a trait-0 pool emptied in three failures while a
        // trait-100 pool took six — worse odds AND fewer tries, a double penalty nobody chose.
        assertEquals(failedAttemptsUntilEmpty(0), failedAttemptsUntilEmpty(100));
        assertEquals(failedAttemptsUntilEmpty(0), failedAttemptsUntilEmpty(50));
        assertEquals(7, failedAttemptsUntilEmpty(0));
    }

    @Test
    void aMaxWillpowerPlayersEconomyIsUnchangedFromBeforeTheRework() {
        // The old code charged a flat 30 / 15 against a fixed 0-100 pool. At trait 100 the ceiling
        // is 100, so the new fractional costs must reproduce those numbers exactly — the rework is
        // a generalisation of the old behaviour, not a rebalance of it.
        assertEquals(30f, StatEffects.resolveCostToBreakFree(100), EPS);
        assertEquals(15f, StatEffects.resolveCostOfFailedAttempt(100), EPS);
    }

    @Test
    void aDrainedPoolStillLeavesARealChance() {
        // After three failures the odds must not collapse to noise, or the curse becomes a
        // guaranteed hold for the rest of its duration.
        int trait = 0;
        float resolve = StatEffects.maxResolve(trait) - 3 * StatEffects.resolveCostOfFailedAttempt(trait);
        float chance = resistChance(resolve, trait, 0f);
        assertTrue(chance > 0.08f,
                "resist chance after three failures fell to " + chance + " — effectively locked in");
    }

    private static int failedAttemptsUntilEmpty(int willTrait) {
        float resolve = StatEffects.maxResolve(willTrait);
        float cost = StatEffects.resolveCostOfFailedAttempt(willTrait);
        int attempts = 0;
        while (resolve > 0f) {
            resolve = Math.max(0f, resolve - cost);
            attempts++;
        }
        return attempts;
    }
}
