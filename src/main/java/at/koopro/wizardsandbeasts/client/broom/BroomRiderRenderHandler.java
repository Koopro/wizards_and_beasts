package at.koopro.wizardsandbeasts.client.broom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Banks the rider's whole body with the broom. The limb pose is a separate concern and lives in
 * {@link BroomRiderPoseHandler}; this only rotates the pose stack around the seat.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public class BroomRiderRenderHandler {

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        BroomRiderRenderer.BroomRideData tilt = BroomRiderRenderer.getRide(state);
        if (tilt == null) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // 0.75 is the rider's hip: rotating about the seat rather than about their feet is what
        // makes a bank read as leaning into a turn instead of swinging from the ankles.
        poseStack.translate(0, 0.75, 0);
        // Yaw first, so the bank and the lean are applied in the rider's own frame — a side-saddle
        // rider banks about the broom's axis, not about their own shoulders.
        if (tilt.yawOffset() != 0.0f) {
            poseStack.mulPose(Axis.YP.rotationDegrees(tilt.yawOffset()));
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(tilt.roll()));
        poseStack.mulPose(Axis.XP.rotationDegrees(tilt.forwardLean() + tilt.pitchTilt()));
        poseStack.translate(0, -0.75, 0);
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        BroomRiderRenderer.BroomRideData tilt = BroomRiderRenderer.getRide(state);
        if (tilt == null) return;

        event.getPoseStack().popPose();
    }
}
