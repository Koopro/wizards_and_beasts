package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shape checks on the authored pose table.
 *
 * <p>Not assertions that the numbers are good — that is a judgement made by looking at them in game.
 * These pin the relationships the pass depends on, so a later tuning pass cannot quietly break one
 * while adjusting another.
 */
class FlightPoseConstantsTest {

    @Test
    void everyStateHasBothAThirdAndAFirstPersonPose() {
        for (FlightPoseState state : FlightPoseState.values()) {
            assertNotNull(FlightPoseConstants.thirdPerson(state), state + " has no third-person pose");
            assertNotNull(FlightPoseConstants.firstPerson(state), state + " has no first-person pose");
        }
    }

    /**
     * Forward is negative, matching vanilla's elytra pitch, and nothing goes fully prone.
     *
     * <p>A positive value would lean the player backwards into the direction they came from, which
     * reads as a physics bug rather than a wrong constant.
     */
    @Test
    void bodyPitchLeansForwardAndStopsShortOfProne() {
        for (FlightPoseState state : FlightPoseState.values()) {
            float pitch = FlightPoseConstants.thirdPerson(state).bodyPitch();
            assertTrue(pitch < 0f, state + " leans backwards: " + pitch);
            assertTrue(pitch > -90f, state + " is fully prone, which reads as a rigid plank: " + pitch);
        }
    }

    /**
     * The head compensation must oppose the body's lean, and in Minecraft that means carrying the
     * <em>same</em> sign as it.
     *
     * <p>This is the one value corrected from the source table, so it is the one most likely to be
     * "fixed" back. Vanilla sets {@code head.xRot = state.xRot * DEG_TO_RAD} and entity pitch is
     * positive looking down; a positive head rotation therefore tips the gaze further into the
     * ground, on a body already pitched forward. If this test fails because someone flipped the
     * sign, the in-game symptom is the head buried in the chest.
     */
    @Test
    void headCompensationLiftsTheGazeRatherThanDroppingIt() {
        for (FlightPoseState state : FlightPoseState.values()) {
            var pose = FlightPoseConstants.thirdPerson(state);
            assertTrue(pose.headPitch() < 0f,
                    state + " head pitch must be negative to lift the gaze, was " + pose.headPitch());
            assertTrue(Math.abs(pose.headPitch()) <= Math.abs(pose.bodyPitch()),
                    state + " compensates more than the body leans, which over-rotates the head");
        }
    }

    /**
     * The pivot is in blocks, so a plausible value is under the player's own height.
     *
     * <p>The unit is the trap: every limb field is in the sixteenths a ModelPart uses, and writing
     * this one to match them would send the pitch pivot thirty blocks into the sky.
     */
    @Test
    void pitchPivotIsInBlocksNotModelUnits() {
        for (FlightPoseState state : FlightPoseState.values()) {
            float pivot = FlightPoseConstants.thirdPerson(state).pivot();
            assertTrue(pivot > 0f && pivot <= 1.8f,
                    state + " pivot looks like model units, not blocks: " + pivot);
        }
    }

    /** Arms mirror across the centre line, or the same pose is wrong on one side. */
    @Test
    void armsMirror() {
        for (FlightPoseState state : FlightPoseState.values()) {
            var pose = FlightPoseConstants.thirdPerson(state);
            assertEquals(pose.rightArm().xRot(), pose.leftArm().xRot(), 1e-5f, state + " arm pitch");
            assertEquals(-pose.rightArm().yRot(), pose.leftArm().yRot(), 1e-5f, state + " arm yaw");
            assertEquals(-pose.rightArm().zRot(), pose.leftArm().zRot(), 1e-5f, state + " arm roll");
        }
    }

    /**
     * The legs are deliberately <em>not</em> mirrored.
     *
     * <p>A perfectly symmetrical pose reads as a mannequin. This is pinned because it looks exactly
     * like an authoring slip and would otherwise be "corrected" by the next person through.
     */
    @Test
    void legsAreDeliberatelyAsymmetric() {
        for (FlightPoseState state : FlightPoseState.values()) {
            var pose = FlightPoseConstants.thirdPerson(state);
            assertNotEquals(pose.rightLeg().xRot(), pose.leftLeg().xRot(),
                    state + " has symmetrical legs — the offset is intentional, see FLIGHT_POSE_CONSTANTS §3");
        }
    }

    /** Hover holds station, so it does not bank; the travelling states do. */
    @Test
    void onlyTheTravellingStatesBank() {
        assertFalse(FlightPoseConstants.thirdPerson(FlightPoseState.HOVER).banks());
        assertTrue(FlightPoseConstants.thirdPerson(FlightPoseState.GLIDE).banks());
        assertTrue(FlightPoseConstants.thirdPerson(FlightPoseState.PROPELLED).banks());
    }

    /** Each state leans further than the last, or the state machine is unobservable. */
    @Test
    void theThreeStatesAreDistinguishable() {
        float hover = FlightPoseConstants.thirdPerson(FlightPoseState.HOVER).bodyPitch();
        float glide = FlightPoseConstants.thirdPerson(FlightPoseState.GLIDE).bodyPitch();
        float propelled = FlightPoseConstants.thirdPerson(FlightPoseState.PROPELLED).bodyPitch();
        assertTrue(hover > glide && glide > propelled,
                "each state should lean further: " + hover + ", " + glide + ", " + propelled);
    }

    /** Releasing should feel quicker than committing. */
    @Test
    void theFadeOutIsShorterThanTheFadeIn() {
        assertTrue(FlightPoseConstants.FADE_OUT_TICKS < FlightPoseConstants.FADE_IN_TICKS);
    }

    /** Every field must blend, or adding one silently freezes it at the from-state during a change. */
    @Test
    void lerpCoversEveryField() {
        var from = FlightPoseConstants.thirdPerson(FlightPoseState.HOVER);
        var to = FlightPoseConstants.thirdPerson(FlightPoseState.PROPELLED);
        var mid = FlightPoseConstants.lerp(from, to, 0.5f);

        assertEquals((from.bodyPitch() + to.bodyPitch()) / 2f, mid.bodyPitch(), 1e-4f);
        assertEquals((from.pivot() + to.pivot()) / 2f, mid.pivot(), 1e-4f);
        assertEquals((from.headPitch() + to.headPitch()) / 2f, mid.headPitch(), 1e-4f);
        assertEquals((from.chest().xRot() + to.chest().xRot()) / 2f, mid.chest().xRot(), 1e-4f);
        assertEquals((from.rightArm().zRot() + to.rightArm().zRot()) / 2f, mid.rightArm().zRot(), 1e-4f);
        assertEquals((from.leftArm().yRot() + to.leftArm().yRot()) / 2f, mid.leftArm().yRot(), 1e-4f);
        assertEquals((from.rightLeg().xRot() + to.rightLeg().xRot()) / 2f, mid.rightLeg().xRot(), 1e-4f);
        assertEquals((from.leftLeg().zRot() + to.leftLeg().zRot()) / 2f, mid.leftLeg().zRot(), 1e-4f);

        assertEquals(from.rightArm(), FlightPoseConstants.lerp(from, to, 0f).rightArm());
        assertEquals(to.leftLeg(), FlightPoseConstants.lerp(from, to, 1f).leftLeg());
    }

    /** A boolean has no midpoint, so the target decides and banking starts with the state. */
    @Test
    void blendingTakesTheTargetStatesBankFlag() {
        var hover = FlightPoseConstants.thirdPerson(FlightPoseState.HOVER);
        var glide = FlightPoseConstants.thirdPerson(FlightPoseState.GLIDE);
        assertTrue(FlightPoseConstants.lerp(hover, glide, 0.01f).banks());
        assertFalse(FlightPoseConstants.lerp(glide, hover, 0.99f).banks());
    }

    /** The first-person arm drops as speed rises so it stays clear of the crosshair. */
    @Test
    void theFirstPersonArmDropsWithSpeed() {
        float hover = FlightPoseConstants.firstPerson(FlightPoseState.HOVER).yOffset();
        float glide = FlightPoseConstants.firstPerson(FlightPoseState.GLIDE).yOffset();
        float propelled = FlightPoseConstants.firstPerson(FlightPoseState.PROPELLED).yOffset();
        assertTrue(hover > glide && glide > propelled,
                "the drop should deepen with speed: " + hover + ", " + glide + ", " + propelled);
    }
}
