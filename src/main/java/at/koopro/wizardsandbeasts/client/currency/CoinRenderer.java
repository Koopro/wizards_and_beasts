package at.koopro.wizardsandbeasts.client.currency;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.currency.CoinItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class CoinRenderer extends GeoItemRenderer<CoinItem> {

    public CoinRenderer(String coinName) {
        super(new DefaultedItemGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, coinName)));
    }
}
