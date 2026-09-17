package at.koopro.wizardsandbeasts.client.beam;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.util.RenderUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of {@link WandTipTracker} that decide where a beam starts and are checkable without a
 * render context: rebuilding a bone's position from what GeckoLib hands a position listener, and the
 * reach guard that keeps a screen-drawn copy of the player from being booked as a wand tip.
 */
class WandTipTrackerTest {

    private static final double EPS = 1e-4;

    /** A player 6 blocks from the camera, turned, with arm and item transforms on top. */
    private static Matrix4f handPose() {
        return new Matrix4f()
                .translate(-2.5f, -1.0f, 6.0f)
                .rotateY((float) Math.toRadians(137))
                .translate(0.35f, 0.7f, -0.2f)
                .rotateX((float) Math.toRadians(-80))
                .scale(0.9f);
    }

    /** The tip bone inside the wand model, under that hand. */
    private static Matrix4f tipBone(Matrix4f handPose) {
        return new Matrix4f(handPose)
                .translate(0.0f, 0.55f, 0.02f)
                .rotateZ((float) Math.toRadians(12));
    }

    /**
     * Runs GeckoLib's own listener math ({@code GeoBone#updateBonePositionListeners}), so a GeckoLib
     * upgrade that changes what {@code localPos} is relative to fails here, not in a screenshot.
     */
    @Test
    void cameraRelative_recoversTheBonePose() {
        Matrix4f hand = handPose();
        Matrix4f bone = tipBone(hand);
        Vec3 localPos = RenderUtil.renderPoseToPosition(RenderUtil.extractPoseFromRoot(bone, hand), 1, 1, 1);

        Vec3 expected = RenderUtil.renderPoseToPosition(bone, 1, 1, 1);
        assertEquals(0.0, WandTipTracker.cameraRelative(hand, localPos).distanceTo(expected), EPS);
    }

    @Test
    void localPos_aloneIsNotTheTip() {
        Matrix4f hand = handPose();
        Matrix4f bone = tipBone(hand);
        Vec3 localPos = RenderUtil.renderPoseToPosition(RenderUtil.extractPoseFromRoot(bone, hand), 1, 1, 1);

        Vec3 tip = RenderUtil.renderPoseToPosition(bone, 1, 1, 1);
        assertTrue(localPos.distanceTo(tip) > 1.0,
                "localPos is relative to the item, with the caster's position and arm missing");
    }

    @Test
    void withinReach_acceptsAWandHeldOverhead() {
        assertTrue(WandTipTracker.withinReach(new Vec3(0.4, 2.6, 0.5), 1.8f));
    }

    @Test
    void withinReach_scalesWithTheCaster() {
        Vec3 tip = new Vec3(1.5, 8.0, 2.0);
        assertFalse(WandTipTracker.withinReach(tip, 1.8f));
        assertTrue(WandTipTracker.withinReach(tip, 7.2f), "a scaled-up caster holds its wand further out");
    }

    @Test
    void withinReach_rejectsAScreenDrawnCopyOfThePlayer() {
        // A GUI pose is scaled to pixels, so the same hand lands tens of blocks from the player.
        Vec3 guiHand = new Vec3(0.35, 1.1, -0.3).scale(30);
        assertFalse(WandTipTracker.withinReach(guiHand, 1.8f));
    }
}
