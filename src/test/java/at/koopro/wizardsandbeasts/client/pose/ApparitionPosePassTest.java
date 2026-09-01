package at.koopro.wizardsandbeasts.client.pose;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The twirl curve, tested away from the renderer.
 *
 * <p>{@link ApparitionPosePass#spinDegrees} is deliberately a closed-form function of the server's own
 * {@code elapsed} rather than a locally accumulated speed, which is exactly what makes it testable — and the
 * reason it is written that way is that an accumulated angle drifts from the charge it is drawing.
 */
class ApparitionPosePassTest {

    private static final int BLINK_WINDOW_OPEN = 10;

    @Test
    void aChargeThatHasNotStartedDoesNotSpin() {
        assertEquals(0.0f, ApparitionPosePass.spinDegrees(0.0f, BLINK_WINDOW_OPEN));
        assertEquals(0.0f, ApparitionPosePass.spinDegrees(-1.0f, BLINK_WINDOW_OPEN));
    }

    @Test
    void theWindUpStartsGentlyAndFinishesAFullTurn() {
        float quarterWay = ApparitionPosePass.spinDegrees(BLINK_WINDOW_OPEN * 0.25f, BLINK_WINDOW_OPEN);
        float halfWay = ApparitionPosePass.spinDegrees(BLINK_WINDOW_OPEN * 0.5f, BLINK_WINDOW_OPEN);
        float atOpen = ApparitionPosePass.spinDegrees(BLINK_WINDOW_OPEN, BLINK_WINDOW_OPEN);

        assertEquals(360.0f, atOpen, 0.01f, "one turn by the time the window opens");
        // Quadratic, so the first quarter of the wind-up is a sixteenth of the rotation: the twirl has to
        // be barely perceptible at the start or every aborted charge looks like a cast.
        assertEquals(360.0f / 16.0f, quarterWay, 0.01f);
        assertEquals(360.0f / 4.0f, halfWay, 0.01f);
    }

    @Test
    void theAngleOnlyEverIncreases() {
        float previous = -1.0f;
        for (float elapsed = 0.0f; elapsed <= 40.0f; elapsed += 0.25f) {
            float degrees = ApparitionPosePass.spinDegrees(elapsed, BLINK_WINDOW_OPEN);
            assertTrue(degrees >= previous,
                    "the twirl reversed at elapsed=" + elapsed + " (" + degrees + " after " + previous + ")");
            previous = degrees;
        }
    }

    /**
     * A visible kick at the moment the window opens would read as the release having already happened, which
     * is the one thing the player is watching for.
     */
    @Test
    void theRateIsContinuousWhereTheWindowOpens() {
        float step = 0.05f;
        float justBefore = rateAt(BLINK_WINDOW_OPEN - step, step);
        float justAfter = rateAt(BLINK_WINDOW_OPEN + step, step);
        assertEquals(justBefore, justAfter, 1.0f, "the spin rate jumps as the window opens");
    }

    @Test
    void aWindowThatOpensImmediatelyIsAlreadyAtFullSpin() {
        assertEquals(360.0f, ApparitionPosePass.spinDegrees(1.0f, 0), 0.01f);
    }

    @Test
    void theSquashRampsToItsFullDepthAndStaysThere() {
        assertEquals(0.0f, ApparitionPosePass.squash(0.0f, BLINK_WINDOW_OPEN));
        assertEquals(0.05f, ApparitionPosePass.squash(BLINK_WINDOW_OPEN * 0.5f, BLINK_WINDOW_OPEN), 0.001f);
        assertEquals(0.10f, ApparitionPosePass.squash(BLINK_WINDOW_OPEN, BLINK_WINDOW_OPEN), 0.001f);
        assertEquals(0.10f, ApparitionPosePass.squash(BLINK_WINDOW_OPEN * 4.0f, BLINK_WINDOW_OPEN), 0.001f,
                "an over-held charge must not keep compressing the body");
    }

    /**
     * The squash is applied as a multiplier on whatever the heritage proportion pass already set, so it must
     * stay well clear of the range where a body could invert or vanish.
     */
    @Test
    void theSquashNeverApproachesZeroHeight() {
        for (float elapsed = 0.0f; elapsed <= 200.0f; elapsed += 1.0f) {
            float squash = ApparitionPosePass.squash(elapsed, BLINK_WINDOW_OPEN);
            assertTrue(squash >= 0.0f && squash <= 0.25f,
                    "squash left its safe range at elapsed=" + elapsed + ": " + squash);
        }
    }

    private static float rateAt(float elapsed, float step) {
        return (ApparitionPosePass.spinDegrees(elapsed + step, BLINK_WINDOW_OPEN)
                - ApparitionPosePass.spinDegrees(elapsed, BLINK_WINDOW_OPEN)) / step;
    }
}
