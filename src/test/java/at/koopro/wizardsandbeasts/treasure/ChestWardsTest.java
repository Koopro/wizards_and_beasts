package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.treasure.ChestWards.Step;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChestWardsTest {

    @Test
    void revelioReadsOnce() {
        assertEquals(Step.READ, ChestWards.judge(false, CurseKind.JINX, "wizards_and_beasts:revelio"));
        assertEquals(Step.READ, ChestWards.judge(false, CurseKind.JINX, "specialis_revelio"));
        assertEquals(Step.NONE, ChestWards.judge(true, CurseKind.JINX, "revelio"));
    }

    @Test
    void onlyARightCounterOnAReadWardDisarms() {
        assertEquals(Step.DISARMED, ChestWards.judge(true, CurseKind.FLAGRANTE, "glacius"));
        assertEquals(Step.FIRED, ChestWards.judge(false, CurseKind.FLAGRANTE, "glacius"));
        assertEquals(Step.FIRED, ChestWards.judge(true, CurseKind.FLAGRANTE, "alohomora"));
    }

    @Test
    void anyOtherSpellIsNoAttempt() {
        assertEquals(Step.NONE, ChestWards.judge(false, CurseKind.SEALING, "lumos"));
        assertEquals(Step.NONE, ChestWards.judge(true, CurseKind.SEALING, "stupefy"));
    }
}
