package at.koopro.wizardsandbeasts.item.currency;

import at.koopro.wizardsandbeasts.item.GeoItemBase;
import at.koopro.wizardsandbeasts.util.ClientClassBridge;
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
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null)
                    this.renderer = ClientClassBridge.instantiate(
                            "at.koopro.wizardsandbeasts.client.currency.CoinRenderer",
                            GeoItemRenderer.class,
                            new Class<?>[] { String.class },
                            new Object[] { coinName });
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
