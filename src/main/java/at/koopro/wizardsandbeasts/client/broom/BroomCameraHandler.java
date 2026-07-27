package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Camera work for broom flight: the view rolls with a banked turn, and the third-person camera eases
 * back as speed builds so a fast broom does not fill the screen.
 *
 * <p><b>Events only, no mixin.</b> {@link ViewportEvent.ComputeCameraAngles} owns the camera angles
 * and {@link CalculateDetachedCameraDistanceEvent} owns the third-person distance — the latter fires
 * before the block-collision raycast, so a pulled-back camera still cannot clip through a wall. The
 * brief for this work expected no event to cover distance; NeoForge 21.11 has one, so the mixin that
 * would otherwise have been needed is not.
 *
 * <p><b>Conservative by construction.</b> Only roll is touched. Yaw and pitch are left exactly as the
 * player aimed them, so nothing here can fight the mouse. Roll and pull-back are both smoothed toward
 * a target each frame and both target zero whenever the player is not on a broom, which is what makes
 * dismounting, dying and switching {@link Module#BROOM_FLIGHT} off mid-flight ease back to the vanilla
 * camera rather than snap. The arithmetic lives in {@link BroomCameraMath} so it can be tested.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
@NullMarked
public final class BroomCameraHandler {

    private static float smoothedRoll;
    private static float smoothedPullBack;

    private BroomCameraHandler() {}

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        BroomEntity broom = ridingBroom(event.getCamera());
        float target = 0.0F;
        if (broom != null) {
            float roll = Mth.lerp((float) event.getPartialTick(), broom.getPrevRollTilt(), broom.getRollTilt());
            target = BroomCameraMath.rollTarget(roll);
        }
        smoothedRoll = BroomCameraMath.advance(smoothedRoll, target);
        if (smoothedRoll == 0.0F) {
            return;
        }
        event.setRoll(event.getRoll() + smoothedRoll);
    }

    @SubscribeEvent
    public static void onCalculateCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        BroomEntity broom = ridingBroom(event.getCamera());
        float target = broom != null ? BroomCameraMath.pullBackTarget(broom.getCurrentSpeed()) : 0.0F;
        smoothedPullBack = BroomCameraMath.advance(smoothedPullBack, target);
        if (smoothedPullBack == 0.0F) {
            return;
        }
        event.setDistance(event.getDistance() + smoothedPullBack);
    }

    /**
     * The broom the camera's own entity is riding, or null. Deliberately keyed off
     * {@link Camera#entity()} rather than {@code Minecraft.player}: spectating another player on a
     * broom should give that player's camera, and a camera attached to anything else should give
     * nothing. {@code entity()} is null before the first {@code setup} and after a level unload.
     */
    private static @Nullable BroomEntity ridingBroom(Camera camera) {
        if (!ModuleManager.isEnabled(Module.BROOM_FLIGHT)) {
            return null;
        }
        Entity viewer = camera.entity();
        return viewer != null && viewer.getVehicle() instanceof BroomEntity broom ? broom : null;
    }
}
