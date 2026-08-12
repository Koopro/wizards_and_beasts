package at.koopro.wizardsandbeasts.mixin.client;

import at.koopro.wizardsandbeasts.client.pose.PlayerPoseHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the pose layer's whole-avatar transform.
 *
 * <p>The tail of {@code setupRotations} is the one frame where a whole-body pitch means what it
 * says: the pose stack has just been yawed to the player's facing with its origin at their feet.
 * Vanilla puts its own two whole-body pitches here for the same reason — elytra flight and swimming
 * — so this sits alongside them rather than inventing a site.
 *
 * <p>Body only. The limb half of a pose cannot be computed here, because {@code setupRotations} runs
 * before {@code setupAnim} and vanilla has not posed the model yet; it arrives through
 * {@code PlayerModelMixin} instead.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void wizardsAndBeasts$applyBodyPose(AvatarRenderState state, PoseStack poseStack,
                                                float bodyRot, float scale, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        PlayerPoseHandler.applyBodyTransform(state, poseStack, model);
    }
}
