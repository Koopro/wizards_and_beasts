package at.koopro.wizardsandbeasts.client.floo;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The green a wizard takes on while they are standing in Floo fire.
 *
 * <p>Same trick as {@code GillyweedTintLayer}, and for the same reason: the player render state in
 * 1.21.11 carries no colour field, so the only way to tint a player is to resubmit the model once
 * more in translucent colour over the real one.
 *
 * <h2>What it keys on</h2>
 * <p>{@link ModEffects#FLOO_PROTECTED}, which is applied every tick anything alive overlaps the
 * flames and expires two thirds of a second later. That makes the tint exactly co-extensive with
 * being <em>in</em> the fire — it comes up as you step in, it holds through the departure windup,
 * and it is gone within a moment of stepping out. No packet and no new state: the effect is already
 * replicated to clients, and the layer reads it off the entity.
 *
 * <h2>Deviation from the brief</h2>
 * <p>The brief asked for the tint on worn <em>leather</em>. This tints the wearer instead. Tinting
 * one armour material would mean hooking the humanoid armour layer and reasoning about dye
 * components, for a result most players would never see — Floo travel does not require armour, and a
 * wizard in robes would get no feedback at all. Greening the person is the effect the brief is
 * actually describing, and it works on everybody who steps into a grate.
 */
public class FlooFlameTintLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /**
     * Emerald at about a fifth opacity.
     *
     * <p>Fainter than it wants to be on purpose. The player standing in the fire already has a
     * full-screen green wash of their own ({@code FlooHearthOverlay}); this is what everyone
     * <em>else</em> in the room sees, and at full strength it turned a wizard into a green cut-out.
     */
    private static final int TINT_ARGB = 0x3321B342;

    public FlooFlameTintLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState renderState, float yRot, float xRot) {
        if (renderState.isSpectator || !inFlooFire(renderState)) {
            return;
        }
        collector.submitModel(
                getParentModel(),
                renderState,
                poseStack,
                RenderTypes.entityTranslucent(renderState.skin.body().texturePath()),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                TINT_ARGB,
                null);
    }

    /** Looks the rendered player up by entity id, so every traveller in the room is tinted. */
    private static boolean inFlooFire(AvatarRenderState renderState) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        Entity entity = mc.level.getEntity(renderState.id);
        return entity instanceof LivingEntity living && living.hasEffect(ModEffects.FLOO_PROTECTED);
    }
}
