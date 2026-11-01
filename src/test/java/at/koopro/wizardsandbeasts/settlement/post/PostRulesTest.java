package at.koopro.wizardsandbeasts.settlement.post;

import at.koopro.wizardsandbeasts.settlement.post.PostRules.Act;
import at.koopro.wizardsandbeasts.settlement.post.PostRules.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostRulesTest {

    private static final double NEAR = 4.0;
    private static final double FAR = (PostRules.STRAY_BLOCKS + 1.0) * (PostRules.STRAY_BLOCKS + 1.0);

    @Test
    void anEmptyPostFillsItself() {
        assertEquals(Act.SPAWN, PostRules.decide(Status.VACANT, false, 0, false, 0, 100, 0));
    }

    @Test
    void someoneAtHomeIsLeftAlone() {
        assertEquals(Act.NOTHING, PostRules.decide(Status.PRESENT, true, NEAR, false, 0, 100, 0));
    }

    @Test
    void aStrayIsBroughtBackOnlyWhenNobodyIsWatching() {
        assertEquals(Act.RETURN, PostRules.decide(Status.PRESENT, true, FAR, false, 0, 100, 0));
        assertEquals(Act.NOTHING, PostRules.decide(Status.PRESENT, true, FAR, true, 0, 100, 0));
    }

    @Test
    void notFindingSomeoneIsNotLosingThem() {
        for (int looks = 1; looks < PostRules.LOST_AFTER_LOOKS; looks++) {
            assertEquals(Act.NOTHING, PostRules.decide(Status.PRESENT, false, 0, false, looks, 100, 0), "look " + looks);
        }
        assertEquals(Act.SPAWN, PostRules.decide(Status.PRESENT, false, 0, false, PostRules.LOST_AFTER_LOOKS, 100, 0));
    }

    @Test
    void someoneAwayReturnsAtTheirTimeAndNotBefore() {
        assertEquals(Act.NOTHING, PostRules.decide(Status.AWAY, false, 0, false, 99, 999, 1000));
        assertEquals(Act.SPAWN, PostRules.decide(Status.AWAY, false, 0, false, 0, 1000, 1000));
    }

    @Test
    void theyAreBackAtTheNextDawn() {
        // Dawn is day time 0: at noon (6000) there are 18000 ticks to go.
        assertEquals(100 + 18000, PostRules.backAt(100, 6000));
        assertEquals(100 + 18000, PostRules.backAt(100, 24000 * 3 + 6000));
    }

    @Test
    void aBlowJustBeforeDawnStillKeepsThemAwayAWhile() {
        assertEquals(100 + PostRules.MIN_AWAY_TICKS, PostRules.backAt(100, 23999));
    }

    @Test
    void timeAwayNeverPassesOneDay() {
        for (long dayTime = 0; dayTime < 24000; dayTime += 500) {
            long away = PostRules.backAt(0, dayTime);
            assertTrue(away >= PostRules.MIN_AWAY_TICKS && away <= 24000, "at " + dayTime);
        }
    }
}
