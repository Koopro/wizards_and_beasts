package at.koopro.wizardsandbeasts.ministry.trace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who may work a case — the guarantees that keep player Aurors from being turned against other players. */
class CaseworkRulesTest {

    private static final UUID AUROR = UUID.randomUUID();
    private static final UUID SUSPECT = UUID.randomUUID();

    @Test
    void onlyAnAurorMayTakeACase() {
        assertEquals(CaseworkRules.Result.NOT_AUROR,
                CaseworkRules.take(AUROR, false, Optional.of(SUSPECT), List.of(), Optional.empty(), 0));
        assertTrue(CaseworkRules.take(AUROR, true, Optional.of(SUSPECT), List.of(), Optional.empty(), 0).ok());
    }

    @Test
    void nobodyWorksTheirOwnCaseOrOneTheyWitnessed() {
        assertEquals(CaseworkRules.Result.OWN_CASE,
                CaseworkRules.take(AUROR, true, Optional.of(AUROR), List.of(), Optional.empty(), 0));
        assertEquals(CaseworkRules.Result.WITNESS_TO_CASE,
                CaseworkRules.take(AUROR, true, Optional.of(SUSPECT), List.of(AUROR), Optional.empty(), 0));
    }

    @Test
    void aHeldCaseIsTheHoldersAndAurorsHoldOnlyAFew() {
        UUID other = UUID.randomUUID();
        assertEquals(CaseworkRules.Result.HELD_BY_ANOTHER,
                CaseworkRules.take(AUROR, true, Optional.of(SUSPECT), List.of(), Optional.of(other), 0));
        assertTrue(CaseworkRules.take(AUROR, true, Optional.of(SUSPECT), List.of(), Optional.of(AUROR),
                CaseworkRules.MAX_HELD).ok(), "re-taking a case already held is not a new one");
        assertEquals(CaseworkRules.Result.TOO_MANY_CASES, CaseworkRules.take(AUROR, true, Optional.of(SUSPECT),
                List.of(), Optional.empty(), CaseworkRules.MAX_HELD));
    }

    @Test
    void sceneExaminationNeedsTheHolderAtAFreshScene() {
        double near = 4.0;
        assertEquals(CaseworkRules.Result.NOT_HOLDER, CaseworkRules.examine(false, true, near, 0L, 10L, false));
        assertEquals(CaseworkRules.Result.TOO_FAR, CaseworkRules.examine(true, false, near, 0L, 10L, false));
        assertEquals(CaseworkRules.Result.TOO_FAR, CaseworkRules.examine(true, true,
                (CaseworkRules.EXAMINE_RADIUS + 1) * (CaseworkRules.EXAMINE_RADIUS + 1), 0L, 10L, false));
        assertEquals(CaseworkRules.Result.STALE_SCENE,
                CaseworkRules.examine(true, true, near, 0L, EvidenceRules.SCENE_FRESH_TICKS + 1, false));
        assertEquals(CaseworkRules.Result.ALREADY_EXAMINED, CaseworkRules.examine(true, true, near, 0L, 10L, true));
        assertTrue(CaseworkRules.examine(true, true, near, 0L, 10L, false).ok());
    }

    @Test
    void onlyARecordedWitnessIsInterviewedAndOnlyOnce() {
        assertEquals(CaseworkRules.Result.NOT_A_WITNESS, CaseworkRules.interview(true, false, true, 1.0, false));
        assertEquals(CaseworkRules.Result.ALREADY_GIVEN, CaseworkRules.interview(true, true, true, 1.0, true));
        assertEquals(CaseworkRules.Result.TOO_FAR, CaseworkRules.interview(true, true, true, 100.0, false));
        assertTrue(CaseworkRules.interview(true, true, true, 1.0, false).ok());
    }

    @Test
    void aPlayerTestifiesOnlyToWhatTheServerSawThemSee() {
        UUID witness = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        List<UUID> recorded = List.of(witness);
        assertTrue(CaseworkRules.testify(witness, SUSPECT, recorded, false, true).ok());
        assertEquals(CaseworkRules.Result.NOT_A_WITNESS,
                CaseworkRules.testify(bystander, SUSPECT, recorded, false, true),
                "a player who was not there cannot accuse anyone");
        assertEquals(CaseworkRules.Result.ALREADY_GIVEN, CaseworkRules.testify(witness, SUSPECT, recorded, true, true));
        assertEquals(CaseworkRules.Result.OWN_CASE,
                CaseworkRules.testify(SUSPECT, SUSPECT, List.of(SUSPECT), false, true));
        assertEquals(CaseworkRules.Result.NOT_CREDIBLE, CaseworkRules.testify(witness, SUSPECT, recorded, false, false));
    }

    @Test
    void anApprehensionNeedsAnAurorCaseAndADownedOrSurrenderingSuspect() {
        double near = 9.0;
        assertEquals(CaseworkRules.Result.NOT_HOLDER,
                CaseworkRules.apprehend(false, CaseStage.AURORS_ASSIGNED, true, true, near, true, false));
        for (CaseStage stage : List.of(CaseStage.INVESTIGATING, CaseStage.SUMMONED)) {
            assertEquals(CaseworkRules.Result.WRONG_STAGE,
                    CaseworkRules.apprehend(true, stage, true, true, near, true, false),
                    "a wizard who still has a summons to answer cannot be arrested: " + stage);
        }
        assertEquals(CaseworkRules.Result.NOT_DOWNED,
                CaseworkRules.apprehend(true, CaseStage.AURORS_ASSIGNED, true, true, near, false, false));
        assertEquals(CaseworkRules.Result.TOO_FAR, CaseworkRules.apprehend(true, CaseStage.AURORS_ASSIGNED, true, true,
                100.0, true, false));
        assertEquals(CaseworkRules.Result.SUSPECT_GONE,
                CaseworkRules.apprehend(true, CaseStage.AURORS_ASSIGNED, false, true, near, true, false));
        assertTrue(CaseworkRules.apprehend(true, CaseStage.AURORS_ASSIGNED, true, true, near, true, false).ok());
        assertTrue(CaseworkRules.apprehend(true, CaseStage.AURORS_ASSIGNED, true, true, near, false, true).ok());
    }

    @Test
    void downedMeansBadlyHurtOrStunned() {
        assertTrue(CaseworkRules.downed(5.0f, 20.0f, false));
        assertFalse(CaseworkRules.downed(10.0f, 20.0f, false));
        assertTrue(CaseworkRules.downed(20.0f, 20.0f, true));
    }
}
