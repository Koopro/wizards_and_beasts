package at.koopro.wizardsandbeasts.client.armor;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Shared base for the wardrobe's armour renderers — the counterpart of {@code GeoRendererHelper} for
 * worn gear, kept as a class rather than a factory method because {@code GeoArmorRenderer} is bound
 * by class name from the item (see {@code WizardArmorItem#rendererClassName}).
 *
 * <p>The {@code R} type parameter cannot be filled in with a concrete class. GeckoLib declares it as
 * {@code HumanoidRenderState & GeoRenderState}, an intersection satisfied only at runtime — its
 * mixin casts the vanilla render state to it — so every renderer below has to keep R open too.
 *
 * <p>Nothing here needs registering in {@code ClientSetup}. GeckoLib's {@code HumanoidArmorLayer}
 * mixin asks each worn item for its renderer as it draws.
 */
public class WizardArmorRenderer<T extends Item & GeoItem, R extends HumanoidRenderState & GeoRenderState>
        extends GeoArmorRenderer<T, R> {

    /** Defaulted paths off one set name — see {@link WizardArmorGeoModel} for the layout. */
    public WizardArmorRenderer(String assetName) {
        super(new WizardArmorGeoModel<>(assetName));
    }

    /** For a set whose texture is redirected off the shared model, as the mask castings are. */
    protected WizardArmorRenderer(GeoModel<T> model) {
        super(model);
    }
}
