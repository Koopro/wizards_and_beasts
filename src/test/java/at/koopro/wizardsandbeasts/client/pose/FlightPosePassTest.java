package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The flight pass's state machine, driven without a client. */
class FlightPosePassTest {

    private static FlightPosePass flying(FlightPoseState state, int ticks) {
        FlightPosePass pass = new FlightPosePass();
        for (int i = 0; i < ticks; i++) {
            pass.tick(PoseOverride.forced(state), true, false);
        }
        return pass;
    }

    /**
     * The regression this seam exists for.
     *
     * <p>Landing used to drop the pose on the tick the player touched the ground: the override went
     * absent, the pass cleared its state, and {@code pose} returned early on the null — so the eight
     * tick fade wound down with nobody reading it. The state has to outlive the deactivation.
     */
    @Test
    void landingEasesOutInsteadOfSnapping() {
        FlightPosePass pass = flying(FlightPoseState.GLIDE, 20);
        assertTrue(pass.posing());

        pass.tick(PoseOverride.NONE, false, false);
        assertTrue(pass.posing(), "the pose must survive the tick the override went away");
        assertEquals(FlightPoseState.GLIDE, pass.currentState(),
                "the state is still needed — it is what the fade is fading out of");

        for (int i = 0; i < FlightPoseConstants.FADE_TICKS + 2; i++) {
            pass.tick(PoseOverride.NONE, false, false);
        }
        assertFalse(pass.posing(), "the fade has run its course");
        assertNull(pass.currentState());
    }

    /** Taking off after landing must start clean rather than resume a half-faded old state. */
    @Test
    void takingOffAgainAfterAFullFadeStartsFresh() {
        FlightPosePass pass = flying(FlightPoseState.PROPELLED, 20);
        for (int i = 0; i < FlightPoseConstants.FADE_TICKS + 2; i++) {
            pass.tick(PoseOverride.NONE, false, false);
        }
        assertNull(pass.currentState());

        pass.tick(PoseOverride.forced(FlightPoseState.HOVER), true, false);
        assertEquals(FlightPoseState.HOVER, pass.currentState());
    }

    /**
     * A grounded player with a stored override is not posed.
     *
     * <p>The command accepts one deliberately — schema §8 — so this is the check that "stored" and
     * "rendered" stay different things.
     */
    @Test
    void aStoredOverrideDoesNothingUntilThePlayerLeavesTheGround() {
        FlightPosePass pass = new FlightPosePass();
        for (int i = 0; i < 20; i++) {
            pass.tick(PoseOverride.forced(FlightPoseState.GLIDE), false, false);
        }
        assertFalse(pass.posing(), "stored, not rendered");

        for (int i = 0; i < 20; i++) {
            pass.tick(PoseOverride.forced(FlightPoseState.GLIDE), true, false);
        }
        assertTrue(pass.posing(), "and it takes effect on take-off");
    }

    /** Switching state mid-flight keeps the pass posing throughout — no gap between the two. */
    @Test
    void switchingStateInFlightNeverStopsPosing() {
        FlightPosePass pass = flying(FlightPoseState.HOVER, 20);
        for (int i = 0; i < FlightPoseConstants.STATE_BLEND_TICKS + 2; i++) {
            pass.tick(PoseOverride.forced(FlightPoseState.PROPELLED), true, false);
            assertTrue(pass.posing(), "the cross-fade must not blink");
        }
        assertEquals(FlightPoseState.PROPELLED, pass.currentState());
    }

    /** Priority has to stay inside the locomotion band or registration rejects the pass. */
    @Test
    void sitsInTheLocomotionBand() {
        assertDoesNotThrow(() -> PoseBand.require(FlightPosePass.PRIORITY, "flight"));
    }
}
