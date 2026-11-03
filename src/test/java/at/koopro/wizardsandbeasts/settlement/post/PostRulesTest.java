package at.koopro.wizardsandbeasts.settlement.post;

import at.koopro.wizardsandbeasts.settlement.post.PostRules.Act;
import at.koopro.wizardsandbeasts.settlement.post.PostRules.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostRulesTest {

    private static final double NEAR = 4.0;
    private static final double FAR = (PostRules.STRAY_BLOCKS + 1.0) * (PostRules.STRAY_BLOCKS + 1.0);
    private static final long DAY = 24000;
    /** The vanilla day: one day tick a game tick. */
    private static final float VANILLA = -1f;

    @Test
    void anEmptyPostFillsItself() {
        assertEquals(Act.SPAWN, PostRules.decide(Status.VACANT, false, 0, false, 0, false));
    }

    @Test
    void someoneAtHomeIsLeftAlone() {
        assertEquals(Act.NOTHING, PostRules.decide(Status.PRESENT, true, NEAR, false, 0, false));
    }

    @Test
    void aStrayIsBroughtBackOnlyWhenNobodyIsWatching() {
        assertEquals(Act.RETURN, PostRules.decide(Status.PRESENT, true, FAR, false, 0, false));
        assertEquals(Act.NOTHING, PostRules.decide(Status.PRESENT, true, FAR, true, 0, false));
    }

    @Test
    void notFindingSomeoneIsNotLosingThem() {
        for (int looks = 1; looks < PostRules.LOST_AFTER_LOOKS; looks++) {
            assertEquals(Act.NOTHING, PostRules.decide(Status.PRESENT, false, 0, false, looks, false), "look " + looks);
        }
        assertEquals(Act.SPAWN, PostRules.decide(Status.PRESENT, false, 0, false, PostRules.LOST_AFTER_LOOKS, false));
    }

    @Test
    void someoneAwayReturnsWhenDueAndNotBefore() {
        assertEquals(Act.NOTHING, PostRules.decide(Status.AWAY, false, 0, false, 99, false));
        assertEquals(Act.SPAWN, PostRules.decide(Status.AWAY, false, 0, false, 0, true));
    }

    @Test
    void theirDawnIsTheNextOne() {
        // Dawn is day time 0: at noon (6000) there are 18000 ticks to go, on any day.
        assertEquals(DAY, PostRules.dawn(6000));
        assertEquals(4 * DAY, PostRules.dawn(3 * DAY + 6000));
        assertEquals(100 + 18000, PostRules.backAt(100, 6000, VANILLA));
    }

    @Test
    void aBlowJustBeforeDawnStillKeepsThemAwayAWhile() {
        assertEquals(23999 + PostRules.MIN_AWAY_TICKS, PostRules.dawn(23999));
        assertEquals(100 + PostRules.MIN_AWAY_TICKS, PostRules.backAt(100, 23999, VANILLA));
    }

    @Test
    void timeAwayNeverPassesOneDay() {
        for (long dayTime = 0; dayTime < DAY; dayTime += 500) {
            long away = PostRules.dawn(dayTime) - dayTime;
            assertTrue(away >= PostRules.MIN_AWAY_TICKS && away <= DAY, "at " + dayTime);
        }
    }

    /** The defect of 2026-11-03: a skipped night moved day time to dawn, and the record waited on game time for a day. */
    @Test
    void aNightSleptThroughBringsThemBackAtTheDawnSleptTo() {
        long game = 500_000;
        long dusk = 5 * DAY + 13_000;
        long backAt = PostRules.backAt(game, dusk, VANILLA);
        long dawn = PostRules.dawn(dusk);
        assertFalse(PostRules.due(game + 100, dusk + 100, backAt, dawn), "back before anyone slept");
        // What ServerLevel does when everyone is asleep: day time to the next multiple of a day, game time untouched.
        long woken = (dusk + 200 + DAY) - (dusk + 200 + DAY) % DAY;
        assertEquals(dawn, woken);
        assertTrue(PostRules.due(game + 200, woken, backAt, dawn), "not back at the dawn they slept to");
    }

    @Test
    void ordinaryTimeBringsThemBackAtDawnAndNotATickBefore() {
        long game = 77;
        long dayTime = 2 * DAY + 20_000;
        long backAt = PostRules.backAt(game, dayTime, VANILLA);
        long dawn = PostRules.dawn(dayTime);
        for (long t = 0; t < dawn - dayTime; t += 250) {
            assertFalse(PostRules.due(game + t, dayTime + t, backAt, dawn), "back early, " + t + " ticks on");
        }
        long left = dawn - dayTime;
        assertFalse(PostRules.due(game + left - 1, dawn - 1, backAt, dawn));
        assertTrue(PostRules.due(game + left, dawn, backAt, dawn));
    }

    @Test
    void timeAddedPastDawnBringsThemBack() {
        long backAt = PostRules.backAt(0, 15_000, VANILLA);
        long dawn = PostRules.dawn(15_000);
        assertTrue(PostRules.due(10, 15_000 + 12_000, backAt, dawn));
    }

    @Test
    void aClockTurnedBackDoesNotKeepThemAwayPastTheirGameTime() {
        long backAt = PostRules.backAt(1000, 18_000, VANILLA);
        long dawn = PostRules.dawn(18_000);
        // /time set 0: a whole day earlier than the blow. Day time alone would keep them away another day.
        long turnedBack = 0;
        assertFalse(PostRules.due(1000 + 5000, turnedBack + 5000, backAt, dawn));
        assertTrue(PostRules.due(backAt, turnedBack + (backAt - 1000), backAt, dawn));
    }

    @Test
    void aStoppedClockStillLetsThemReturnByGameTime() {
        long backAt = PostRules.backAt(0, 18_000, VANILLA);
        long dawn = PostRules.dawn(18_000);
        assertFalse(PostRules.due(backAt - 1, 18_000, backAt, dawn));
        assertTrue(PostRules.due(backAt, 18_000, backAt, dawn));
    }

    @Test
    void aSlowerDayMovesTheBackstopWithIt() {
        // /neoforge day at half speed: dawn is twice as many game ticks away, and the backstop must not come first.
        long backAt = PostRules.backAt(0, 18_000, 0.5f);
        long dawn = PostRules.dawn(18_000);
        assertEquals(12_000, backAt);
        for (long t = 0; t < 12_000; t += 100) {
            assertFalse(PostRules.due(t, 18_000 + t / 2, backAt, dawn), "back at night, " + t + " game ticks on");
        }
        assertTrue(PostRules.due(12_000, 24_000, backAt, dawn));
    }

    @Test
    void aFasterDayBringsThemBackAtItsDawn() {
        long backAt = PostRules.backAt(0, 18_000, 20f);
        long dawn = PostRules.dawn(18_000);
        assertFalse(PostRules.due(299, 18_000 + 299 * 20, backAt, dawn));
        assertTrue(PostRules.due(300, 18_000 + 300 * 20, backAt, dawn));
    }

    @Test
    void aRecordFromBeforeDawnsWaitsOnGameTimeAsWritten() {
        assertFalse(PostRules.due(999, 10 * DAY, 1000, PostRules.NO_DAWN));
        assertTrue(PostRules.due(1000, 0, 1000, PostRules.NO_DAWN));
    }
}
