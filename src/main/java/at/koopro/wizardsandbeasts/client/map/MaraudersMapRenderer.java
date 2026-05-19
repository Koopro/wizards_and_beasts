package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.map.MaraudersMapItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MaraudersMapRenderer extends GeoItemRenderer<MaraudersMapItem> {

    public MaraudersMapRenderer() {
        super(new DefaultedItemGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "marauders_map")));
    }
}
