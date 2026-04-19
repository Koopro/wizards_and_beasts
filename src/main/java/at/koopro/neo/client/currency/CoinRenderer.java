package at.koopro.neo.client.currency;

import at.koopro.neo.Neo;
import at.koopro.neo.item.currency.CoinItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class CoinRenderer extends GeoItemRenderer<CoinItem> {

    public CoinRenderer(String coinName) {
        super(new DefaultedItemGeoModel<>(
                Identifier.fromNamespaceAndPath(Neo.MODID, coinName)));
    }
}
