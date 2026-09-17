package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hearings: every verdict follows from stated facts and names its reasons. */
class WizengamotTest {

    private static Wizengamot.Charge charge(LegalClass legalClass, Exposure exposure, boolean underage,
                                            boolean inDanger) {
        Optional<MagicalOffence> offence = legalClass == LegalClass.UNFORGIVABLE
                ? Optional.of(MagicalOffence.CRUCIO) : Optional.empty();
        return new Wizengamot.Charge(legalClass, exposure, underage, inDanger, offence, false);
    }

    private static Wizengamot.Ruling rule(List<Wizengamot.Charge> charges, int underageWarnings, int secrecyNotes,
                                          boolean appeared) {
        return Wizengamot.rule(new Wizengamot.Hearing(charges, underageWarnings, secrecyNotes, appeared));
    }

    @Test
    void underageMagicAgainstALifeThreateningDangerIsCleared() {
        // Order of the Phoenix: the Patronus against the Dementors.
        Wizengamot.Ruling ruling = rule(List.of(charge(LegalClass.UNRESTRICTED, Exposure.MODERATE, true, true)),
                1, 1, true);
        assertEquals(Verdict.DISMISSED, ruling.verdict());
        assertTrue(ruling.reasons().contains("mitigated_danger"));
        assertTrue(ruling.filed().isEmpty());
    }

    @Test
    void dangerDoesNotExcuseAnUnforgivable() {
        Wizengamot.Ruling ruling = rule(List.of(charge(LegalClass.UNFORGIVABLE, Exposure.NONE, true, true)),
                0, 0, true);
        assertEquals(Verdict.AZKABAN_REFERRAL, ruling.verdict());
    }

    @Test
    void aChildIsWarnedFirstAndLosesTheWandAfterAWarning() {
        Wizengamot.Ruling first = rule(List.of(charge(LegalClass.UNRESTRICTED, Exposure.LOW, true, false)),
                0, 0, true);
        assertEquals(Verdict.WARNING, first.verdict());
        assertEquals(Optional.of(MagicalOffence.UNDERAGE_MAGIC), first.filed());

        Wizengamot.Ruling after = rule(List.of(charge(LegalClass.UNRESTRICTED, Exposure.LOW, true, false)),
                1, 0, true);
        assertEquals(Verdict.WAND_CONFISCATION, after.verdict());
        assertEquals(Wizengamot.CONFISCATION_SHORT_TICKS, after.confiscationTicks());
    }

    @Test
    void theUnforgivablesMeanAzkabanAndFileTheirOwnOffence() {
        Wizengamot.Ruling ruling = rule(List.of(
                charge(LegalClass.UNRESTRICTED, Exposure.MODERATE, false, false),
                charge(LegalClass.UNFORGIVABLE, Exposure.NONE, false, false)), 0, 1, true);
        assertEquals(Verdict.AZKABAN_REFERRAL, ruling.verdict());
        assertEquals(Optional.of(MagicalOffence.CRUCIO), ruling.filed());
        assertEquals(Wizengamot.CONFISCATION_LONG_TICKS, ruling.confiscationTicks());
    }

    @Test
    void verdictsScaleWithWhatWasDone() {
        assertEquals(Verdict.AZKABAN_REFERRAL,
                rule(List.of(charge(LegalClass.RESTRICTED, Exposure.EXTREME, false, false)), 0, 1, true).verdict());
        assertEquals(Verdict.WAND_CONFISCATION,
                rule(List.of(charge(LegalClass.DARK, Exposure.NONE, false, false)), 0, 0, true).verdict());
        Wizengamot.Ruling severe = rule(List.of(charge(LegalClass.RESTRICTED, Exposure.SEVERE, false, false)),
                0, 1, true);
        assertEquals(Verdict.FINE, severe.verdict());
        assertEquals(Optional.of(MagicalOffence.STATUTE_OF_SECRECY_BREACH), severe.filed());
        assertEquals(Verdict.FINE, rule(List.of(charge(LegalClass.UNRESTRICTED, Exposure.MODERATE, false, false)),
                0, CaseRules.BREACHES_BEFORE_HEARING, true).verdict());
    }

    @Test
    void aChildIsNeverFined() {
        Wizengamot.Ruling ruling = rule(List.of(charge(LegalClass.RESTRICTED, Exposure.SEVERE, true, false)),
                0, 1, true);
        assertEquals(Verdict.WAND_CONFISCATION, ruling.verdict());
        assertTrue(ruling.filed().map(o -> o.remedy() != MagicalOffence.Remedy.FINE).orElse(true));
    }

    @Test
    void failingToAppearMakesAnyVerdictWorse() {
        Wizengamot.Ruling appeared = rule(List.of(charge(LegalClass.UNRESTRICTED, Exposure.LOW, true, false)),
                0, 0, true);
        Wizengamot.Ruling brought = rule(List.of(charge(LegalClass.UNRESTRICTED, Exposure.LOW, true, false)),
                0, 0, false);
        assertTrue(brought.verdict().ordinal() > appeared.verdict().ordinal());
        assertTrue(brought.reasons().contains("failed_to_appear"));
        assertTrue(brought.confiscationTicks() >= Wizengamot.CONFISCATION_SHORT_TICKS);
    }

    @Test
    void aWandExaminationCanBringOldDarkMagicBeforeTheCourt() {
        Wizengamot.Charge found = new Wizengamot.Charge(LegalClass.UNFORGIVABLE, Exposure.NONE, false, false,
                Optional.of(MagicalOffence.IMPERIO), true);
        Wizengamot.Ruling ruling = rule(List.of(charge(LegalClass.UNRESTRICTED, Exposure.MODERATE, false, false),
                found), 0, 3, true);
        assertEquals(Verdict.AZKABAN_REFERRAL, ruling.verdict());
        assertTrue(ruling.reasons().contains("wand_examination"));
    }

    @Test
    void everyRulingSaysWhy() {
        for (LegalClass legalClass : LegalClass.values()) {
            for (Exposure exposure : Exposure.values()) {
                for (boolean underage : new boolean[] {false, true}) {
                    Wizengamot.Ruling ruling = rule(List.of(charge(legalClass, exposure, underage, false)), 0, 0,
                            true);
                    assertTrue(!ruling.reasons().isEmpty(), legalClass + "/" + exposure + " gave no reason");
                }
            }
        }
    }
}
