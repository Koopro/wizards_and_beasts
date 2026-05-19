package at.koopro.wizardsandbeasts.mixin.client;

import at.koopro.wizardsandbeasts.client.debug.DebugTransformData;
import at.koopro.wizardsandbeasts.client.debug.ModelDebugEditor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects at the TAIL of {@link PlayerModel#setupAnim(AvatarRenderState)}
 * to apply per-part debug transforms AFTER vanilla animation has set all values.
 * <p>
 * Scale is multiplicative; offset and rotation are additive.
 * <p>
 * Fields like head, body, leftArm etc. live on HumanoidModel (superclass),
 * so we access them by casting rather than @Shadow (which only finds fields
 * declared directly on the target class).
 */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {

    @Inject(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
        at = @At("TAIL")
    )
    private void WizardsAndBeastsMod$applyDebugPartTransforms(AvatarRenderState state, CallbackInfo ci) {
        ModelDebugEditor editor = ModelDebugEditor.get();
        if (!editor.isActive()) return;
        if (editor.getModelMode() != ModelDebugEditor.ModelMode.VANILLA) return;

        PlayerModel model = (PlayerModel) (Object) this;
        HumanoidModel<?> humanoid = model;

        // HumanoidModel parts
        WizardsAndBeastsMod$applyToModelPart(editor, humanoid.head,     "head");
        WizardsAndBeastsMod$applyToModelPart(editor, humanoid.hat,      "hat");
        WizardsAndBeastsMod$applyToModelPart(editor, humanoid.body,     "body");
        WizardsAndBeastsMod$applyToModelPart(editor, humanoid.leftArm,  "left_arm");
        WizardsAndBeastsMod$applyToModelPart(editor, humanoid.rightArm, "right_arm");
        WizardsAndBeastsMod$applyToModelPart(editor, humanoid.leftLeg,  "left_leg");
        WizardsAndBeastsMod$applyToModelPart(editor, humanoid.rightLeg, "right_leg");

        // PlayerModel parts
        WizardsAndBeastsMod$applyToModelPart(editor, model.jacket,      "jacket");
        WizardsAndBeastsMod$applyToModelPart(editor, model.leftSleeve,  "left_sleeve");
        WizardsAndBeastsMod$applyToModelPart(editor, model.rightSleeve, "right_sleeve");
        WizardsAndBeastsMod$applyToModelPart(editor, model.leftPants,   "left_pants");
        WizardsAndBeastsMod$applyToModelPart(editor, model.rightPants,  "right_pants");
    }

    @Unique
    private static void WizardsAndBeastsMod$applyToModelPart(ModelDebugEditor editor, ModelPart part, String name) {
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
