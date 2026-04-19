package at.koopro.neo.client.wand;

import at.koopro.neo.Neo;
import at.koopro.neo.item.WandItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class WandRenderer extends GeoItemRenderer<WandItem> {

    public WandRenderer() {
        super(new WandModel());
    }

    private static final class WandModel extends GeoModel<WandItem> {
        @Override
        public Identifier getModelResource(GeoRenderState renderState) {
            return Identifier.fromNamespaceAndPath(Neo.MODID, "item/wand");
        }

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            return Identifier.fromNamespaceAndPath(Neo.MODID, "textures/item/wand.png");
        }

        @Override
        public Identifier getAnimationResource(WandItem animatable) {
            return null;
        }
    }
}
