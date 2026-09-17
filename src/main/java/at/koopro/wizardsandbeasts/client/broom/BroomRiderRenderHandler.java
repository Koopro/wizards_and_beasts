package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.broom.BroomGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Banks and leans the rider's whole body with the broom. The limb pose is a separate concern and lives in
 * {@link BroomRiderPoseHandler}; this only rotates the pose stack about the seat.
 *
 * <h2>In the broom's frame, with the broom's signs</h2>
 * {@link RenderLivingEvent.Pre} fires before {@code LivingEntityRenderer.setupRotations}, so the pose stack
 * here is still world-aligned. The rotations used to go straight onto it, which is only the rider's frame
 * while they face south: facing east or west, "lean forward" tipped them sideways. They are now taken about
 * the rider's own axes — turn into the body's heading, rotate, turn back — and the body's heading is the
 * broom's, because {@code BroomEntity.positionRider} locks it.
 *
 * <p>The signs are the broom's, carried through GeckoLib's frame. The {@code broom_body} bone takes
 * {@code ZP(roll)·XP(lean)} inside a model already turned by {@code 180 - yaw} ({@code GeoEntityRenderer
 * .applyRotations}, {@code RenderUtil}); expressed in the rider's forward-is-{@code +z} frame that half-turn
 * flips both axes, so the rider takes {@code ZP(-roll)·XP(-lean)}. Unflipped, the rider leaned back while
 * the broom tucked its nose down and banked against the broom in every turn.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public class BroomRiderRenderHandler {

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        BroomRiderRenderer.BroomRideData ride = BroomRiderRenderer.getRide(state);
        if (ride == null) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // The hover bob first, so the rider rises and falls with the model under them.
        poseStack.translate(0.0, ride.bob(), 0.0);
        // About the hip: a bank reads as leaning into a turn instead of swinging from the ankles.
        poseStack.translate(0.0, BroomGeometry.HIP_HEIGHT, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.bodyRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-ride.roll()));
        poseStack.mulPose(Axis.XP.rotationDegrees(-(ride.forwardLean() + ride.pitchTilt())));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.bodyRot));
        // Side-saddle last, so it turns the rider on a broom that has already banked.
        if (ride.yawOffset() != 0.0f) {
            poseStack.mulPose(Axis.YP.rotationDegrees(ride.yawOffset()));
        }
        poseStack.translate(0.0, -BroomGeometry.HIP_HEIGHT, 0.0);
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        if (BroomRiderRenderer.getRide(event.getRenderState()) == null) return;

        event.getPoseStack().popPose();
    }
}
