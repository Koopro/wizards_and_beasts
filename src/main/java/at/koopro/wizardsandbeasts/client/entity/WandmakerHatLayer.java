package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModVillager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerData;

/**
 * Puts {@link WandmakerHatModel} on the head of a wandmaker villager.
 *
 * <p>Wired onto the vanilla villager renderer from {@code ClientSetup#registerLayers}, so it runs
 * for every villager and has to filter itself: profession first, then the two cases vanilla's own
 * {@code VillagerProfessionLayer} also refuses — an invisible villager, and a baby, who has no
 * profession to advertise and would wear an adult-sized hat at half scale anyway.
 *
 * <p>The pose is walked to the head the way {@code CustomHeadLayer} does it — root, then head —
 * which picks up the head's look direction and the unhappy-villager head shake for free.
 */
public class WandmakerHatLayer extends RenderLayer<VillagerRenderState, VillagerModel> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/entity/villager/wandmaker_hat.png");

    private final WandmakerHatModel hat = new WandmakerHatModel();

    public WandmakerHatLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       VillagerRenderState renderState, float yRot, float xRot) {
        if (renderState.isInvisible || renderState.isBaby) return;
        if (!ModuleManager.isEnabled(Module.WANDS)) return;

        VillagerData data = renderState.getVillagerData();
        if (data == null || !data.profession().is(ModVillager.WANDMAKER.getKey())) return;

        poseStack.pushPose();
        VillagerModel model = getParentModel();
        model.root().translateAndRotate(poseStack);
        model.translateToHead(poseStack);
        collector.submitModelPart(hat.root(), poseStack, RenderTypes.entityCutoutNoCull(TEXTURE),
                packedLight, OverlayTexture.NO_OVERLAY, null, false, false, -1, null,
                renderState.outlineColor);
        poseStack.popPose();
    }
}
