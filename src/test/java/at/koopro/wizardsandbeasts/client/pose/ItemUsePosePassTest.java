package at.koopro.wizardsandbeasts.client.pose;

import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The optic arm maths, tested away from the renderer.
 *
 * <p>What is actually at risk here is agreement. Vanilla poses the arm holding the Omnioculars
 * through {@code ArmPose.SPYGLASS} and {@link ItemUsePosePass} poses the other one, so the two arms
 * are computed by two different pieces of code that have to land in the same place. These tests
 * assert the second against the first's published formula rather than against a screenshot.
 */
class ItemUsePosePassTest {

    /** Straight from {@code HumanoidModel}'s SPYGLASS case, for the arm vanilla poses. */
    private static float vanillaHoldingArmPitch(float headXRot, boolean crouching) {
        return net.minecraft.util.Mth.clamp(
                headXRot - 1.9198622f - (crouching ? (float) (Math.PI / 12) : 0f), -2.4f, 3.3f);
    }

    private static float vanillaRightArmYaw(float headYRot) {
        return headYRot - (float) (Math.PI / 12);
    }

    private static float vanillaLeftArmYaw(float headYRot) {
        return headYRot + (float) (Math.PI / 12);
    }

    @Test
    void theFreeArmPitchMatchesTheArmVanillaPoses() {
        for (float headXRot = -1.5f; headXRot <= 1.5f; headXRot += 0.1f) {
            assertEquals(vanillaHoldingArmPitch(headXRot, false),
                    ItemUsePosePass.opticPitch(headXRot, false), 1.0e-6f,
                    "the two arms disagree at head pitch " + headXRot);
        }
    }

    @Test
    void crouchingLowersBothArmsByTheSameAmount() {
        float standing = ItemUsePosePass.opticPitch(0.0f, false);
        float crouched = ItemUsePosePass.opticPitch(0.0f, true);

        assertEquals(vanillaHoldingArmPitch(0.0f, true), crouched, 1.0e-6f);
        assertEquals((float) (Math.PI / 12), standing - crouched, 1.0e-6f,
                "the crouch offset is not vanilla's PI/12");
    }

    /**
     * Looking straight up drives the raw pitch past the clamp. Without it the shoulder keeps
     * rotating and the arm folds back through the chest, which is what the bound is for — and it is
     * the one branch a player reaches by doing something completely ordinary.
     */
    @Test
    void aHardUpwardLookIsClamped() {
        float straightUp = (float) -Math.PI / 2.0f;
        assertEquals(-2.4f, ItemUsePosePass.opticPitch(straightUp, false), 1.0e-6f);
        assertEquals(-2.4f, ItemUsePosePass.opticPitch(straightUp, true), 1.0e-6f);
    }

    /**
     * The upper bound is vanilla's and is kept for agreement, not because a look angle reaches it: a
     * head pitch tops out near {@code PI/2}, which leaves the raw value around {@code -0.35}. If this
     * ever fails, something is feeding the pass a pitch that is not a look direction.
     */
    @Test
    void theUpperClampIsUnreachableFromARealLookAngle() {
        float straightDown = (float) Math.PI / 2.0f;
        assertTrue(ItemUsePosePass.opticPitch(straightDown, false) < 3.3f);
    }

    @Test
    void eachArmSplaysTowardTheMidlineAndNotAcrossIt() {
        float headYRot = 0.4f;

        assertEquals(vanillaRightArmYaw(headYRot),
                ItemUsePosePass.opticYaw(headYRot, HumanoidArm.RIGHT), 1.0e-6f);
        assertEquals(vanillaLeftArmYaw(headYRot),
                ItemUsePosePass.opticYaw(headYRot, HumanoidArm.LEFT), 1.0e-6f);
    }

    /**
     * The whole point of the pass. A right-handed wizard has the right arm posed by vanilla and the
     * left by this pass; if the mirror were dropped, both hands would swing to the same side of the
     * face and the Omnioculars would sit crooked.
     */
    // ── working strokes ──────────────────────────────────────────────────────

    /**
     * The pose's stroke period and the item's are separate constants in separate source sets — one
     * client, one common — so nothing but this stops them drifting apart. They drifting apart is not
     * a crash: it is a knife that swings out of time with its own chips, which nobody would trace
     * back to a number.
     */
    @Test
    void theCarveStrokeMatchesTheItemThatFiresIt() {
        assertEquals(at.koopro.wizardsandbeasts.item.wand.WandBlankItem.STROKE_TICKS,
                ItemUsePoseConstants.CARVE.strokeTicks());
    }

    /**
     * A channel that is not a whole number of strokes ends with the arm stopped somewhere in the
     * middle of a swing, which reads as the tool being snatched away.
     */
    @Test
    void bothChannelsAreAWholeNumberOfStrokes() {
        assertEquals(0,
                at.koopro.wizardsandbeasts.item.wand.WandBlankItem.CARVE_TICKS
                        % ItemUsePoseConstants.CARVE.strokeTicks(),
                "the carve ends mid-stroke");
        assertEquals(0,
                at.koopro.wizardsandbeasts.item.broom.BroomPolishItem.POLISH_TICKS
                        % ItemUsePoseConstants.POLISH.strokeTicks(),
                "the polish ends mid-stroke");
    }

    /**
     * The whole point of {@code -cos}. Both items fire their sound and particles on
     * {@code elapsed % strokeTicks == 0}, so the arm has to be fully forward — phase {@code -1} — at
     * exactly those ticks. Written with {@code sin} the arm is at zero there, mid-swing, and every
     * chip flies while the knife is halfway back.
     */
    @Test
    void theArmIsFullyForwardOnEveryTickAChipFlies() {
        int stroke = ItemUsePoseConstants.CARVE.strokeTicks();
        for (int tick = 0; tick <= 50; tick += stroke) {
            assertEquals(-1.0f, ItemUsePosePass.strokePhase(tick, stroke), 1.0e-5f,
                    "the arm was not at the end of its travel at tick " + tick);
        }
    }

    @Test
    void theArmIsDrawnRightBackBetweenStrokes() {
        int stroke = ItemUsePoseConstants.CARVE.strokeTicks();
        assertEquals(1.0f, ItemUsePosePass.strokePhase(stroke / 2.0f, stroke), 1.0e-5f);
        assertEquals(1.0f, ItemUsePosePass.strokePhase(stroke * 1.5f, stroke), 1.0e-5f);
    }

    @Test
    void theStrokeStaysWithinItsTravel() {
        int stroke = ItemUsePoseConstants.POLISH.strokeTicks();
        for (float tick = 0.0f; tick <= 40.0f; tick += 0.25f) {
            float phase = ItemUsePosePass.strokePhase(tick, stroke);
            assertTrue(phase >= -1.0f - 1.0e-5f && phase <= 1.0f + 1.0e-5f,
                    "the stroke left its travel at tick " + tick + " (" + phase + ")");
        }
    }

    // ── winding the chain ────────────────────────────────────────────────────

    @Test
    void oneTurnTakesExactlyTheSpinPeriod() {
        int spin = ItemUsePoseConstants.WIND.spinTicks();
        assertEquals(0.0f, ItemUsePosePass.spinDegrees(0, spin), 1.0e-4f);
        assertEquals(90.0f, ItemUsePosePass.spinDegrees(spin * 0.25f, spin), 1.0e-4f);
        assertEquals(180.0f, ItemUsePosePass.spinDegrees(spin * 0.5f, spin), 1.0e-4f);
        assertEquals(0.0f, ItemUsePosePass.spinDegrees(spin, spin), 1.0e-4f, "the turn did not close");
    }

    /**
     * The reason the angle is wrapped rather than left to accumulate. The Time-Turner's channel runs
     * to {@code MAX_USE_TICKS} — an hour — and an unbounded angle would be past a million degrees by
     * then, far enough that a float starts visibly quantising the rotation.
     */
    @Test
    void theAngleStaysInsideOneTurnForTheWholeChannel() {
        int spin = ItemUsePoseConstants.WIND.spinTicks();
        for (float tick = 0.0f; tick <= 72000.0f; tick += 37.0f) {
            float degrees = ItemUsePosePass.spinDegrees(tick, spin);
            assertTrue(degrees >= 0.0f && degrees < 360.0f,
                    "the wind left one turn at tick " + tick + " (" + degrees + ")");
        }
    }

    /** Within a single turn the chain only ever winds forwards. */
    @Test
    void theWindDoesNotReverseWithinATurn() {
        int spin = ItemUsePoseConstants.WIND.spinTicks();
        float previous = -1.0f;
        for (float tick = 0.0f; tick < spin; tick += 0.25f) {
            float degrees = ItemUsePosePass.spinDegrees(tick, spin);
            assertTrue(degrees >= previous,
                    "the wind reversed at tick " + tick + " (" + degrees + " after " + previous + ")");
            previous = degrees;
        }
    }

    @Test
    void theTwoArmsAreSymmetricAboutTheLookDirection() {
        float headYRot = -1.2f;
        float right = ItemUsePosePass.opticYaw(headYRot, HumanoidArm.RIGHT);
        float left = ItemUsePosePass.opticYaw(headYRot, HumanoidArm.LEFT);

        assertEquals(headYRot, (right + left) / 2.0f, 1.0e-6f, "the arms are not centred on the look direction");
        assertTrue(right < left, "the arms are splayed the wrong way round");
    }
}
