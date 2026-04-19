package at.koopro.neo.client.deluminator;

import at.koopro.neo.Neo;
import at.koopro.neo.item.wizarding.DeluminatorItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class DeluminatorRenderer extends GeoItemRenderer<DeluminatorItem> {

    public DeluminatorRenderer() {
        super(new DefaultedItemGeoModel<>(
                Identifier.fromNamespaceAndPath(Neo.MODID, "deluminator")));
    }
}
