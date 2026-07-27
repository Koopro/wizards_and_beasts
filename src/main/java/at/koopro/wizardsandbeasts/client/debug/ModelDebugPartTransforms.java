package at.koopro.wizardsandbeasts.client.debug;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the {@link ModelDebugEditor}'s per-part transforms to a vanilla player model. Extracted
 * out of {@code PlayerModelMixin} so the mixin is pure delegation: a mixin that carries logic does
 * not survive a Minecraft update, one that resolves a target and calls out does.
 *
 * <p>Scale is multiplicative; offset and rotation are additive — the editor nudges vanilla animation
 * rather than replacing it, which is why this must run after {@code setupAnim} has set every value.
 */
@NullMarked
public final class ModelDebugPartTransforms {

    private ModelDebugPartTransforms() {}

    public static void apply(PlayerModel model) {
        ModelDebugEditor editor = ModelDebugEditor.get();
        if (!editor.isActive()) return;
        if (editor.getModelMode() != ModelDebugEditor.ModelMode.VANILLA) return;

        HumanoidModel<?> humanoid = model;

        // HumanoidModel parts
        applyToModelPart(editor, humanoid.head,     "head");
        applyToModelPart(editor, humanoid.hat,      "hat");
        applyToModelPart(editor, humanoid.body,     "body");
        applyToModelPart(editor, humanoid.leftArm,  "left_arm");
        applyToModelPart(editor, humanoid.rightArm, "right_arm");
        applyToModelPart(editor, humanoid.leftLeg,  "left_leg");
        applyToModelPart(editor, humanoid.rightLeg, "right_leg");

        // PlayerModel parts
        applyToModelPart(editor, model.jacket,      "jacket");
        applyToModelPart(editor, model.leftSleeve,  "left_sleeve");
        applyToModelPart(editor, model.rightSleeve, "right_sleeve");
        applyToModelPart(editor, model.leftPants,   "left_pants");
        applyToModelPart(editor, model.rightPants,  "right_pants");
    }

    private static void applyToModelPart(ModelDebugEditor editor, ModelPart part, String name) {
        DebugTransformData d = editor.getData(name);
        if (d.isDefault()) return;
        // Scale: multiplicative — don't overwrite vanilla
        part.xScale *= d.scaleX;
        part.yScale *= d.scaleY;
        part.zScale *= d.scaleZ;
        // Offset and rotation: additive
        part.x += d.offX;
        part.y += d.offY;
        part.z += d.offZ;
        part.xRot += d.rotX;
        part.yRot += d.rotY;
        part.zRot += d.rotZ;
    }
}
