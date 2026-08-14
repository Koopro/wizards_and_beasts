package at.koopro.wizardsandbeasts.client.form;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transform that places a replacement player model.
 *
 * <p>Worth pinning because it is the one piece of the GeckoLib form path that headless work cannot
 * otherwise check, and because sign errors here are invisible in code review and obvious in a frame:
 * a wrong yaw sign makes the model face backwards, a wrong scale sign turns it inside out, and a
 * wrong offset buries it in the floor or floats it.
 */
class FormEntitySpaceTest {

    private static Matrix4f applied(float bodyYaw) {
        PoseStack stack = new PoseStack();
        FormEntitySpace.apply(stack, bodyYaw);
        return new Matrix4f(stack.last().pose());
    }

    /** The literal sequence the borrowed-Animagus path used before it was routed through one method. */
    private static Matrix4f legacyVanillaPath(float bodyYaw) {
        PoseStack stack = new PoseStack();
        stack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        stack.scale(-1.0f, -1.0f, 1.0f);
        stack.translate(0.0f, -1.501f, 0.0f);
        return new Matrix4f(stack.last().pose());
    }

    @Test
    void matchesTheTransformTheBorrowedVanillaModelsAlreadyUsed() {
        for (float yaw : new float[]{0f, 45f, 90f, 180f, -90f, 359f}) {
            assertTrue(applied(yaw).equals(legacyVanillaPath(yaw), 1e-5f),
                    "the borrowed-vanilla path must keep the transform it always had; "
                            + "it differs at bodyYaw=" + yaw);
        }
    }

    /**
     * <b>This transform is for vanilla models only.</b>
     *
     * <p>An earlier version of this class asserted that the GeckoLib player-form path had to produce
     * the same matrix, on the reasoning that both draw a replacement player model. That was wrong, it
     * shipped, and it put the werewolf upside down, inside out and buried a block and a half into the
     * floor — three symptoms, one cause.
     *
     * <p>Vanilla models are authored origin-at-top with +Y running down, so they need the flip and the
     * 1.501 drop. A GeckoLib baked model has already been converted by GeckoLib's loader and stands
     * upright with its origin at the feet, which is why {@code GeoEntityRenderer.adjustRenderPose}
     * applies a yaw and nothing else. The two conventions are the {@code scale(-1,-1,1)} versus
     * {@code diag(1,-1,1)} pair the stack rules say in as many words not to mix.
     *
     * <p>So the two paths are pinned <em>apart</em> here, deliberately. If someone later routes
     * {@code PlayerFormGeoRenderer} through {@link FormEntitySpace} to remove the apparent
     * duplication, this fails and says why.
     */
    @Test
    void theGeckoLibPathMustNotUseThisTransform() {
        float yaw = 42f;

        PoseStack geckolib = new PoseStack();
        geckolib.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
        geckolib.translate(0.0f, 0.01f, 0.0f);
        Matrix4f geckolibMatrix = new Matrix4f(geckolib.last().pose());

        assertFalse(applied(yaw).equals(geckolibMatrix, 1e-4f),
                "the vanilla-model transform and the GeckoLib one are different conventions; "
                        + "if these ever match, one of them has been broken to match the other");

        // And specifically: no vertical flip on the GeckoLib side.
        Vector4f up = geckolibMatrix.transform(new Vector4f(0f, 1f, 0f, 0f));
        assertTrue(up.y > 0f, "a GeckoLib baked model is already upright; nothing may flip it");
    }

    /**
     * The model-space convention these transforms exist to undo.
     *
     * <p>Vanilla entity models are authored <b>Y-down with the origin at the top</b>: {@code y = 0} is
     * the neck, and {@code y} grows toward the feet, which sit near {@code +1.5} blocks (24
     * sixteenths). That is why the sequence ends in {@code translate(0, -1.501, 0)} — after the flip it
     * puts the feet on the entity origin rather than the head. <b>GeckoLib baked models are not in this
     * space</b>; see {@link #theGeckoLibPathMustNotUseThisTransform()}.
     *
     * <p>Spelled out because a first pass at this test asserted the opposite and the numbers caught
     * it. Getting the sign of this convention wrong is a whole-model displacement.
     */
    @Test
    void theModelFeetLandOnTheEntityOrigin() {
        Vector4f feetInModelSpace = new Vector4f(0f, 1.5f, 0f, 1f);
        Vector4f world = applied(0f).transform(new Vector4f(feetInModelSpace));

        assertEquals(0.0f, world.y, 2e-3f,
                "feet are at model y=+1.5 and must land on the player's own origin; got " + world.y);
    }

    @Test
    void theModelTopLandsAPlayerHeightAboveTheFeet() {
        Vector4f topInModelSpace = new Vector4f(0f, 0f, 0f, 1f);
        Vector4f world = applied(0f).transform(new Vector4f(topInModelSpace));

        assertTrue(world.y > 0f,
                "the Y-down flip is what makes a model stand up rather than hang; got y=" + world.y);
        assertEquals(1.501f, world.y, 1e-4f,
                "the authoring origin is the top of the model, so it lands ~1.5 blocks up");
    }

    /**
     * Yaw must rotate the model, and must do so in the direction that leaves a forward-facing model
     * facing the way the body points.
     */
    @Test
    void yawTurnsTheModel() {
        Vector4f noseInModelSpace = new Vector4f(0f, 0f, 1f, 1f);

        Vector4f facingNorth = applied(0f).transform(new Vector4f(noseInModelSpace));
        Vector4f facingEast = applied(90f).transform(new Vector4f(noseInModelSpace));

        assertTrue(Math.abs(facingNorth.z) > 0.9f,
                "at yaw 0 the nose should lie on the Z axis, got " + facingNorth);
        assertTrue(Math.abs(facingEast.x) > 0.9f,
                "at yaw 90 the nose should have swung onto the X axis, got " + facingEast);
    }

    @Test
    void theTransformIsPurelyPlacement_noScaleIsBakedIn() {
        // Scale belongs to the size profile, applied by the mixin before this runs. If this method
        // ever grew a scale factor it would be squared against the profile.
        Matrix4f matrix = applied(0f);
        Vector4f unit = matrix.transform(new Vector4f(1f, 0f, 0f, 0f));
        assertEquals(1.0f, unit.length(), 1e-5f,
                "a direction vector must keep unit length; a scale here would square against the "
                        + "size profile the mixin already applied");
    }
}
