package at.koopro.wizardsandbeasts.duel;

import at.koopro.wizardsandbeasts.duel.FormalDuelRules.End;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormalDuelRulesTest {

    @Test
    void theCountIsThreeTwoOneThenTheDuel() {
        assertEquals(3, FormalDuelRules.count(0));
        assertEquals(0, FormalDuelRules.count(10));
        assertEquals(2, FormalDuelRules.count(20));
        assertEquals(1, FormalDuelRules.count(40));
        assertEquals(0, FormalDuelRules.count(60));
        assertFalse(FormalDuelRules.started(59));
        assertTrue(FormalDuelRules.started(60));
    }

    @Test
    void aDuellistDownBeforeTheCountWinsNothing() {
        assertEquals(End.NONE, FormalDuelRules.check(30, true, true, 25, true, false));
        assertEquals(End.WON, FormalDuelRules.check(80, true, true, 25, false, true));
    }

    @Test
    void walkingAwayDeathAndTimeEndIt() {
        assertEquals(End.ABANDONED, FormalDuelRules.check(80, true, true, 33 * 33, false, false));
        assertEquals(End.ABANDONED, FormalDuelRules.check(80, true, false, 0, false, false));
        assertEquals(End.DEATH, FormalDuelRules.check(80, false, true, 0, false, false));
        assertEquals(End.TIME, FormalDuelRules.check(FormalDuelRules.MAX_TICKS, true, true, 0, false, false));
    }

    @Test
    void aBlowNeverTakesTheLastOfTheHealth() {
        assertEquals(5f, FormalDuelRules.cap(5f, 20f));
        assertEquals(3f, FormalDuelRules.cap(10f, 4f));
        assertEquals(0f, FormalDuelRules.cap(10f, 1f));
    }
}
