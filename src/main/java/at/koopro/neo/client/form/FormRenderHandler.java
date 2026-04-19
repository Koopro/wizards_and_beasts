package at.koopro.neo.client.form;

import at.koopro.neo.Neo;
import at.koopro.neo.form.ModelType;
import at.koopro.neo.form.SizeProfile;
import at.koopro.neo.network.ClientTransitionTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Client-side render handler for the form/size system.
 * <p>
 * Responsibilities after Mixin refactor:
 * <ul>
 *   <li>Client tick: advance lerp + transition trackers</li>
 *   <li>Non-uniform visual scale for HUMANOID forms (PoseStack only)</li>
 * </ul>
 * Non-HUMANOID rendering is handled by {@code LivingEntityRendererMixin}.
 * HUMANOID scale lerp is handled by {@link FormRenderStateModifier} (state.scale).
 */
@EventBusSubscriber(modid = Neo.MODID, value = Dist.CLIENT)
public class FormRenderHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SizeLerpTracker.tick();
        ClientTransitionTracker.tick();
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        FormRenderStateModifier.FormRenderData formData = FormRenderStateModifier.getFormData(state);
        if (formData == null) return;

        // Non-HUMANOID forms are fully handled by LivingEntityRendererMixin
        if (formData.modelType() != ModelType.HUMANOID) return;

        // HUMANOID: apply non-uniform scale visually via PoseStack
        SizeProfile size = formData.sizeProfile();
        if (size.isNonUniform()) {
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            // Y scale is already handled by state.scale (lerped).
            // Only apply X/Z aspect ratio correction here.
            float sx = size.scaleX() / size.scaleY();
            float sz = size.scaleZ() / size.scaleY();
            poseStack.scale(sx, 1.0f, sz);
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        FormRenderStateModifier.FormRenderData formData = FormRenderStateModifier.getFormData(state);
        if (formData == null) return;

        if (formData.modelType() != ModelType.HUMANOID) return;

        SizeProfile size = formData.sizeProfile();
        if (size.isNonUniform()) {
            event.getPoseStack().popPose();
        }

        FormRenderStateModifier.removeFormData(state);
    }
}
