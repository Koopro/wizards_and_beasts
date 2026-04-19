package at.koopro.neo.client.map;

import at.koopro.neo.Neo;
import at.koopro.neo.item.MaraudersMapItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MaraudersMapRenderer extends GeoItemRenderer<MaraudersMapItem> {

    public MaraudersMapRenderer() {
        super(new DefaultedItemGeoModel<>(
                Identifier.fromNamespaceAndPath(Neo.MODID, "marauders_map")));
    }
}
