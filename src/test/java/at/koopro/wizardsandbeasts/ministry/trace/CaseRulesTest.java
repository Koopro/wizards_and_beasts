package at.koopro.wizardsandbeasts.ministry.trace;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The response ladder and the passage of time on a case. */
class CaseRulesTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final BlockPos HERE = new BlockPos(10, 64, 10);

    // ── the ladder ──

    @Test
    void aChildsFirstOffenceIsALetterAndTheSecondAHearing() {
        CaseRules.Decision first = CaseRules.decide(LegalClass.UNRESTRICTED, Exposure.LOW, true, 0, 0);
        assertEquals(MinistryResponse.WARNING, first.response());
        assertEquals(1, first.underageWarnings());
        CaseRules.Decision second = CaseRules.decide(LegalClass.UNRESTRICTED, Exposure.LOW, true,
                first.underageWarnings(), first.secrecyNotes());
        assertEquals(MinistryResponse.SUMMON, second.response());
    }

    @Test
    void anAdultsBreachesAreRecordedThenWarnedThenHeard() {
        CaseRules.Decision one = CaseRules.decide(LegalClass.UNRESTRICTED, Exposure.MODERATE, false, 0, 0);
        assertEquals(MinistryResponse.RECORD, one.response());
        CaseRules.Decision two = CaseRules.decide(LegalClass.UNRESTRICTED, Exposure.MODERATE, false, 0,
                one.secrecyNotes());
        assertEquals(MinistryResponse.WARNING, two.response());
        CaseRules.Decision three = CaseRules.decide(LegalClass.UNRESTRICTED, Exposure.MODERATE, false, 0,
                two.secrecyNotes());
        assertEquals(MinistryResponse.SUMMON, three.response());
        assertEquals(3, three.secrecyNotes());
    }

    @Test
    void graveMagicSkipsTheLettersButNotEveryRung() {
        assertEquals(MinistryResponse.INVESTIGATE,
                CaseRules.decide(LegalClass.DARK, Exposure.NONE, false, 0, 0).response());
        assertEquals(MinistryResponse.INVESTIGATE,
                CaseRules.decide(LegalClass.RESTRICTED, Exposure.SEVERE, false, 0, 0).response());
        assertEquals(MinistryResponse.AURORS,
                CaseRules.decide(LegalClass.UNFORGIVABLE, Exposure.NONE, false, 0, 0).response());
        assertEquals(MinistryResponse.AURORS,
                CaseRules.decide(LegalClass.RESTRICTED, Exposure.EXTREME, false, 0, 0).response());
    }

    @Test
    void notEveryIllegalActIsEquivalent() {
        assertTrue(CaseRules.decide(LegalClass.UNFORGIVABLE, Exposure.NONE, false, 0, 0).response().ordinal()
                > CaseRules.decide(LegalClass.DARK, Exposure.NONE, false, 0, 0).response().ordinal());
        assertTrue(CaseRules.decide(LegalClass.DARK, Exposure.NONE, false, 0, 0).response().ordinal()
                > CaseRules.decide(LegalClass.UNRESTRICTED, Exposure.LOW, true, 0, 0).response().ordinal());
    }

    // ── cases ──

    @Test
    void lettersOpenNoCase() {
        assertTrue(CaseRules.fold(Optional.empty(), MinistryResponse.WARNING, 1L, OVERWORLD, HERE, 0L).isEmpty());
        assertTrue(CaseRules.fold(Optional.empty(), MinistryResponse.RECORD, 1L, OVERWORLD, HERE, 0L).isEmpty());
    }

    @Test
    void aSummonsCarriesADeadline() {
        OpenCase open = CaseRules.fold(Optional.empty(), MinistryResponse.SUMMON, 7L, OVERWORLD, HERE, 1000L)
                .orElseThrow();
        assertEquals(CaseStage.SUMMONED, open.stage());
        assertEquals(1000L + CaseRules.SUMMONS_TICKS, open.deadline());
        assertEquals(HERE, open.lastKnownPos());
    }

    @Test
    void aCaseOnlyClimbsAndFollowsTheLatestReport() {
        OpenCase aurors = CaseRules.fold(Optional.empty(), MinistryResponse.AURORS, 1L, OVERWORLD, HERE, 0L)
                .orElseThrow();
        BlockPos elsewhere = new BlockPos(500, 70, 500);
        OpenCase after = CaseRules.fold(Optional.of(aurors), MinistryResponse.WARNING, 2L, OVERWORLD, elsewhere, 50L)
                .orElseThrow();
        assertEquals(CaseStage.AURORS_ASSIGNED, after.stage(), "a lesser incident never lowers a case");
        assertEquals(2, after.incidents().size());
        assertEquals(elsewhere, after.lastKnownPos(), "the Aurors go where the newest report puts the wizard");

        OpenCase investigating = CaseRules.fold(Optional.empty(), MinistryResponse.INVESTIGATE, 3L, OVERWORLD, HERE,
                0L).orElseThrow();
        OpenCase raised = CaseRules.fold(Optional.of(investigating), MinistryResponse.AURORS, 4L, OVERWORLD, HERE,
                10L).orElseThrow();
        assertEquals(CaseStage.AURORS_ASSIGNED, raised.stage());
    }

    @Test
    void anInvestigationEndsInASummonsAndAnUnansweredSummonsInAurors() {
        OpenCase open = CaseRules.fold(Optional.empty(), MinistryResponse.INVESTIGATE, 1L, OVERWORLD, HERE, 0L)
                .orElseThrow();
        assertEquals(CaseRules.Step.NOTHING,
                CaseRules.advance(open, CaseRules.INVESTIGATION_TICKS - 1).step());

        CaseRules.Advance summoned = CaseRules.advance(open, CaseRules.INVESTIGATION_TICKS);
        assertEquals(CaseRules.Step.SUMMONS_ISSUED, summoned.step());
        assertEquals(CaseStage.SUMMONED, summoned.openCase().stage());

        long deadline = summoned.openCase().deadline();
        assertEquals(CaseRules.Step.NOTHING, CaseRules.advance(summoned.openCase(), deadline - 1).step());
        CaseRules.Advance failed = CaseRules.advance(summoned.openCase(), deadline);
        assertEquals(CaseRules.Step.FAILED_TO_APPEAR, failed.step());
        assertTrue(failed.openCase().failedToAppear());
        assertEquals(CaseStage.AURORS_ASSIGNED, failed.openCase().stage());

        long search = failed.openCase().nextSearch();
        assertTrue(search > deadline, "Aurors do not arrive the instant a deadline passes");
        assertEquals(CaseRules.Step.SEARCH_DUE, CaseRules.advance(failed.openCase(), search).step());
    }

    @Test
    void aurorsFindOnlyAWizardStillNearWhereTheyWereReported() {
        double near = 10.0 * 10.0;
        double far = (CaseRules.AUROR_SEARCH_RADIUS + 1) * (CaseRules.AUROR_SEARCH_RADIUS + 1);
        assertTrue(CaseRules.aurorsFind(true, true, near));
        assertFalse(CaseRules.aurorsFind(true, true, far));
        assertFalse(CaseRules.aurorsFind(true, false, near), "another dimension is not where they were reported");
        assertFalse(CaseRules.aurorsFind(false, true, near), "nobody is arrested while dead");
    }
}
