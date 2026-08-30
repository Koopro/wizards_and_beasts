package at.koopro.wizardsandbeasts.item;

import at.koopro.wizardsandbeasts.util.ClientClassBridge;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Lazily builds the GeckoLib renderer for an item without naming the renderer class.
 *
 * <p>Renderers are client-only, so the item — which is common code — can only reach one through
 * {@link ClientClassBridge}. Three items wrote out the same anonymous {@code GeoRenderProvider}
 * with the same null-check-and-cache field to do that. This is that provider, once.
 *
 * <p>Construction is deferred rather than eager because {@code createGeoRenderer} is called during
 * item construction, long before the client is ready to build a renderer.
 */
@NullMarked
public final class GeoItemRenderers {

    private GeoItemRenderers() {
    }

    /** Provider for a renderer with a no-argument constructor. */
    public static GeoRenderProvider lazy(String rendererClass) {
        return lazy(rendererClass, new Class<?>[0], new Object[0]);
    }

    /** Provider for a renderer whose constructor takes arguments — a coin needs its denomination. */
    public static GeoRenderProvider lazy(String rendererClass, Class<?>[] parameterTypes, Object[] args) {
        return new GeoRenderProvider() {
            private @Nullable GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = ClientClassBridge.instantiate(
                            rendererClass, GeoItemRenderer.class, parameterTypes, args);
                }
                return this.renderer;
            }
        };
    }
}
