package at.koopro.wizardsandbeasts.client.pose;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The band contract and the op algebra.
 *
 * <p>Both are pure arithmetic over enums, so they are testable without a client, a model or a render
 * pass — which is most of the reason {@code PartPoseData} queues operations instead of writing them
 * straight onto a {@code ModelPart}.
 */
class PoseBandTest {

    @Test
    void everyBandBoundaryResolves() {
        assertEquals(PoseBand.POSTURE, PoseBand.require(0, "t"));
        assertEquals(PoseBand.POSTURE, PoseBand.require(99, "t"));
        assertEquals(PoseBand.LOCOMOTION, PoseBand.require(100, "t"));
        assertEquals(PoseBand.LOCOMOTION, PoseBand.require(199, "t"));
        assertEquals(PoseBand.CASTING, PoseBand.require(200, "t"));
        assertEquals(PoseBand.CASTING, PoseBand.require(299, "t"));
        assertEquals(PoseBand.TRANSFORM, PoseBand.require(300, "t"));
        assertEquals(PoseBand.TRANSFORM, PoseBand.require(Integer.MAX_VALUE, "t"));
    }

    /**
     * A priority in no band is a hard error, not a warning.
     *
     * <p>The failure it prevents is quiet: a pass with a nonsense priority still runs, just somewhere
     * arbitrary in the order, and surfaces weeks later as a pose nobody can trace back to a number in
     * a constructor.
     */
    @Test
    void negativePriorityIsRejectedAndNamesThePass() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> PoseBand.require(-1, "my_pass"));
        assertTrue(thrown.getMessage().contains("my_pass"),
                "the error must name the offending pass, not just the number");
    }

    @Test
    void setLerpsByMultiplier() {
        float result = PoseOpType.SET.apply(PoseTarget.X, 0f, 10f, 0f, 0.5f);
        assertEquals(5f, result, 1e-6);
    }

    @Test
    void multiplierZeroLeavesTheAccumulatedValueUntouched() {
        assertEquals(3f, PoseOpType.SET.apply(PoseTarget.X, 3f, 99f, 0f, 0f), 1e-6);
        assertEquals(3f, PoseOpType.ADD.apply(PoseTarget.X, 3f, 99f, 0f, 0f), 1e-6);
        assertEquals(3f, PoseOpType.RESET.apply(PoseTarget.X, 3f, 0f, 0f, 0f), 1e-6);
    }

    /** Scale is multiplicative around 1.0: two passes each adding 0.5 must not erase the part. */
    @Test
    void addOnScaleIsMultiplicative() {
        float once = PoseOpType.ADD.apply(PoseTarget.X_SCALE, 1f, 0.5f, 1f, 1f);
        assertEquals(1.5f, once, 1e-6);
        float twice = PoseOpType.ADD.apply(PoseTarget.X_SCALE, once, 0.5f, 1f, 1f);
        assertEquals(2.25f, twice, 1e-6);
    }

    @Test
    void addOnTranslationIsAdditive() {
        assertEquals(4f, PoseOpType.ADD.apply(PoseTarget.X, 3f, 1f, 0f, 1f), 1e-6);
    }

    /**
     * RESET blends toward the captured vanilla value, not toward zero or a rest pose.
     *
     * <p>The distinction is the whole of schema rev 4. A blend-out means "hand this limb back to
     * vanilla mid-stride"; if it targeted the authored rest pose, a pose releasing during a walk
     * would snap the limb toward a T-pose for one frame before {@code setupAnim} put it back in the
     * walk cycle. Here the captured value is 1.0 — a limb mid-swing — and a half-weight RESET lands
     * halfway between the posed value and that, never at zero.
     */
    @Test
    void resetBlendsTowardTheCapturedVanillaValue() {
        float posed = 4f;
        float capturedVanilla = 1f;
        assertEquals(2.5f, PoseOpType.RESET.apply(PoseTarget.X_ROT, posed, 0f, capturedVanilla, 0.5f), 1e-6);
        assertEquals(capturedVanilla,
                PoseOpType.RESET.apply(PoseTarget.X_ROT, posed, 0f, capturedVanilla, 1f), 1e-6,
                "a full-weight RESET lands exactly on vanilla's value");
    }

    /** The operand is ignored by RESET — the destination is the captured value, not the value field. */
    @Test
    void resetIgnoresItsOperand() {
        assertEquals(PoseOpType.RESET.apply(PoseTarget.X_ROT, 4f, 0f, 1f, 1f),
                PoseOpType.RESET.apply(PoseTarget.X_ROT, 4f, 99f, 1f, 1f), 1e-6);
    }

    /**
     * SET_SHORTEST takes the near arc; SET does not.
     *
     * <p>350° to 10° is 20° forward or 340° backward. Anything derived from entity yaw crosses that
     * boundary constantly, so a raw lerp there spins the model the long way round once per rotation.
     */
    @Test
    void setShortestTakesTheNearArcWhereSetDoesNot() {
        float from = (float) Math.toRadians(350);
        float to = (float) Math.toRadians(10);

        float shortest = PoseOpType.SET_SHORTEST.apply(PoseTarget.Y_ROT, from, to, 0f, 1f);
        assertEquals(Math.toRadians(370), shortest, 1e-4,
                "the short way is forward past 360, not backward through 180");

        float raw = PoseOpType.SET.apply(PoseTarget.Y_ROT, from, to, 0f, 1f);
        assertEquals(to, raw, 1e-6, "SET is deliberately raw");
    }

    @Test
    void setShortestIsPlainLerpOnNonRotationTargets() {
        assertEquals(10f, PoseOpType.SET_SHORTEST.apply(PoseTarget.X, 0f, 10f, 0f, 1f), 1e-6);
    }

    @Test
    void postRotationTargetsAreFlaggedAndScaleIdentityIsOne() {
        assertTrue(PoseTarget.X2.isPostRotation());
        assertFalse(PoseTarget.X.isPostRotation());
        assertEquals(1f, PoseTarget.X_SCALE.identity(), 1e-6);
        assertEquals(0f, PoseTarget.X_ROT.identity(), 1e-6);
    }

    @Test
    void bodyIsTheOnlyVirtualPart() {
        for (PlayerModelPart part : PlayerModelPart.values()) {
            assertEquals(part == PlayerModelPart.BODY, part.isVirtual(), part.name());
        }
    }

    @Test
    void builderCreatesPartsLazilyAndClears() {
        PoseBuilder builder = new PoseBuilder();
        assertTrue(builder.isEmpty(), "an untouched builder costs nothing");
        builder.get(PlayerModelPart.HEAD).setRotDeg(PoseTarget.X_ROT, 30f);
        assertEquals(1, builder.parts().size());
        builder.clear();
        assertTrue(builder.isEmpty());
    }
}
