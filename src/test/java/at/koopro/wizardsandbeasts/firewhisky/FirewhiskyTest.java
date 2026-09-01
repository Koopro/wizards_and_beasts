package at.koopro.wizardsandbeasts.firewhisky;

import at.koopro.wizardsandbeasts.spell.cast.SpellCastGate;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The escalation, which is the whole item.
 *
 * <p>Nobody wants to discover by drinking three in a playtest that the third beat never fires, or
 * that the second one fires on the first drink of the evening. Both are one-character mistakes in
 * {@link Firewhisky#classify} and neither would throw.
 */
class FirewhiskyTest {

    private static Deque<Long> history(long... times) {
        Deque<Long> deque = new ArrayDeque<>();
        for (long time : times) {
            deque.addLast(time);
        }
        return deque;
    }

    @Test
    void theBriefsWindowsAreWhatShipped() {
        assertEquals(12, Firewhisky.COURAGE_TICKS / 20);
        assertEquals(3, Firewhisky.BURN_TICKS / 20);
        assertEquals(60, Firewhisky.SECOND_WINDOW_TICKS / 20);
        assertEquals(120, Firewhisky.THIRD_WINDOW_TICKS / 20);
        assertEquals(15, Firewhisky.DRUNK_TICKS / 20);
    }

    @Test
    void theFirstDrinkOfTheEveningIsJustADrink() {
        assertEquals(Firewhisky.Round.FIRST,
                Firewhisky.classify(Firewhisky.timesSince(history(), 10_000L)));
    }

    @Test
    void aSecondInsideAMinuteIsOneTooMany() {
        long now = 10_000L;
        Deque<Long> one = history(now - 600L);   // 30s ago
        assertEquals(Firewhisky.Round.SECOND, Firewhisky.classify(Firewhisky.timesSince(one, now)));
    }

    @Test
    void aThirdInsideTwoMinutesPutsYouOnTheFloor() {
        long now = 10_000L;
        // Two shots: one 90s ago (outside the minute, inside the two), one 30s ago.
        Deque<Long> two = history(now - 1800L, now - 600L);
        assertEquals(Firewhisky.Round.THIRD, Firewhisky.classify(Firewhisky.timesSince(two, now)));
    }

    @Test
    void waitingOutTheMinuteDropsYouBackToAPlainDrink() {
        long now = 10_000L;
        Deque<Long> stale = history(now - 1300L);   // 65s ago: outside the second window
        assertEquals(Firewhisky.Round.FIRST, Firewhisky.classify(Firewhisky.timesSince(stale, now)),
                "a patient drinker should get a clean shot again");
    }

    @Test
    void twoOldShotsOutsideBothWindowsAreForgotten() {
        long now = 10_000L;
        Deque<Long> ancient = history(now - 5000L, now - 4000L);
        assertEquals(Firewhisky.Round.FIRST, Firewhisky.classify(Firewhisky.timesSince(ancient, now)));
    }

    @Test
    void theWindowBoundariesAreExclusiveOnTheFarSide() {
        long now = 10_000L;
        // Exactly on the boundary is outside: 60s ago no longer counts toward the second beat.
        Deque<Long> exactly = history(now - Firewhisky.SECOND_WINDOW_TICKS);
        assertEquals(Firewhisky.Round.FIRST, Firewhisky.classify(Firewhisky.timesSince(exactly, now)));

        Deque<Long> justInside = history(now - Firewhisky.SECOND_WINDOW_TICKS + 1);
        assertEquals(Firewhisky.Round.SECOND, Firewhisky.classify(Firewhisky.timesSince(justInside, now)));
    }

    @Test
    void aClockSetBackwardsDoesNotCauseAPermanentHangover() {
        // /time set can move game time behind a recorded shot. That must read as ancient, not as
        // a shot that will be "recent" for the rest of the world's life.
        long now = 1_000L;
        Deque<Long> future = history(now + 50_000L);
        assertEquals(Firewhisky.Round.FIRST, Firewhisky.classify(Firewhisky.timesSince(future, now)));
    }

    @Test
    void thirdWindowIsWiderThanSecondOrTheBeatsCouldNeverOrder() {
        assertTrue(Firewhisky.THIRD_WINDOW_TICKS > Firewhisky.SECOND_WINDOW_TICKS,
                "the third beat's window has to contain the second's");
    }

    @Test
    void everyShotCostsSomethingEvenTheFirst() {
        // "Never pure Strength": the burn is unconditional, so there is no round that is free.
        assertTrue(Firewhisky.BURN_TICKS > 0, "the burn is what stops this being a Strength potion");
        assertTrue(Firewhisky.COURAGE_TICKS > Firewhisky.BURN_TICKS,
                "the courage should outlast the burn, or nobody would ever drink it");
    }

    @Test
    void beingDrunkBlocksCastingAfterEveryGateThatDescribesTheSpell() {
        SpellCastGate.Inputs drunk = new SpellCastGate.Inputs(
                true, true, true, true, false, true, false, false, true, false, false);
        assertEquals(SpellCastGate.TOO_DRUNK, SpellCastGate.evaluate(drunk));
    }

    @Test
    void notKnowingTheSpellOutranksBeingDrunk() {
        // Telling a drunk wizard "you have not learned that" is the more useful of the two truths.
        SpellCastGate.Inputs drunkAndIgnorant = new SpellCastGate.Inputs(
                true, true, true, false, false, true, false, false, true, false, false);
        assertEquals(SpellCastGate.SPELL_NOT_KNOWN, SpellCastGate.evaluate(drunkAndIgnorant));
    }

    @Test
    void beingDrunkOutranksACooldownTheCasterCannotReach() {
        SpellCastGate.Inputs drunkAndCooling = new SpellCastGate.Inputs(
                true, true, true, true, false, true, false, false, true, true, true);
        assertEquals(SpellCastGate.TOO_DRUNK, SpellCastGate.evaluate(drunkAndCooling));
    }

    @Test
    void aSoberWizardIsUnaffectedByTheNewGate() {
        SpellCastGate.Inputs sober = new SpellCastGate.Inputs(
                true, true, true, true, false, true, false, false, false, false, false);
        assertNull(SpellCastGate.evaluate(sober), "the gate must not fire for anyone who has not drunk");
        assertNotEquals(SpellCastGate.TOO_DRUNK, SpellCastGate.evaluate(sober));
    }
}
