package at.koopro.wizardsandbeasts.client.form;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.NullMarked;

/**
 * The transform that puts a replacement player model where the player is standing.
 *
 * <p>Two render paths need it and must agree, or the same player changes position and facing when
 * their form happens to be backed by different art:
 *
 * <ul>
 *   <li>{@code FormModelRenderer.renderVanilla}, for the Animagus forms that borrow a real vanilla
 *       entity model;</li>
 *   <li>{@code PlayerFormGeoRenderer.adjustRenderPose}, for the heritage forms that draw a GeckoLib
 *       rig.</li>
 * </ul>
 *
 * <p>It lives here as one method rather than twice as two literal sequences because the two copies
 * were already drifting: {@code GeoObjectRenderer}'s inherited pose adjustment translates by
 * {@code (0.5, 0.51, 0.5)}, which is right for something drawn from a block corner and half a block
 * wrong in three axes for a player, and nothing would have caught the difference except looking at it.
 *
 * <p>Both vanilla entity models and GeckoLib's baked models are authored with the origin at the top
 * and +Y pointing down — GeckoLib's loader converts {@code y_model = 24 - y_geo} on load, landing in
 * the same convention — so the same flip serves both.
 */
@NullMarked
public final class FormEntitySpace {

    /** Vanilla's entity-model vertical offset: drops the origin from the model's top to its feet. */
    public static final float MODEL_Y_OFFSET = -1.501f;

    private FormEntitySpace() {}

    /**
     * Yaw to face the body direction, flip into the model's Y-down convention, then drop the origin
     * to the feet.
     *
     * <p>The caller owns the surrounding push/pop and any form scale — applying scale here would
     * square it against the size profile the mixin has already put on the stack.
     *
     * @param bodyYaw body rotation in degrees, as carried on the render state
     */
    public static void apply(PoseStack poseStack, float bodyYaw) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, MODEL_Y_OFFSET, 0.0f);
    }
}
