package at.koopro.wizardsandbeasts.client.deluminator;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.deluminator.DeluminatorItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class DeluminatorRenderer extends GeoItemRenderer<DeluminatorItem> {

    public DeluminatorRenderer() {
        super(new DefaultedItemGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "deluminator")));
    }
}
