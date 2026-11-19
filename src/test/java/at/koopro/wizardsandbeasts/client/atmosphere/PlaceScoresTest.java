package at.koopro.wizardsandbeasts.client.atmosphere;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceScoresTest {

    private static int ticksUntil(PlaceScores s, double frame, Predicate<PlaceScores> done) {
        for (int i = 1; i <= 2000; i++) {
            s.observe(frame);
            if (done.test(s)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void aRoomIsRecognisedWithinSecondsAndReachesFullStrength() {
        PlaceScores s = new PlaceScores(0.04, 0.02, 0.10);
        int on = ticksUntil(s, 0.15, PlaceScores::active);
        assertTrue(on > 0 && on <= 60, "recognised after " + on + " ticks");
        ticksUntil(s, 0.15, x -> x.strength() >= 1f);
        assertEquals(1f, s.strength(), 1e-6);
    }

    @Test
    void strengthEasesInRatherThanPopping() {
        PlaceScores s = new PlaceScores(0.04, 0.02, 0.10);
        ticksUntil(s, 0.5, PlaceScores::active);
        float before = s.strength();
        s.observe(0.5);
        assertTrue(s.strength() - before <= PlaceScores.EASE + 1e-6);
    }

    @Test
    void aDoorwayDoesNotFlicker() {
        PlaceScores s = new PlaceScores(0.04, 0.02, 0.10);
        ticksUntil(s, 0.15, PlaceScores::active);
        // Hovering between leave and enter keeps it on.
        for (int i = 0; i < 400; i++) {
            s.observe(0.03);
        }
        assertTrue(s.active());
        // Stepping out turns it off, but not on the first tick, and strength fades to nothing.
        s.observe(0.0);
        assertTrue(s.active());
        int off = ticksUntil(s, 0.0, x -> !x.active());
        assertTrue(off > 0);
        ticksUntil(s, 0.0, x -> x.strength() == 0f);
        assertEquals(0f, s.strength());
    }

    @Test
    void noSwitchSoonerThanTheDwell() {
        PlaceScores s = new PlaceScores(0.04, 0.02, 0.10);
        ticksUntil(s, 1.0, PlaceScores::active);
        int off = ticksUntil(s, 0.0, x -> !x.active());
        assertTrue(off >= PlaceScores.MIN_DWELL - 1, "switched off after " + off);
    }

    @Test
    void neverOnBelowEnter() {
        PlaceScores s = new PlaceScores(0.04, 0.02, 0.10);
        for (int i = 0; i < 2000; i++) {
            s.observe(0.039);
        }
        assertFalse(s.active());
        assertEquals(0f, s.strength());
    }

    @Test
    void resetForgetsAtOnce() {
        PlaceScores s = new PlaceScores(0.04, 0.02, 0.10);
        ticksUntil(s, 0.5, x -> x.strength() >= 1f);
        s.reset();
        assertFalse(s.active());
        assertEquals(0f, s.strength());
        assertEquals(0.0, s.score());
    }

    @Test
    void badThresholdsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceScores(0.02, 0.04, 0.10));
        assertThrows(IllegalArgumentException.class, () -> new PlaceScores(0.04, 0.02, 0.03));
    }
}
