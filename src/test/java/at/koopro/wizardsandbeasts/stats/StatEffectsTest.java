package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins every {@link StatEffects} curve at its endpoints and midpoint, and guards the one property
 * that is easy to break by tuning: a stat must never be able to reach a
 * {@link ModifierStack} clamp on its own.
 *
 * <p>The cast pipeline multiplies wand integrity, allegiance, skill trees, vocations, Obscurial
 * rules, niffler happiness and Dark corruption into the same running product before clamping it to
 * [{@link ModifierStack#HARD_FLOOR}, {@link ModifierStack#HARD_CAP}]. If a stat spent that budget
 * by itself, every system multiplied after it would silently stop mattering — with no error and no
 * failing test anywhere else.
 */
class StatEffectsTest {

    private static final float EPS = 1.0e-4f;

    @Test
    void damageMultiplierSpansItsDeclaredRange() {
        assertEquals(0.90f, StatEffects.damageMultiplier(0), EPS);
        assertEquals(1.05f, StatEffects.damageMultiplier(50), EPS);
        assertEquals(1.20f, StatEffects.damageMultiplier(100), EPS);
    }

    @Test
    void misfireDeltaOnlyEverRemovesMisfires() {
        assertEquals(0.00f, StatEffects.misfireDelta(0), EPS);
        assertEquals(-0.04f, StatEffects.misfireDelta(50), EPS);
        assertEquals(-0.08f, StatEffects.misfireDelta(100), EPS);
        for (int precision = 0; precision <= 100; precision++) {
            assertTrue(StatEffects.misfireDelta(precision) <= 0f,
                    "PRECISION " + precision + " added misfire chance instead of removing it");
        }
    }

    @Test
    void cooldownMultiplierFallsAsReflexesRise() {
        assertEquals(1.10f, StatEffects.cooldownMultiplier(0), EPS);
        assertEquals(0.99f, StatEffects.cooldownMultiplier(50), EPS);
        assertEquals(0.88f, StatEffects.cooldownMultiplier(100), EPS);
    }

    @Test
    void resistScalarNeverReachesZero() {
        assertEquals(0.40f, StatEffects.resistScalar(0), EPS);
        assertEquals(0.70f, StatEffects.resistScalar(50), EPS);
        assertEquals(1.00f, StatEffects.resistScalar(100), EPS);
        // A zero here would make the Imperius Curse unbreakable for an untrained player.
        assertTrue(StatEffects.resistScalar(0) > 0f, "trait-0 resist scalar must not be zero");
    }

    @Test
    void resolvePoolGrowsWithTheTrait() {
        assertEquals(50.0f, StatEffects.maxResolve(0), EPS);
        assertEquals(75.0f, StatEffects.maxResolve(50), EPS);
        assertEquals(100.0f, StatEffects.maxResolve(100), EPS);
        // Divided by in ImperioServerLogic, so it must never be zero.
        assertTrue(StatEffects.maxResolve(0) > 0f, "Resolve ceiling is a divisor — it cannot be zero");

        assertEquals(0.5f / 20f, StatEffects.resolveRegenPerTick(0), EPS);
        assertEquals(1.5f / 20f, StatEffects.resolveRegenPerTick(100), EPS);
    }

    @Test
    void outOfRangeStatsClampRatherThanExtrapolate() {
        assertEquals(StatEffects.damageMultiplier(0), StatEffects.damageMultiplier(-50), EPS);
        assertEquals(StatEffects.damageMultiplier(100), StatEffects.damageMultiplier(9999), EPS);
        assertEquals(StatEffects.cooldownMultiplier(100), StatEffects.cooldownMultiplier(200), EPS);
    }

    @Test
    void noStatCanReachAModifierStackClampAlone() {
        for (int stat = 0; stat <= 100; stat++) {
            assertWithinClamp(StatEffects.damageMultiplier(stat), "damage", stat);
            assertWithinClamp(StatEffects.cooldownMultiplier(stat), "cooldown", stat);
        }
    }

    @Test
    void aMaxPrecisionCasterStillCannotDriveMisfireNegative() {
        // finalMisfireChance() clamps to [0,1], so a spell with no innate misfire chance and a
        // maxed caster must read exactly zero rather than a negative that leaks into a later sum.
        ModifierStack stack = new ModifierStack();
        stack.addMisfireChance(StatEffects.misfireDelta(100), "test");
        assertEquals(0.0f, stack.finalMisfireChance(), EPS);
    }

    @Test
    void studyRateSpansItsDeclaredRange() {
        assertEquals(1.00f, StatEffects.studyRate(0), EPS);
        assertEquals(1.25f, StatEffects.studyRate(50), EPS);
        assertEquals(1.50f, StatEffects.studyRate(100), EPS);

        // KNOWLEDGE is a reward for work already done elsewhere, so the direction matters: more of
        // it must never make practice pay off more slowly.
        for (int knowledge = 1; knowledge <= 100; knowledge++) {
            assertTrue(StatEffects.studyRate(knowledge) >= StatEffects.studyRate(knowledge - 1),
                    "study rate fell between KNOWLEDGE " + (knowledge - 1) + " and " + knowledge);
        }
    }

    /**
     * The floor is 1.0, not something below it.
     *
     * <p>KNOWLEDGE was a tuition discount before it was a study rate, and a discount's natural
     * shape is "everyone pays full price, the well-read pay less". Ported carelessly onto a rate
     * that becomes "everyone learns slowly, the well-read learn normally" — a penalty on a stat
     * nobody can train directly, applied to every player who has not ground out the other four
     * systems it derives from.
     */
    @Test
    void studyRateNeverPenalisesAnUnreadWizard() {
        for (int knowledge = 0; knowledge <= 100; knowledge++) {
            assertTrue(StatEffects.studyRate(knowledge) >= 1.0f,
                    "practice was slowed at KNOWLEDGE " + knowledge);
        }
    }

    /**
     * Switching {@code PLAYER_STATS} off must never make the game harder than never having shipped it.
     *
     * <p>{@code StatResistModifiers} substitutes {@link PlayerStatsData#MAX_VALUE} for the trait when the
     * module is off, because with it off WILLPOWER is unreadable, undisplayable and untrainable — an
     * ungated read returned 0 forever and pinned the resist scalar at its 0.40 floor, making the Imperius
     * Curse two and a half times harder to throw off on a server that had turned player stats <em>off</em>.
     * These are the invariants that choice rests on: the fallback is the top of every mind-magic curve, and
     * it restores the flat 0–100 pool with 30/15 attempt costs the Imperius code used before this system.
     */
    @Test
    void theModuleOffFallbackIsTheMostGenerousPointOfEveryResistCurve() {
        int off = PlayerStatsData.MAX_VALUE;
        for (int trait = 0; trait <= 100; trait++) {
            assertTrue(StatEffects.resistScalar(off) >= StatEffects.resistScalar(trait),
                    "module-off resist scalar is worse than trait " + trait);
            assertTrue(StatEffects.maxResolve(off) >= StatEffects.maxResolve(trait),
                    "module-off Resolve ceiling is smaller than trait " + trait);
            assertTrue(StatEffects.resolveRegenPerTick(off) >= StatEffects.resolveRegenPerTick(trait),
                    "module-off Resolve regen is slower than trait " + trait);
        }

        assertEquals(1.00f, StatEffects.resistScalar(off), EPS, "module off must not scale the roll at all");
        assertEquals(100.0f, StatEffects.maxResolve(off), EPS, "the pre-stats pool was a flat 0-100");
        assertEquals(30f, StatEffects.resolveCostToBreakFree(off), EPS, "the pre-stats break-free cost");
        assertEquals(15f, StatEffects.resolveCostOfFailedAttempt(off), EPS, "the pre-stats failure cost");
    }

    private static void assertWithinClamp(float value, String channel, int stat) {
        assertTrue(value > ModifierStack.HARD_FLOOR && value < ModifierStack.HARD_CAP,
                channel + " multiplier " + value + " at stat " + stat
                        + " reaches a ModifierStack clamp on its own");
    }
}
