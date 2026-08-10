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

    private static void assertWithinClamp(float value, String channel, int stat) {
        assertTrue(value > ModifierStack.HARD_FLOOR && value < ModifierStack.HARD_CAP,
                channel + " multiplier " + value + " at stat " + stat
                        + " reaches a ModifierStack clamp on its own");
    }
}
