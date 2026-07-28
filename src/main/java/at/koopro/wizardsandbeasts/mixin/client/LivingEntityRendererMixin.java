package at.koopro.wizardsandbeasts.mixin.client;

import at.koopro.wizardsandbeasts.client.form.FormModelRenderer;
import at.koopro.wizardsandbeasts.client.form.FormRenderStateModifier;
import at.koopro.wizardsandbeasts.client.form.SizeLerpTracker;
import at.koopro.wizardsandbeasts.form.ModelType;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on LivingEntityRenderer to intercept submit() at HEAD for non-HUMANOID forms.
 * This fires BEFORE vanilla applies state.scale and AvatarRenderer's 0.9375f,
 * giving us full control over the rendering of custom form models.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void WizardsAndBeastsMod$renderCustomForm(
            LivingEntityRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera,
            CallbackInfo ci) {

        FormRenderStateModifier.FormRenderData formData =
                FormRenderStateModifier.getFormData(state);
        if (formData == null || formData.modelType() == ModelType.HUMANOID) return;

        // Cancel vanilla render entirely for non-HUMANOID forms
        ci.cancel();

        poseStack.pushPose();

        // Apply form scale with smooth lerp
        SizeProfile size = formData.sizeProfile();
        float pt = Minecraft.getInstance()
                .getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float visualScale = SizeLerpTracker.getVisualScale(
                formData.playerUUID(), size.modelScale(), pt);

        float sx = size.modelAspectX() * visualScale;
        float sy = visualScale;
        float sz = size.modelAspectZ() * visualScale;
        poseStack.scale(sx, sy, sz);

        // Render custom model through the proper render pipeline
        FormModelRenderer.renderToCollector(poseStack, collector, formData, state);

        poseStack.popPose();
    }
}
