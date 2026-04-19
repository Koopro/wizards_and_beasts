package at.koopro.neo.item.currency;

import at.koopro.neo.client.currency.CoinRenderer;
import at.koopro.neo.item.GeoItemBase;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.function.Consumer;

public class CoinItem extends GeoItemBase {

    private final String coinName;

    public CoinItem(Properties properties, String coinName) {
        super(properties);
        this.coinName = coinName;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private CoinRenderer renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null)
                    this.renderer = new CoinRenderer(coinName);
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CoinItem>(
                "coin_controller", 0,
                state -> PlayState.STOP));
    }
}
