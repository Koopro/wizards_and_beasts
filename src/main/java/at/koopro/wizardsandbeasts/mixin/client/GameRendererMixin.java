package at.koopro.wizardsandbeasts.mixin.client;

import at.koopro.wizardsandbeasts.client.beam.FirstPersonProjection;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Copies the projection inputs {@code renderLevel} builds into {@link FirstPersonProjection}, so a
 * beam can start on the wand tip the first-person hand pass draws.
 *
 * <p>A mixin because the level FOV, the hand FOV and the view-bob pose are all locals of
 * {@code renderLevel}: {@code getFov} and {@code bobView} are private, and no NeoForge event carries
 * the bob matrix. Every injection hands its argument back unchanged.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    /** The level pass's FOV, after every modifier. */
    @ModifyArg(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;getProjectionMatrix(F)Lorg/joml/Matrix4f;",
                    ordinal = 0))
    private float WizardsAndBeastsMod$captureLevelFov(float fov) {
        FirstPersonProjection.captureLevelFov(fov);
        return fov;
    }

    /** The view-bob pose vanilla multiplies into the level projection. */
    @ModifyArg(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;",
                    ordinal = 0))
    private Matrix4fc WizardsAndBeastsMod$captureViewBob(Matrix4fc bob) {
        FirstPersonProjection.captureViewBob(bob);
        return bob;
    }

    /** The hand pass's FOV, as handed to the 3D HUD projection buffer. */
    @ModifyArg(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CachedPerspectiveProjectionMatrixBuffer;getBuffer(IIF)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",
                    ordinal = 0),
            index = 2)
    private float WizardsAndBeastsMod$captureHandFov(float fov) {
        FirstPersonProjection.captureHandFov(fov);
        return fov;
    }
}
