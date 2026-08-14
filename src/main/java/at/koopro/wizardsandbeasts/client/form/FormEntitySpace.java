package at.koopro.wizardsandbeasts.client.form;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.NullMarked;

/**
 * The transform that puts a <b>vanilla</b> entity model where the player is standing.
 *
 * <p>Used by {@code FormModelRenderer.renderVanilla}, for the Animagus forms that borrow a real
 * vanilla entity model (cat, wolf, rabbit, parrot, silverfish). Vanilla models are authored with the
 * origin at the top and +Y running <em>down</em> toward the feet, so placing one takes a yaw, a flip
 * into world orientation, and a drop from the model's top to its feet.
 *
 * <p><b>This is not the GeckoLib convention, and the two must not be shared.</b> An earlier version
 * of this class asserted that GeckoLib's loader landed baked models in the same space, and routed
 * {@code PlayerFormGeoRenderer} through here to remove the apparent duplication. It does not, and the
 * result rendered the werewolf upside down, inside out, and buried a block and a half in the floor —
 * three symptoms from that one assumption. GeckoLib converts geometry on load and hands back a model
 * that already stands upright with its origin at the feet, which is why
 * {@code GeoEntityRenderer.adjustRenderPose} applies a yaw and nothing else.
 *
 * <p>This is precisely the {@code scale(-1,-1,1)} versus {@code diag(1,-1,1)} distinction the stack
 * rules call out by name: <i>do not mix them</i>. {@code FormEntitySpaceTest} now pins the two paths
 * apart, so the same tidying-up cannot be repeated by accident.
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
