package at.koopro.wizardsandbeasts.client.spell.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cooldown readout maths. Pure, so the three ways it has actually gone wrong in this HUD are
 * cheap to pin down: dividing by the base cooldown instead of the applied span (sweep looks frozen),
 * an unclamped fraction (sweep springs past full or goes negative on the boundary frame), and a
 * seconds readout that rounds down (shows "0" to a player who still cannot cast).
 */
class SpellCooldownDisplayTest {

    private static final float EPS = 1e-4f;

    @Test
    void freshCooldown_isFull() {
        // 60-tick cooldown applied at tick 100: expiry 160, nothing elapsed.
        assertEquals(1.0f, SpellCooldownDisplay.remainingFraction(160L, 100f, 60L), EPS);
    }

    @Test
    void halfElapsed_isHalf() {
        assertEquals(0.5f, SpellCooldownDisplay.remainingFraction(160L, 130f, 60L), EPS);
    }

    @Test
    void expired_isZero() {
        assertEquals(0.0f, SpellCooldownDisplay.remainingFraction(160L, 160f, 60L), EPS);
    }

    @Test
    void clampsBothEnds_becauseTheClocksDisagreeByATick() {
        // Client is a tick ahead of the expiry: without the clamp this is negative.
        assertEquals(0.0f, SpellCooldownDisplay.remainingFraction(160L, 161.5f, 60L), EPS);
        // Client is a tick behind the stamp: without the clamp this exceeds 1.
        assertEquals(1.0f, SpellCooldownDisplay.remainingFraction(160L, 98.5f, 60L), EPS);
    }

    @Test
    void spanIsTheAppliedDuration_notTheBaseCooldown() {
        // A x3 cooldown modifier: base 60, applied 180. Measured against the applied span the sweep is
        // two-thirds full one third of the way through; measured against the base it would clamp at 1
        // for the first 120 ticks and read as frozen.
        assertEquals(2f / 3f, SpellCooldownDisplay.remainingFraction(280L, 160f, 180L), EPS);
        assertEquals(1.0f, SpellCooldownDisplay.remainingFraction(280L, 160f, 60L), EPS);
    }

    @Test
    void nonPositiveSpan_readsAsFinished_ratherThanDividingByZero() {
        assertEquals(0.0f, SpellCooldownDisplay.remainingFraction(160L, 100f, 0L), EPS);
        assertEquals(0.0f, SpellCooldownDisplay.remainingFraction(160L, 100f, -5L), EPS);
    }

    @Test
    void remainingTicks_neverGoesNegative() {
        assertEquals(20f, SpellCooldownDisplay.remainingTicks(160L, 140f), EPS);
        assertEquals(0f, SpellCooldownDisplay.remainingTicks(160L, 200f), EPS);
    }

    @Test
    void secondsRoundUp_soTheLastSecondStillReadsOne() {
        assertEquals(1, SpellCooldownDisplay.secondsRemaining(160L, 159f));   // 1 tick left
        assertEquals(1, SpellCooldownDisplay.secondsRemaining(160L, 141f));   // 19 ticks left
        assertEquals(2, SpellCooldownDisplay.secondsRemaining(160L, 139f));   // 21 ticks left
        assertEquals(3, SpellCooldownDisplay.secondsRemaining(160L, 110f));   // 50 ticks left
        assertEquals(0, SpellCooldownDisplay.secondsRemaining(160L, 160f));
    }

    @Test
    void secondsReadoutIsSuppressedUnderOneSecond_whereTheSweepReadsBetter() {
        assertTrue(SpellCooldownDisplay.showsSecondsReadout(160L, 130f));   // 30 ticks
        assertFalse(SpellCooldownDisplay.showsSecondsReadout(160L, 140f));  // exactly 20 ticks
        assertFalse(SpellCooldownDisplay.showsSecondsReadout(160L, 145f));  // 15 ticks
        assertFalse(SpellCooldownDisplay.showsSecondsReadout(160L, 170f));  // already over
    }
}
