package at.koopro.wizardsandbeasts.mixin.client;

import at.koopro.wizardsandbeasts.client.debug.ModelDebugPartTransforms;
import at.koopro.wizardsandbeasts.client.petrify.PetrifyRenderHandler;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects at the TAIL of {@link PlayerModel#setupAnim(AvatarRenderState)} — the one point where the
 * vanilla player pose is fully computed and not yet consumed. {@code PlayerModel.setupAnim} calls
 * {@code super.setupAnim} as its last statement, so TAIL is genuinely after every limb rotation
 * {@link net.minecraft.client.model.HumanoidModel} sets, including the passenger crouch.
 *
 * <p><b>Why a mixin and not an event.</b> NeoForge exposes no hook between a humanoid model finishing
 * its animation and the renderer submitting it. {@code RenderLivingEvent.Pre} fires before
 * {@code setupAnim} runs, so anything written there is immediately overwritten;
 * {@code RegisterRenderStateModifiersEvent} sees the render state but never the {@code ModelPart}s,
 * which is where a pose lives. Overriding pose therefore requires this injection point. This mixin
 * predates the petrification work, which extends it rather than adding a second hook onto the same
 * call.
 *
 * <p><b>No logic here.</b> Every case delegates to an ordinary handler class, which is also where the
 * module gates live, so the gating stays testable.
 *
 * <p><b>If this silently stops firing:</b> petrified players animate normally under their stone skin
 * instead of standing frozen, and the {@code /wandb} model debug editor's part sliders do nothing.
 * Nothing crashes and nothing else regresses. The mixin config sets {@code injectors.defaultRequire: 1},
 * so a failure to resolve is a hard crash at load rather than a silent miss.
 */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {

    @Inject(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
        at = @At("TAIL")
    )
    private void WizardsAndBeastsMod$applyPartTransforms(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        // The debug editor runs last because it is additive on top of whatever survived, which is
        // what makes it useful for tuning the cases above it.
        PetrifyRenderHandler.applyFrozenPose(model, state);
        ModelDebugPartTransforms.apply(model);
    }
}
