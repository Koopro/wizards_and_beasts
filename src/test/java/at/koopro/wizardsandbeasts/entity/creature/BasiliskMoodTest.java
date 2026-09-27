package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.entity.creature.BasiliskEntity.Mood;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** When it coils, rears and settles. */
class BasiliskMoodTest {

    @Test
    void aTargetInSightButOutOfReachIsThreatened() {
        assertEquals(Mood.THREATEN, BasiliskEntity.moodFor(Mood.CALM, true, 10, true, 0));
        assertEquals(Mood.CALM, BasiliskEntity.moodFor(Mood.THREATEN, true, 3, true, 0), "close enough to strike");
        assertEquals(Mood.CALM, BasiliskEntity.moodFor(Mood.CALM, true, 10, false, 0), "cannot see it");
    }

    @Test
    void itCoilsAfterLyingStillAndUncoilsWhenItMoves() {
        assertEquals(Mood.CALM, BasiliskEntity.moodFor(Mood.CALM, false, 0, false, BasiliskEntity.COIL_AFTER - 10));
        assertEquals(Mood.COILED, BasiliskEntity.moodFor(Mood.CALM, false, 0, false, BasiliskEntity.COIL_AFTER));
        assertEquals(Mood.COILED, BasiliskEntity.moodFor(Mood.COILED, false, 0, false, 10), "stays coiled while still");
        assertEquals(Mood.CALM, BasiliskEntity.moodFor(Mood.COILED, false, 0, false, 0), "moving uncoils it");
        assertEquals(Mood.THREATEN, BasiliskEntity.moodFor(Mood.COILED, true, 12, true, 400), "a target rouses it");
    }
}
