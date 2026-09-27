package at.koopro.wizardsandbeasts.basilisk;

import at.koopro.wizardsandbeasts.basilisk.BasiliskGaze.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The eyes' rule, without a world: direct sight kills, indirect petrifies, anything else is nothing. */
class BasiliskGazeTest {

    private static final double FACING = 1.0;
    private static final double MEET = 1.0;

    @Test
    void meetingItsEyesDirectlyKills() {
        assertEquals(Outcome.DEATH, BasiliskGaze.outcome(false, true, FACING, MEET, false));
    }

    @Test
    void meetingThemIndirectlyPetrifies() {
        assertEquals(Outcome.PETRIFY, BasiliskGaze.outcome(false, true, FACING, MEET, true));
    }

    @Test
    void closedEyesWallsAndAvertedEyesAreSafe() {
        assertEquals(Outcome.NONE, BasiliskGaze.outcome(true, true, FACING, MEET, false), "blindfold or Protego");
        assertEquals(Outcome.NONE, BasiliskGaze.outcome(false, false, FACING, MEET, false), "wall between");
        assertEquals(Outcome.NONE, BasiliskGaze.outcome(false, true, FACING, BasiliskGaze.MEET - 0.01, false), "looked away");
        assertEquals(Outcome.NONE, BasiliskGaze.outcome(false, true, FACING, BasiliskGaze.MEET - 0.01, true),
                "looked away from the reflection too");
    }

    @Test
    void itMustBeLookingAtYou() {
        assertEquals(Outcome.NONE, BasiliskGaze.outcome(false, true, BasiliskGaze.FACING - 0.01, MEET, false));
        assertEquals(Outcome.DEATH, BasiliskGaze.outcome(false, true, BasiliskGaze.FACING, MEET, false));
    }
}
