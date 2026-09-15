package at.koopro.wizardsandbeasts.spell.clash;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellClashRulesTest {

    @Test
    void ordinarySpellsLock() {
        assertTrue(SpellClashRules.canClash("stupefy", "expelliarmus"));
        assertTrue(SpellClashRules.canClash("confringo", "depulso"));
    }

    /** Namespaced and bare ids name the same spell — the clash rule must not care which arrives. */
    @Test
    void namespacedIdsAreTheSameSpell() {
        assertTrue(SpellClashRules.canClash("wizards_and_beasts:avada_kedavra", "expelliarmus"));
        assertTrue(SpellClashRules.canClash("avada_kedavra", "wizards_and_beasts:expelliarmus"));
        assertFalse(SpellClashRules.canClash("wizards_and_beasts:avada_kedavra", "stupefy"));
    }

    /** Canon: nothing meets the Killing Curse but Expelliarmus. */
    @Test
    void onlyExpelliarmusMeetsTheKillingCurse() {
        assertTrue(SpellClashRules.canClash("avada_kedavra", "expelliarmus"));
        assertTrue(SpellClashRules.canClash("expelliarmus", "avada_kedavra"));
        assertFalse(SpellClashRules.canClash("avada_kedavra", "stupefy"));
        assertFalse(SpellClashRules.canClash("confringo", "avada_kedavra"));
    }

    /** Priori Incantatem itself: two Killing Curses lock rather than cancelling silently. */
    @Test
    void twoKillingCursesLock() {
        assertTrue(SpellClashRules.canClash("avada_kedavra", "avada_kedavra"));
    }

    @Test
    void aMissingSpellNeverClashes() {
        assertFalse(SpellClashRules.canClash(null, "stupefy"));
        assertFalse(SpellClashRules.canClash("stupefy", null));
    }

    @Test
    void evenPowerDoesNotDrift() {
        assertEquals(0.0f, SpellClashRules.bias(1.0f, 1.0f), 1.0e-6);
        assertEquals(0.0f, SpellClashRules.bias(2.5f, 2.5f), 1.0e-6);
    }

    /** The axis runs A to B, so a stronger A must push the lock towards B — a positive bias. */
    @Test
    void theStrongerCastPushesTowardsTheWeaker() {
        assertTrue(SpellClashRules.bias(3.0f, 1.0f) > 0.0f);
        assertTrue(SpellClashRules.bias(1.0f, 3.0f) < 0.0f);
    }

    /** Bias is bounded even when a multiplier is absurd or nonsensical, so drift cannot fling the clash away. */
    @Test
    void biasStaysWithinItsBounds() {
        assertTrue(SpellClashRules.bias(1000.0f, 0.0f) <= 1.0f);
        assertTrue(SpellClashRules.bias(0.0f, 1000.0f) >= -1.0f);
        assertTrue(SpellClashRules.bias(-5.0f, 1.0f) >= -1.0f);
    }

    /** An even duel holds longest; a mismatch collapses sooner. Both stay inside the authored window. */
    @Test
    void lifetimeFollowsHowEvenTheDuelIs() {
        int even = SpellClashRules.lifetimeTicks(1.0f, 1.0f);
        int lopsided = SpellClashRules.lifetimeTicks(4.0f, 1.0f);

        assertEquals(SpellClashRules.MAX_LIFETIME_TICKS, even);
        assertTrue(lopsided < even, "a lopsided clash should break before an even one");
        assertTrue(lopsided >= SpellClashRules.MIN_LIFETIME_TICKS);
    }

    /**
     * Two bolts at 1.8 blocks a tick, two blocks apart, cross in the middle of the tick. Both ends of
     * the tick are 1.6 or more apart, so a check on where they ended up never sees them meet.
     */
    @Test
    void boltsThatCrossBetweenTicksMeetMidTick() {
        // A runs 0 -> 1.8, B runs 2.0 -> 0.2, both along x.
        double t = SpellClashRules.closestApproachTime(0.0 - 2.0, 0, 0, (1.8 - 0.0) - (0.2 - 2.0), 0, 0);

        assertEquals(2.0 / 3.6, t, 1.0e-9);
        double gap = Math.abs((0.0 + 1.8 * t) - (2.0 - 1.8 * t));
        assertTrue(gap < SpellClashRules.CLASH_RADIUS, "they pass through each other at t=" + t);
    }

    /** Bolts on parallel lanes a block apart are closest wherever they are level, and that is too far. */
    @Test
    void parallelLanesStayApart() {
        double t = SpellClashRules.closestApproachTime(-1.0, 1.0, 0, 3.6, 0, 0);

        assertEquals(1.0 / 3.6, t, 1.0e-9);
        double gapX = -1.0 + 3.6 * t;
        assertTrue(Math.hypot(gapX, 1.0) > SpellClashRules.CLASH_RADIUS);
    }

    /** The closest moment is never outside the tick: bolts flying apart were closest at its start. */
    @Test
    void closestMomentIsClampedToTheTick() {
        assertEquals(0.0, SpellClashRules.closestApproachTime(1.0, 0, 0, 3.6, 0, 0));
        assertEquals(1.0, SpellClashRules.closestApproachTime(-10.0, 0, 0, 3.6, 0, 0));
    }

    /** Two bolts not moving relative to each other have no better moment than the start. */
    @Test
    void boltsMovingTogetherAreClosestAtTheStart() {
        assertEquals(0.0, SpellClashRules.closestApproachTime(0.3, 0.1, 0, 0, 0, 0));
    }
}
