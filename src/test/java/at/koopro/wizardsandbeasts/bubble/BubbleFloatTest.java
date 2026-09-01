package at.koopro.wizardsandbeasts.bubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three limits that keep a bubble from being creative flight.
 *
 * <p>{@link #theCeilingIsMeasuredFromWhereTheBubbleWasBlown} is the one that matters. If the ceiling
 * ever became relative to the ground, or to the current position, a player could ratchet upward
 * indefinitely by stepping off things — and it would look exactly like working code while they did.
 */
class BubbleFloatTest {

    @Test
    void theBriefsNumbersAreWhatShipped() {
        assertEquals(25, BubbleFloat.DURATION_TICKS / 20);
        assertEquals(40, BubbleFloat.COOLDOWN_TICKS / 20);
        assertEquals(8.0, BubbleFloat.MAX_RISE, 1e-6);
    }

    @Test
    void theCooldownIsLongerThanTheFloatItGrants() {
        // Otherwise a player chains bubbles and it is flight with extra clicks.
        assertTrue(BubbleFloat.COOLDOWN_TICKS > BubbleFloat.DURATION_TICKS,
                "cooldown " + BubbleFloat.COOLDOWN_TICKS + " must outlast the float "
                        + BubbleFloat.DURATION_TICKS);
    }

    @Test
    void theCeilingIsMeasuredFromWhereTheBubbleWasBlown() {
        double anchor = 64.0;
        assertTrue(BubbleFloat.canRise(64.0, anchor), "should rise from the anchor itself");
        assertTrue(BubbleFloat.canRise(71.9, anchor), "just under the cap still rises");
        assertFalse(BubbleFloat.canRise(72.0, anchor), "the cap is exactly +8");
        assertFalse(BubbleFloat.canRise(200.0, anchor));
    }

    @Test
    void steppingOffACliffDoesNotBuyMoreClimb() {
        // Anchored at 64 and now at 40 after a fall: still allowed to rise, but only back toward 72 —
        // never 48. A ground-relative or position-relative ceiling would give the player another
        // eight blocks every time they dropped.
        double anchor = 64.0;
        assertTrue(BubbleFloat.canRise(40.0, anchor));
        assertFalse(BubbleFloat.canRise(72.5, anchor),
                "falling must not raise the ceiling");
    }

    @Test
    void risingIsGentlerThanAJump() {
        // A jump is roughly 0.42 initial velocity. The bubble has to read as being carried.
        assertTrue(BubbleFloat.RISE_SPEED > 0.0 && BubbleFloat.RISE_SPEED < 0.42,
                "rise speed " + BubbleFloat.RISE_SPEED + " should be a drift, not a leap");
    }

    @Test
    void reachingTheCeilingHoldsMotionRatherThanCuttingIt() {
        // Returning zero at the cap would drop the player out of the sky at exactly the moment they
        // expected to hover.
        double atCap = BubbleFloat.targetVelocity(72.0, 64.0, 0.13);
        assertEquals(0.13, atCap, 1e-6, "at the cap the current velocity is preserved");

        double climbing = BubbleFloat.targetVelocity(66.0, 64.0, -0.5);
        assertEquals(BubbleFloat.RISE_SPEED, climbing, 1e-6, "below the cap it targets the rise speed");
    }

    @Test
    void aFallingFloaterUnderTheCapIsStillCaught() {
        // The interesting case: popped upward, fell back below the anchor, still holding jump.
        double target = BubbleFloat.targetVelocity(60.0, 64.0, -0.8);
        assertTrue(target > 0.0, "holding jump under the cap should arrest a fall, not accelerate it");
    }

    @Test
    void theResponseEasesRatherThanSnaps() {
        assertTrue(BubbleFloat.RISE_RESPONSE > 0.0f && BubbleFloat.RISE_RESPONSE < 1.0f,
                "a response of 1 would set velocity outright and stack with a jump");
    }
}
