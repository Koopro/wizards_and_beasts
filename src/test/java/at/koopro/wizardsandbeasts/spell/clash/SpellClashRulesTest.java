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

    // ── a held lock ─────────────────────────────────────────────────────────────────────────────

    private static final int IN_GRACE = SpellClashRules.HOLD_GRACE_TICKS - 1;
    private static final int AFTER_GRACE = SpellClashRules.HOLD_GRACE_TICKS;
    private static final double FAR = 3.0;

    /** Nobody has picked the lock up yet, but there is still time to: it waits. */
    @Test
    void nobodyHoldingInsideTheGraceWaits() {
        assertEquals(SpellClashRules.Outcome.HOLD,
                SpellClashRules.judge(false, false, false, false, IN_GRACE, FAR, FAR));
    }

    /** One caster picked it up in time and the other never did: the one holding wins. */
    @Test
    void neverPickingItUpLosesOnceTheGraceIsOver() {
        assertEquals(SpellClashRules.Outcome.A_WINS,
                SpellClashRules.judge(true, true, false, false, AFTER_GRACE, FAR, FAR));
        assertEquals(SpellClashRules.Outcome.B_WINS,
                SpellClashRules.judge(false, false, true, true, AFTER_GRACE, FAR, FAR));
    }

    /** Letting go loses at once — the grace is for picking the lock up, not for putting it down. */
    @Test
    void lettingGoLosesEvenInsideTheGrace() {
        assertEquals(SpellClashRules.Outcome.B_WINS,
                SpellClashRules.judge(false, true, true, true, IN_GRACE, FAR, FAR));
        assertEquals(SpellClashRules.Outcome.A_WINS,
                SpellClashRules.judge(true, true, false, true, IN_GRACE, FAR, FAR));
    }

    /** Both gave up, one by letting go and one by never holding: nobody is left to win. */
    @Test
    void bothGivingUpBreaksTheLock() {
        assertEquals(SpellClashRules.Outcome.BREAK,
                SpellClashRules.judge(false, false, false, false, AFTER_GRACE, FAR, FAR));
        assertEquals(SpellClashRules.Outcome.BREAK,
                SpellClashRules.judge(false, true, false, false, AFTER_GRACE, FAR, FAR));
    }

    /** Pushed onto a caster's wand, that caster loses; the one pushing wins. */
    @Test
    void theJointReachingAWandLosesThatCaster() {
        double reach = SpellClashRules.WAND_REACH;
        assertEquals(SpellClashRules.Outcome.B_WINS,
                SpellClashRules.judge(true, true, true, true, AFTER_GRACE, reach, FAR));
        assertEquals(SpellClashRules.Outcome.A_WINS,
                SpellClashRules.judge(true, true, true, true, AFTER_GRACE, FAR, reach));
        assertEquals(SpellClashRules.Outcome.HOLD,
                SpellClashRules.judge(true, true, true, true, AFTER_GRACE, reach + 0.01, FAR));
    }

    /** Both holding, joint clear of both wands: no time limit ends it. */
    @Test
    void anEvenHeldLockHasNoTimeLimit() {
        assertEquals(SpellClashRules.Outcome.HOLD,
                SpellClashRules.judge(true, true, true, true, 20 * 60 * 10, FAR, FAR));
    }

    /** The stronger cast pushes the joint away from itself, and an even duel does not move. */
    @Test
    void theJointMovesAwayFromTheStrongerCast() {
        assertTrue(SpellClashRules.jointStep(3.0f, 1.0f) > 0.0);
        assertTrue(SpellClashRules.jointStep(1.0f, 3.0f) < 0.0);
        assertEquals(0.0, SpellClashRules.jointStep(1.5f, 1.5f), 1.0e-9);
        assertTrue(Math.abs(SpellClashRules.jointStep(1000.0f, 0.0f)) <= SpellClashRules.DRIFT_PER_TICK + 1.0e-9);
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

    /** Bolts flying at each other are head-on; two converging on one target from the same side are not. */
    @Test
    void onlyBoltsFlyingAtEachOtherAreHeadOn() {
        assertTrue(SpellClashRules.headOn(-1.8 * 1.8));
        // Two allies 4 blocks apart firing at one enemy 10 blocks ahead: about 23 degrees between them.
        double dot = (0.2 * -0.2) + (1.0 * 1.0);
        assertFalse(SpellClashRules.headOn(dot));
        assertFalse(SpellClashRules.headOn(0.0), "bolts crossing at a right angle are not duelling");
    }

    /** Two bolts not moving relative to each other have no better moment than the start. */
    @Test
    void boltsMovingTogetherAreClosestAtTheStart() {
        assertEquals(0.0, SpellClashRules.closestApproachTime(0.3, 0.1, 0, 0, 0, 0));
    }
}
