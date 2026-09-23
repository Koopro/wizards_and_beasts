package at.koopro.wizardsandbeasts.client.item;

import at.koopro.wizardsandbeasts.item.AnimatedItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The one renderer every {@link AnimatedItem} shares. Model and animation are found by the item's
 * id; the texture is {@code textures/item/model/<id>.png}, because the default path is the flat
 * inventory icon.
 *
 * <p>Built lazily, through {@code GeoItemRenderers.lazy}, after the item registry is frozen — which
 * is what lets it look its own id up rather than being told it.
 */
public class AnimatedItemRenderer<T extends Item & AnimatedItem> extends GeoItemRenderer<T> {

    public AnimatedItemRenderer(Item item) {
        super(model(BuiltInRegistries.ITEM.getKey(item)));
    }

    /** Hands the stack's chosen clip to the controller — see {@link AnimatedItem#clipFor}. */
    @Override
    public void addRenderData(T animatable, @Nullable RenderData renderData, GeoRenderState renderState,
                              float partialTick) {
        if (renderData != null) {
            renderState.addGeckolibData(AnimatedItem.CLIP, animatable.clipFor(renderData.itemStack()));
        }
    }

    private static <T extends Item & AnimatedItem> DefaultedItemGeoModel<T> model(Identifier id) {
        return new DefaultedItemGeoModel<T>(id)
                .withAltTexture(Identifier.fromNamespaceAndPath(id.getNamespace(), "model/" + id.getPath()));
    }
}
