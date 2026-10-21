package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.career.CareerProfile;
import at.koopro.wizardsandbeasts.owl.Profession;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The legal way to the papers a gate asks for: the licence office, the commerce allowance, suspensions. */
class LicenceOfficeRulesTest {

    @Test
    void testsRegistersAndWarrantsAreNotSoldOverTheCounter() {
        assertFalse(LicenceRules.soldAtOffice(LicenseType.APPARITION));
        assertFalse(LicenceRules.soldAtOffice(LicenseType.ANIMAGUS));
        assertFalse(LicenceRules.soldAtOffice(LicenseType.AUROR_TRAINEE));
        assertTrue(LicenceRules.soldAtOffice(LicenseType.MAGICAL_COMMERCE));
        assertTrue(LicenceRules.soldAtOffice(LicenseType.RESTRICTED_MAGIC));
        assertTrue(LicenceRules.soldAtOffice(LicenseType.DANGEROUS_CREATURES));
        assertEquals(LicenceRules.Application.NOT_SOLD_HERE, LicenceRules.apply(LicenseType.APPARITION, OWLGrade.O,
                false, false, false, false, false));
    }

    @Test
    void aSuspensionAnOpenCaseOrAManhuntRefuseBeforeAnythingElse() {
        assertEquals(LicenceRules.Application.SUSPENDED, LicenceRules.apply(LicenseType.BROOM, OWLGrade.O,
                true, true, true, true, true));
        assertEquals(LicenceRules.Application.OPEN_CASE, LicenceRules.apply(LicenseType.BROOM, OWLGrade.O,
                false, true, true, false, false));
        assertEquals(LicenceRules.Application.DISTRUSTED, LicenceRules.apply(LicenseType.BROOM, OWLGrade.O,
                false, false, true, false, false));
    }

    @Test
    void aHeldLicenceIsRenewedOnlyOnceItHasExpired() {
        assertEquals(LicenceRules.Application.ALREADY_HELD, LicenceRules.apply(LicenseType.BROOM, OWLGrade.O,
                false, false, false, true, false));
        assertEquals(LicenceRules.Application.RENEW, LicenceRules.apply(LicenseType.BROOM, OWLGrade.P,
                false, false, false, true, true), "a renewal does not re-examine");
    }

    @Test
    void licensedDarkPracticeAsksForMoreThanAPass() {
        assertEquals(LicenceRules.Application.GRADE, LicenceRules.apply(LicenseType.RESTRICTED_MAGIC, OWLGrade.A,
                false, false, false, false, false));
        assertEquals(LicenceRules.Application.ISSUE, LicenceRules.apply(LicenseType.RESTRICTED_MAGIC, OWLGrade.E,
                false, false, false, false, false));
        assertEquals(LicenceRules.Application.ISSUE, LicenceRules.apply(LicenseType.MAGICAL_COMMERCE, OWLGrade.A,
                false, false, false, false, false));
        assertEquals(LicenceRules.Application.GRADE, LicenceRules.apply(LicenseType.MAGICAL_COMMERCE, OWLGrade.P,
                false, false, false, false, false));
    }

    @Test
    void aZeroTermIssuesALicenceThatNeverExpires() {
        assertEquals(LicenseData.NEVER_EXPIRES, LicenceRules.expiryFor(500L, 0));
        assertEquals(500L + 56 * 24000L, LicenceRules.expiryFor(500L, 56));
    }

    @Test
    void theCommerceAllowanceRefusesOnlyPastItsLimit() {
        assertEquals(Long.MAX_VALUE, CommerceAllowance.left(0L, 1_000_000L), "0 means no limit");
        assertEquals(400L, CommerceAllowance.left(1000L, 600L));
        assertEquals(0L, CommerceAllowance.left(1000L, 5000L));
    }

    @Test
    void breedingNeedsALicenceOnlyForTheDangerousBeasts() {
        assertTrue(DangerousCreatureLicensing.needsLicence(4, 0));
        assertTrue(DangerousCreatureLicensing.needsLicence(1, 5));
        assertFalse(DangerousCreatureLicensing.needsLicence(3, 3));
    }

    @Test
    void theNewOffencesAreFinesNotSentences() {
        assertEquals(MagicalOffence.Remedy.FINE, MagicalOffence.UNLICENSED_DANGEROUS_CREATURE.remedy());
        assertEquals(MagicalOffence.Remedy.FINE, MagicalOffence.BRIBERY_OF_OFFICIAL.remedy());
        for (MagicalOffence offence : MagicalOffence.values()) {
            if (offence.remedy() == MagicalOffence.Remedy.FINE) {
                assertTrue(MagicalOffence.BRIBERY_OF_OFFICIAL.fineKnuts() >= offence.fineKnuts(),
                        "a failed bribe is the dearest fine on the books: " + offence);
            }
        }
    }

    @Test
    void thoseWhoWorkWithDarkCursesCarryARestrictedMagicLicence() {
        assertTrue(CareerProfile.papersFor(Map.of(), Profession.AUROR).contains(LicenseType.RESTRICTED_MAGIC));
        assertTrue(CareerProfile.papersFor(Map.of(), Profession.CURSE_BREAKER).contains(LicenseType.RESTRICTED_MAGIC));
        assertFalse(CareerProfile.papersFor(Map.of(), Profession.HERBOLOGIST).contains(LicenseType.RESTRICTED_MAGIC));
    }
}
