package at.koopro.wizardsandbeasts.map.client;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.map.item.MaraudersMapItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MaraudersMapRenderer extends GeoItemRenderer<MaraudersMapItem> {

    public MaraudersMapRenderer() {
        super(new DefaultedItemGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "marauders_map")));
    }
}
