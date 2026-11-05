package at.koopro.wizardsandbeasts.client.armor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skirt rule the owner approved on the offline walk sheets (option B). Vanilla leg x-rotation
 * is negative for a forward swing; the cloth bones take GeckoLib's sign, positive forward.
 */
class ClothMotionTest {

    private static final float EPS = 1e-5f;
    private static final float SWING = 0.7f;

    @Test
    void aLowerHalfFollowsItsOwnLegForward() {
        float forward = ClothMotion.lowerHalf(-SWING, 0f);
        assertTrue(forward > 0, "a leg swung forward must carry its half forward");
        assertEquals(SWING * ClothMotion.LEG_FOLLOW, forward, EPS);
        assertEquals(-SWING * ClothMotion.LEG_FOLLOW, ClothMotion.lowerHalf(SWING, 0f), EPS);
    }

    @Test
    void walkingLeavesTheHipFrontHanging() {
        assertEquals(0f, ClothMotion.hipFront(-SWING, SWING), EPS);
        assertEquals(0f, ClothMotion.hipFront(SWING, -SWING), EPS);
        assertEquals(0f, ClothMotion.hipFront(0f, 0f), EPS);
    }

    @Test
    void sittingLiftsTheHipFrontByWhatBothLegsShare() {
        // Vanilla's riding pose: both legs about -1.41 rad, splayed a little.
        float lift = ClothMotion.hipFront(-1.41f, -1.30f);
        assertEquals(1.30f * ClothMotion.SEAT_LIFT, lift, EPS);
    }

    @Test
    void aStandingCapeHangsAtVanillasRestAngleBehindTheWearer() {
        assertEquals(-(float) Math.toRadians(6), ClothMotion.capeRoot(0f, 0f), EPS);
        assertEquals(0f, ClothMotion.capeHem(0f, 0f, 0f), EPS);
    }

    @Test
    void walkingFoldsTheCapesHemBackButNeverIntoAFlag() {
        float stride = 1.4f;                       // vanilla's full walk amplitude, as seen in game
        float root = ClothMotion.capeRoot(20f, 0f);
        float hem = ClothMotion.capeHem(20f, 0f, stride);
        assertTrue(root < 0 && hem < 0, "both parts swing back (negative in GeckoLib's sign)");
        assertEquals(-(float) Math.toRadians(ClothMotion.CAPE_MAX_FOLD_DEG), hem, EPS);
    }

    @Test
    void aGentleWalkFoldsOnlyWhatTheStrideAsks() {
        float stride = 0.4f;
        float needDeg = (float) Math.toDegrees(stride) * ClothMotion.CAPE_CLEAR_SHARE + ClothMotion.CAPE_CLEARANCE_DEG;
        float expected = Math.max(0f, needDeg - (6f + 4f / 2f));
        assertEquals(-(float) Math.toRadians(expected), ClothMotion.capeHem(4f, 0f, stride), EPS);
    }

    @Test
    void aFastCapeAlreadyClearOfTheRobeDoesNotFoldFurther() {
        // Falling or sprinting hard: vanilla's lean alone carries the cape far back.
        assertEquals(0f, ClothMotion.capeHem(150f, 10f, 0.5f), EPS);
    }

    @Test
    void crouchingKeepsTheHalvesHangingWithTheLegs() {
        // Crouched: the body pitches forward 0.5 rad, the legs stay upright. GeckoLib turns the body
        // bone by -0.5; the halves hang off it, so they turn by +0.5 to come back level with the legs.
        float half = ClothMotion.lowerHalf(0f, 0.5f);
        assertEquals(0.5f * ClothMotion.LEG_FOLLOW, half, EPS);
    }
}
