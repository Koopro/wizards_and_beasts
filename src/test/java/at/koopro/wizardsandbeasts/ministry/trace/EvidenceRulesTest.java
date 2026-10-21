package at.koopro.wizardsandbeasts.ministry.trace;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What evidence is worth — and the caps that stop players manufacturing a case. */
class EvidenceRulesTest {

    private static final long INCIDENT = 7L;

    private static Evidence from(EvidenceKind kind, UUID source) {
        return Evidence.from(kind, INCIDENT, source, "", 0L);
    }

    @Test
    void anOfficialsReportAloneIdentifiesAndConvictsUpToDarkMagic() {
        List<Evidence> evidence = List.of(from(EvidenceKind.OFFICIAL_REPORT, UUID.randomUUID()));
        int strength = EvidenceRules.strength(evidence);
        assertTrue(EvidenceRules.identifies(evidence));
        assertTrue(EvidenceRules.meetsStandard(strength, LegalClass.DARK, 0));
        assertTrue(EvidenceRules.meetsStandard(strength, LegalClass.UNFORGIVABLE, 0));
    }

    @Test
    void oneMuggleIsNotEnoughButTwoPutANameToIt() {
        assertFalse(EvidenceRules.identifies(List.of(from(EvidenceKind.MUGGLE_ACCOUNT, UUID.randomUUID()))));
        assertTrue(EvidenceRules.identifies(List.of(
                from(EvidenceKind.MUGGLE_ACCOUNT, UUID.randomUUID()),
                from(EvidenceKind.MUGGLE_ACCOUNT, UUID.randomUUID()))));
    }

    @Test
    void theSameSourceCountsOnceAtItsBest() {
        UUID muggle = UUID.randomUUID();
        List<Evidence> evidence = List.of(
                from(EvidenceKind.MUGGLE_ACCOUNT, muggle),
                from(EvidenceKind.WITNESS_INTERVIEW, muggle),
                from(EvidenceKind.MUGGLE_ACCOUNT, muggle));
        assertEquals(EvidenceKind.MUGGLE_ACCOUNT.weight(), EvidenceRules.strength(evidence));
    }

    @Test
    void sceneResidueCountsOnceHoweverManyAurorsExamine() {
        List<Evidence> evidence = List.of(
                from(EvidenceKind.SCENE_RESIDUE, UUID.randomUUID()),
                from(EvidenceKind.SCENE_RESIDUE, UUID.randomUUID()),
                Evidence.of(EvidenceKind.SCENE_RESIDUE, INCIDENT, 0L));
        assertEquals(EvidenceKind.SCENE_RESIDUE.weight(), EvidenceRules.strength(evidence));
    }

    @Test
    void anyNumberOfPlayersAloneCanNeverPutANameToAnIncident() {
        List<Evidence> evidence = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            evidence.add(from(EvidenceKind.WIZARD_TESTIMONY, UUID.randomUUID()));
        }
        int strength = EvidenceRules.strength(evidence);
        assertTrue(strength < EvidenceRules.IDENTIFY, "players alone reached " + strength);
        assertFalse(EvidenceRules.identifies(evidence));
        for (LegalClass legalClass : LegalClass.values()) {
            assertFalse(EvidenceRules.meetsStandard(strength, legalClass, 0), legalClass.name());
        }
    }

    @Test
    void playerTestimonyIsCappedEvenWhenCorroborated() {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(from(EvidenceKind.MUGGLE_ACCOUNT, UUID.randomUUID()));
        for (int i = 0; i < 20; i++) {
            evidence.add(from(EvidenceKind.WIZARD_TESTIMONY, UUID.randomUUID()));
        }
        assertEquals(EvidenceKind.MUGGLE_ACCOUNT.weight() + EvidenceRules.PLAYER_TESTIMONY_CAP,
                EvidenceRules.strength(evidence));
    }

    @Test
    void playerTestimonyCorroboratesOneMuggleIntoAName() {
        List<Evidence> evidence = List.of(
                from(EvidenceKind.MUGGLE_ACCOUNT, UUID.randomUUID()),
                from(EvidenceKind.WIZARD_TESTIMONY, UUID.randomUUID()));
        assertTrue(EvidenceRules.identifies(evidence));
    }

    @Test
    void anAurorsSceneWorkAndOneInterviewIdentify() {
        List<Evidence> evidence = List.of(
                from(EvidenceKind.SCENE_RESIDUE, UUID.randomUUID()),
                from(EvidenceKind.WITNESS_INTERVIEW, UUID.randomUUID()));
        assertTrue(EvidenceRules.identifies(evidence));
        assertFalse(EvidenceRules.meetsStandard(EvidenceRules.strength(evidence), LegalClass.UNFORGIVABLE, 0),
                "scene work and one interview must not be enough for an Unforgivable");
    }

    @Test
    void graverChargesAndAppealsAskForMore() {
        assertTrue(EvidenceRules.standardFor(LegalClass.UNRESTRICTED) <= EvidenceRules.standardFor(LegalClass.DARK));
        assertTrue(EvidenceRules.standardFor(LegalClass.DARK) < EvidenceRules.standardFor(LegalClass.UNFORGIVABLE));
        int bar = EvidenceRules.standardFor(LegalClass.RESTRICTED);
        assertTrue(EvidenceRules.meetsStandard(bar, LegalClass.RESTRICTED, 0));
        assertFalse(EvidenceRules.meetsStandard(bar, LegalClass.RESTRICTED, EvidenceRules.APPEAL_BONUS));
    }

    @Test
    void strengthIsBounded() {
        List<Evidence> evidence = List.of(
                from(EvidenceKind.WAND_EXAMINATION, UUID.randomUUID()),
                from(EvidenceKind.CONFESSION, UUID.randomUUID()),
                from(EvidenceKind.OFFICIAL_REPORT, UUID.randomUUID()));
        assertEquals(EvidenceRules.MAX_STRENGTH, EvidenceRules.strength(evidence));
        assertEquals(0, EvidenceRules.strength(List.of()));
    }

    @Test
    void residueFadesAfterThreeDays() {
        assertTrue(EvidenceRules.sceneFresh(1000L, 1000L + EvidenceRules.SCENE_FRESH_TICKS));
        assertFalse(EvidenceRules.sceneFresh(1000L, 1001L + EvidenceRules.SCENE_FRESH_TICKS));
    }
}
