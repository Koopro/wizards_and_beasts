package at.koopro.wizardsandbeasts.client.petrify;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.petrify.state.ClientPetrifyState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Draws the player's own body model with a solid stone texture while petrified — the same
 * "re-render the parent model through a different RenderType" technique {@link at.koopro.wizardsandbeasts.client.form.FormModelRenderer}
 * uses for Animagus forms, applied to every tracked player (not just the local one), since a stone
 * statue must be visible to everyone in the room. Registered in {@code ClientSetup.registerLayers}
 * alongside {@code NifflerPocketLayer}.
 *
 * <p>Needs a real {@code textures/entity/petrify/stone_statue.png} asset — ships as a placeholder path
 * (see the many {@code "_comment": "PLACEHOLDER box rig"} creature JSONs already in this repo for the
 * same pattern) until art exists.
 */
public class PetrifyStoneLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Identifier STONE_TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/entity/petrify/stone_statue.png");

    public PetrifyStoneLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                        AvatarRenderState renderState, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(renderState.id);
        if (entity == null || !ClientPetrifyState.isPetrified(entity.getUUID())) {
            return;
        }

        RenderType renderType = RenderTypes.entitySolid(STONE_TEXTURE);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            PoseStack tempStack = new PoseStack();
            tempStack.last().pose().set(pose.pose());
            tempStack.last().normal().set(pose.normal());
            getParentModel().renderToBuffer(tempStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        });
    }
}
