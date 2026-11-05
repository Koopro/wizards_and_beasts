package at.koopro.wizardsandbeasts.entity.flight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The takeoff beat plays when a winged walker takes to the air, never on a hop up a block (Beast Renaissance D13).
 */
class FlightBeatsTest {

    @Test
    void itsOwnFlightIsATakeoff() {
        assertTrue(FlightBeats.takesOff(true, false, false, false, 1, 0.6, false));
    }

    @Test
    void aStepUpJumpIsNot() {
        // Leaving the ground with a jump's lift, unridden and not flying.
        for (int air = 1; air <= 10; air++) {
            assertFalse(FlightBeats.takesOff(false, false, false, false, air, 0.42, false), "hop, air tick " + air);
        }
    }

    @Test
    void aRiddenClimbPastAJumpIs() {
        assertFalse(FlightBeats.takesOff(false, false, true, false, 3, 0.4, false), "too early to tell from a jump");
        assertTrue(FlightBeats.takesOff(false, false, true, false, FlightBeats.RIDDEN_TAKEOFF_TICKS, 0.2, false));
        assertFalse(FlightBeats.takesOff(false, false, true, false, FlightBeats.RIDDEN_TAKEOFF_TICKS, -0.1, false),
                "falling after a jump is not a takeoff");
    }

    @Test
    void onceUpItDoesNotTakeOffAgain() {
        assertFalse(FlightBeats.takesOff(true, false, true, false, FlightBeats.RIDDEN_TAKEOFF_TICKS, 0.5, true));
    }
}
