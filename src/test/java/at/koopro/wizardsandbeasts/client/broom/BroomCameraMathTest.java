package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.entity.broom.BroomTuning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camera behaviour that cannot be judged from a screenshot but is obvious in motion: a horizon that
 * tips too far, a camera that keeps sliding out at speed, and — the one that only shows up minutes
 * later — a camera that never quite hands itself back after the player dismounts.
 */
class BroomCameraMathTest {

    private static final float EPS = 1e-5F;

    @Test
    void roll_staysUnderTheCapEvenAtTheBroomsMaximumBank() {
        float atMaxBank = BroomCameraMath.rollTarget(BroomTuning.MAX_ROLL_TILT);

        assertTrue(atMaxBank <= BroomCameraMath.MAX_ROLL_DEG,
                "camera roll must never exceed the cap; got " + atMaxBank);
        assertTrue(atMaxBank < BroomTuning.MAX_ROLL_TILT,
                "camera must take less roll than the broom, or the horizon tips as hard as the broom does");
    }

    @Test
    void roll_isSignPreservingAndSymmetric() {
        assertEquals(-BroomCameraMath.rollTarget(20.0F), BroomCameraMath.rollTarget(-20.0F), EPS,
                "banking left and right must mirror, or the camera drifts one way over a flight");
        assertTrue(BroomCameraMath.rollTarget(20.0F) > 0.0F, "a positive bank must roll positively");
    }

    @Test
    void roll_clampsBeyondTheBroomsOwnLimit() {
        // A tilt this large should be impossible, but a physics change upstream must not be able to
        // whip the camera past the cap.
        assertEquals(BroomCameraMath.MAX_ROLL_DEG, BroomCameraMath.rollTarget(1000.0F), EPS);
        assertEquals(-BroomCameraMath.MAX_ROLL_DEG, BroomCameraMath.rollTarget(-1000.0F), EPS);
    }

    @Test
    void pullBack_isZeroAtRestAndCappedAtFullBoost() {
        assertEquals(0.0F, BroomCameraMath.pullBackTarget(0.0F), EPS,
                "a stationary broom must leave the camera exactly where vanilla put it");

        float boosted = BroomTuning.MAX_SPEED * BroomTuning.BOOST_MULTIPLIER;
        assertEquals(BroomCameraMath.MAX_PULL_BACK, BroomCameraMath.pullBackTarget(boosted), EPS);
        assertEquals(BroomCameraMath.MAX_PULL_BACK, BroomCameraMath.pullBackTarget(boosted * 4.0F), EPS,
                "overspeed must saturate, not keep pushing the camera out");
    }

    @Test
    void pullBack_treatsReverseAsSpeedNotAsPullIn() {
        assertEquals(BroomCameraMath.pullBackTarget(0.6F), BroomCameraMath.pullBackTarget(-0.6F), EPS);
        assertTrue(BroomCameraMath.pullBackTarget(-0.6F) >= 0.0F,
                "a negative pull-back would shove the camera inside the rider");
    }

    @Test
    void pullBack_growsWithSpeed() {
        float slow = BroomCameraMath.pullBackTarget(0.2F);
        float fast = BroomCameraMath.pullBackTarget(0.9F);

        assertTrue(fast > slow, "the camera must ease out as speed builds, not jump between states");
    }

    @Test
    void advance_settlesToExactlyZeroAfterDismount() {
        // The value the camera was holding mid-turn, then the rider dismounts: target becomes 0.
        float value = BroomCameraMath.rollTarget(BroomTuning.MAX_ROLL_TILT);
        assertTrue(value > 0.0F);

        int frames = 0;
        while (value != 0.0F && frames < 600) {
            value = BroomCameraMath.advance(value, 0.0F);
            frames++;
        }

        assertEquals(0.0F, value, "camera must return to exactly neutral, not asymptotically near it");
        assertTrue(frames < 120, "return to neutral must take under ~2s at 60fps; took " + frames + " frames");
    }

    @Test
    void advance_easesRatherThanSnapping() {
        float target = BroomCameraMath.MAX_ROLL_DEG;
        float afterOneFrame = BroomCameraMath.advance(0.0F, target);

        assertTrue(afterOneFrame > 0.0F, "must move toward the target");
        assertTrue(afterOneFrame < target * 0.5F,
                "a single frame must not cover half the distance, or a hard turn reads as a snap");
    }

    @Test
    void advance_neverOvershootsTheTarget() {
        float value = 0.0F;
        float target = BroomCameraMath.MAX_ROLL_DEG;
        for (int i = 0; i < 500; i++) {
            value = BroomCameraMath.advance(value, target);
            assertTrue(value <= target + EPS, "smoothing must not overshoot; got " + value);
        }
        assertEquals(target, value, 0.01F, "and must actually converge");
    }
}
