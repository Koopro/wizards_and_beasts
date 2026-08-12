package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shape checks on the pose table.
 *
 * <p>Not assertions about the numbers — those are placeholders and will all change. These pin the
 * invariants the pass relies on, which must survive whoever replaces them with authored values.
 */
class FlightPoseConstantsTest {

    @Test
    void everyStateHasBothAThirdAndAFirstPersonPose() {
        for (FlightPoseState state : FlightPoseState.values()) {
            assertNotNull(FlightPoseConstants.of(state, false), state + " has no third-person pose");
            assertNotNull(FlightPoseConstants.of(state, true), state + " has no first-person pose");
        }
    }

    /**
     * Forward is negative, matching vanilla's elytra pitch, and nothing goes past prone.
     *
     * <p>A positive value here would lean the player backwards into the direction they came from,
     * which is the kind of thing that looks like a physics bug rather than a wrong constant.
     */
    @Test
    void bodyPitchLeansForwardAndNeverPastProne() {
        for (FlightPoseState state : FlightPoseState.values()) {
            float pitch = FlightPoseConstants.of(state, false).bodyPitch();
            assertTrue(pitch <= 0f, state + " leans backwards: " + pitch);
            assertTrue(pitch >= -90f, state + " pitches past prone: " + pitch);
        }
    }

    /** A fraction, because it multiplies the applied pitch. Outside 0..1 it stops being a counter. */
    @Test
    void headCounterIsAFraction() {
        for (FlightPoseState state : FlightPoseState.values()) {
            float counter = FlightPoseConstants.of(state, false).headCounter();
            assertTrue(counter >= 0f && counter <= 1f, state + " headCounter out of range: " + counter);
        }
    }

    /**
     * The pivot is in blocks, so a plausible value is under the player's own height.
     *
     * <p>The unit is the trap: the limb fields are in the sixteenths a ModelPart uses, and someone
     * writing this one to match them would send the pitch pivot thirty-odd blocks into the sky.
     */
    @Test
    void pitchPivotIsInBlocksNotModelUnits() {
        for (FlightPoseState state : FlightPoseState.values()) {
            float pivot = FlightPoseConstants.of(state, false).pitchPivot();
            assertTrue(pivot >= 0f && pivot <= 1.8f,
                    state + " pitchPivot looks like model units, not blocks: " + pivot);
        }
    }

    /** First person draws an arm and nothing else, so the body fields must be inert there. */
    @Test
    void firstPersonCarriesNoBodyTransform() {
        for (FlightPoseState state : FlightPoseState.values()) {
            var pose = FlightPoseConstants.of(state, true);
            assertEquals(0f, pose.bodyPitch(), 1e-6f, state + " pitches the body in first person");
            assertEquals(0f, pose.pitchPivot(), 1e-6f);
            assertEquals(0f, pose.legPitch(), 1e-6f, state + " poses legs that are not on screen");
        }
    }

    /** Every field must blend, or adding one silently freezes it at the from-state during a change. */
    @Test
    void lerpCoversEveryField() {
        var from = new FlightPoseConstants.FlightPose(-10f, 0.5f, 0.2f, -20f, 3f, 5f, 1f);
        var to = new FlightPoseConstants.FlightPose(-50f, 1.5f, 0.8f, -80f, 9f, 25f, 7f);
        var mid = FlightPoseConstants.lerp(from, to, 0.5f);

        assertEquals(-30f, mid.bodyPitch(), 1e-5f);
        assertEquals(1.0f, mid.pitchPivot(), 1e-5f);
        assertEquals(0.5f, mid.headCounter(), 1e-5f);
        assertEquals(-50f, mid.armPitch(), 1e-5f);
        assertEquals(6f, mid.armSplay(), 1e-5f);
        assertEquals(15f, mid.legPitch(), 1e-5f);
        assertEquals(4f, mid.legSplay(), 1e-5f);

        assertEquals(from, FlightPoseConstants.lerp(from, to, 0f));
        assertEquals(to, FlightPoseConstants.lerp(from, to, 1f));
    }

    /** The three states must be visibly different or the whole state machine is unobservable. */
    @Test
    void theThreeStatesAreDistinguishable() {
        float hover = FlightPoseConstants.of(FlightPoseState.HOVER, false).bodyPitch();
        float glide = FlightPoseConstants.of(FlightPoseState.GLIDE, false).bodyPitch();
        float propelled = FlightPoseConstants.of(FlightPoseState.PROPELLED, false).bodyPitch();
        assertTrue(hover > glide && glide > propelled,
                "each state should lean further than the last: " + hover + ", " + glide + ", " + propelled);
    }
}
