package at.koopro.wizardsandbeasts.ministry.law;

import at.koopro.wizardsandbeasts.ministry.trace.Verdict;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.standing.StandingBand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the law does to a wizard's reputations. */
class LawReputationRulesTest {

    @Test
    void theHeavierTheVerdictTheWorseTheNews() {
        float previous = -1.0f;
        for (Verdict verdict : Verdict.values()) {
            float penalty = LawReputationRules.publicPenalty(verdict);
            assertTrue(penalty > previous || verdict == Verdict.DISMISSED, verdict.name());
            previous = penalty;
        }
        assertEquals(0.0f, LawReputationRules.publicPenalty(Verdict.DISMISSED));
    }

    @Test
    void thoseWhoEnforceTheLawAreHeldToItTwiceOver() {
        for (Verdict verdict : Verdict.values()) {
            assertEquals(2.0f * LawReputationRules.professionPenalty(verdict, Profession.HERBOLOGIST),
                    LawReputationRules.professionPenalty(verdict, Profession.AUROR), verdict.name());
        }
        assertTrue(LawReputationRules.enforcesTheLaw(Profession.HIT_WIZARD));
        assertFalse(LawReputationRules.enforcesTheLaw(null));
    }

    @Test
    void answeringBeatsSurrenderingBeatsBeingCaught() {
        int answered = LawReputationRules.conductFor(true, false, false);
        int surrendered = LawReputationRules.conductFor(false, true, false);
        int caught = LawReputationRules.conductFor(false, false, false);
        assertTrue(answered > surrendered && surrendered > caught);
        assertTrue(caught < 0, "being caught after ignoring a summons counts against");
        assertTrue(LawReputationRules.conductFor(true, false, true) > answered, "an honest plea is cooperation");
    }

    @Test
    void anOverturnedVerdictIsRetractedInFullAndAnUpheldOneNotAtAll() {
        assertEquals(LawReputationRules.publicPenalty(Verdict.WAND_CONFISCATION),
                LawReputationRules.publicRestored(Verdict.WAND_CONFISCATION, Verdict.DISMISSED));
        assertEquals(0.0f, LawReputationRules.publicRestored(Verdict.FINE, Verdict.FINE));
        assertEquals(0.0f, LawReputationRules.publicRestored(Verdict.WARNING, Verdict.FINE),
                "an appeal never costs reputation");
    }

    @Test
    void onlyThePublicsPariahIsDisbelievedAndOnlyGoodStandingIsAReference() {
        assertFalse(LawReputationRules.credibleWitness(StandingBand.STRONG_NEGATIVE));
        assertTrue(LawReputationRules.credibleWitness(StandingBand.LEANING_NEGATIVE));
        assertTrue(LawReputationRules.goodStanding(StandingBand.LEANING_POSITIVE));
        assertFalse(LawReputationRules.goodStanding(StandingBand.NEUTRAL));
    }
}
