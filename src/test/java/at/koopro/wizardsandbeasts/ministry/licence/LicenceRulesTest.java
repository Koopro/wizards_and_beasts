package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.broom.BroomTier;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The licence's tariff, pinned rather than described.
 *
 * <p>What is worth testing here is the set of things a player would feel immediately if they broke:
 * a starter broom that suddenly needs papers, an endorsement ladder that can be climbed in one step,
 * a forgery that is either never caught or always caught.
 */
class LicenceRulesTest {

    @Test
    void theStarterBroomNeverNeedsPapers() {
        // The broom every player gets first must not be gated, or the licence stops being something
        // you grow into and becomes a wall in front of the flight module.
        assertFalse(LicenceRules.requiresBroomLicence(BroomTier.SCHOOL));
        assertFalse(LicenceRules.requiresBroomLicence(BroomTier.STANDARD));
    }

    @Test
    void everythingFromRacingUpwardsNeedsPapers() {
        assertTrue(LicenceRules.requiresBroomLicence(BroomTier.RACING));
        assertTrue(LicenceRules.requiresBroomLicence(BroomTier.ELITE));
        assertTrue(LicenceRules.requiresBroomLicence(BroomTier.LEGENDARY));
    }

    @Test
    void theBroomGateIsMonotonicInTier() {
        // Once a tier needs papers, every faster tier must too — otherwise there is a broom that
        // outruns an Auror and is somehow less regulated than the one below it.
        boolean seenGated = false;
        for (BroomTier tier : BroomTier.values()) {
            boolean gated = LicenceRules.requiresBroomLicence(tier);
            if (seenGated) {
                assertTrue(gated, tier + " must need papers: a slower tier already does");
            }
            seenGated |= gated;
        }
        assertTrue(seenGated, "no tier needs a licence at all");
    }

    @Test
    void theEndorsementLadderClimbsAndHasATop() {
        assertEquals(OWLGrade.A, LicenceRules.gradeRequiredFor(1));
        assertEquals(OWLGrade.E, LicenceRules.gradeRequiredFor(2));
        assertEquals(OWLGrade.O, LicenceRules.gradeRequiredFor(3));

        assertNull(LicenceRules.gradeRequiredFor(0), "rank 0 is issued, not endorsed to");
        assertNull(LicenceRules.gradeRequiredFor(LicenseType.MAX_RANK + 1), "there must be a ceiling");
        assertNull(LicenceRules.gradeRequiredFor(-1));
    }

    @Test
    void eachRungAsksForStrictlyMoreThanTheLast() {
        for (int rank = 2; rank <= LicenseType.MAX_RANK; rank++) {
            OWLGrade lower = LicenceRules.gradeRequiredFor(rank - 1);
            OWLGrade here = LicenceRules.gradeRequiredFor(rank);
            assertNotNull(lower);
            assertNotNull(here);
            assertTrue(here.value > lower.value,
                    "rank " + rank + " must be harder to reach than rank " + (rank - 1));
        }
    }

    @Test
    void aFailingGradeNeverBuysAnEndorsement() {
        OWLGrade firstRung = LicenceRules.gradeRequiredFor(1);
        assertNotNull(firstRung);
        for (OWLGrade grade : OWLGrade.values()) {
            if (!grade.passing) {
                assertFalse(LicenceRules.gradeSatisfies(grade, firstRung),
                        grade + " is a fail and must not endorse anything");
            }
        }
        assertTrue(LicenceRules.gradeSatisfies(OWLGrade.O, firstRung), "an O covers everything below it");
    }

    @Test
    void ranksAreCumulative() {
        assertTrue(LicenceRules.rankSatisfies(3, 1), "a higher endorsement covers a lower requirement");
        assertTrue(LicenceRules.rankSatisfies(0, 0), "rank 0 satisfies a job that asks for nothing");
        assertFalse(LicenceRules.rankSatisfies(1, 2));
    }

    @Test
    void forgeryIsWorthDoingAndWorthFearing() {
        float chance = LicenceRules.FORGERY_DETECTION_CHANCE;
        assertTrue(chance > 0.0f && chance < 1.0f,
                "a forgery that is never caught, or always caught, is not a decision");

        // Roughly a coin-flip across four or five dealings — the shape the whole mechanic depends on.
        double survivesFive = Math.pow(1.0 - chance, 5);
        assertTrue(survivesFive > 0.25 && survivesFive < 0.75,
                "five dealings on a forgery should be a real gamble; got " + survivesFive);
    }

    @Test
    void theTrespassBillIsNotChargedEveryTick() {
        assertTrue(LicenceRules.TRESPASS_COOLDOWN_TICKS > LicenceRules.AREA_CHECK_INTERVAL_TICKS,
                "standing in a lobby must be one offence, not one per check");
    }

    @Test
    void ministryDensityNeedsMoreThanAStrayBlock() {
        assertFalse(LicenceRules.isMinistryDensity(0));
        assertFalse(LicenceRules.isMinistryDensity(1),
                "one Ministry block is decoration, not premises");
        assertFalse(LicenceRules.isMinistryDensity(LicenceRules.AREA_BLOCK_THRESHOLD - 1));
        assertTrue(LicenceRules.isMinistryDensity(LicenceRules.AREA_BLOCK_THRESHOLD));
    }

    @Test
    void theScanBoxCanActuallyHoldTheThreshold() {
        // A threshold larger than the sampled cube could never be met, and the gate would be dead code.
        int side = LicenceRules.AREA_SCAN_RADIUS * 2 + 1;
        assertTrue(LicenceRules.AREA_BLOCK_THRESHOLD < side * side * side,
                "the threshold must be reachable inside the scan box");
    }
}
