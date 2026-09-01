package at.koopro.wizardsandbeasts.client.gillyweed;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The green cast on the skin of somebody who has grown gills.
 *
 * <p>The player model resubmitted once more in translucent green over the top of the real one. That
 * is the only way to tint a player in 1.21.11 — the render state carries no colour field, so there is
 * nothing a {@code RegisterRenderStateModifiersEvent} modifier could write to. Deliberately faint:
 * this should read as a wizard who has changed rather than as a wizard who has been painted.
 *
 * <p>Drawn for every gilled player, not only the local one, and read straight off the effect the
 * client already replicates — so the whole cosmetic costs no packet and no state.
 */
public class GillyweedTintLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /** Lake green at about a fifth opacity. The alpha is doing most of the work here. */
    private static final int TINT_ARGB = 0x334E8C5A;

    public GillyweedTintLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState renderState, float yRot, float xRot) {
        if (renderState.isSpectator || !isGilled(renderState)) {
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

    /** Looks the rendered player up by entity id, so other divers are tinted too. */
    private static boolean isGilled(AvatarRenderState renderState) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        Entity entity = mc.level.getEntity(renderState.id);
        return entity instanceof LivingEntity living && living.hasEffect(ModEffects.GILLS);
    }
}
