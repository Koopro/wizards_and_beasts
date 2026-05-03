package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.WandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public class WandRenderer extends GeoItemRenderer<WandItem> {

    public WandRenderer() {
        super(new WandModel());
    }

    @Override
    public void preRenderPass(RenderPassInfo<GeoRenderState> info, SubmitNodeCollector collector) {
        super.preRenderPass(info, collector);
        Minecraft mc = Minecraft.getInstance();
        GeoRenderState state = info.renderState();
        ItemDisplayContext perspective = state.getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        if (perspective == null) {
            return;
        }
        // Only cache the tip for the item pass that matches the active camera (FP vs TP).
        boolean cameraFirstPerson = mc.options.getCameraType().isFirstPerson();
        boolean contextFirstPerson = perspective.firstPerson();
        if (cameraFirstPerson != contextFirstPerson) {
            return;
        }
        info.addBonePositionListener(WandTipWorldCache.WAND_TIP_BONE, (worldPos, modelPos, preRenderPos) -> {
            if (worldPos != null) {
                WandTipWorldCache.setWorldTip(worldPos);
            }
        });
    }

    private static final class WandModel extends GeoModel<WandItem> {
        @Override
        public Identifier getModelResource(GeoRenderState renderState) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "item/wand");
        }

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/item/wand.png");
        }

        @Override
        public Identifier getAnimationResource(WandItem animatable) {
            return null;
        }
    }
}
