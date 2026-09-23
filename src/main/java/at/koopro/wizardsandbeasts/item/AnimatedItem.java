package at.koopro.wizardsandbeasts.item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.world.item.Item;
import org.jspecify.annotations.NullMarked;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * An item GeckoLib draws in hand: a model with a looping {@code idle}, from assets named after the
 * item's own id.
 *
 * <p>GeckoLib only renders an item that <em>is</em> a {@link GeoItem}, and the items worth animating
 * — the Time-Turner, the Snitch, the brooms, the Monster Book — already extend a dozen different
 * classes, several of them plain {@link Item}. So this is an interface rather than a base class:
 * {@code implements AnimatedItem} is the whole change to an item, and the three things GeoItem asks
 * for are answered here once.
 *
 * <ul>
 *   <li>The instance cache lives in a map keyed by item, because an interface cannot hold a field.
 *       Items are registry singletons, so one entry each for the life of the game.</li>
 *   <li>Assets follow the item id: {@code geckolib/models/item/<id>.geo.json},
 *       {@code geckolib/animations/item/<id>.animation.json} and
 *       {@code textures/item/model/<id>.png} — the texture sits apart because
 *       {@code textures/item/<id>.png} is the flat icon the inventory shows.</li>
 *   <li>One controller loops {@code idle}. An item that wants more overrides
 *       {@link #registerControllers}.</li>
 * </ul>
 *
 * <p>In slots the item still shows its flat icon; the model is only for hand, see
 * {@code ModModelProvider.iconInSlotModelInHand}. Written by {@code tools/item_geo.py}.
 */
@NullMarked
public interface AnimatedItem extends GeoItem {

    RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    @Override
    default AnimatableInstanceCache getAnimatableInstanceCache() {
        return Caches.BY_ITEM.computeIfAbsent((Item) this, item -> GeckoLibUtil.createInstanceCache(this));
    }

    @Override
    default void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AnimatedItem>("idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    default void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(GeoItemRenderers.lazy("at.koopro.wizardsandbeasts.client.item.AnimatedItemRenderer",
                new Class<?>[] {Item.class}, new Object[] {(Item) this}));
    }

    /** Holder for the per-item caches; an interface cannot declare a mutable field of its own. */
    final class Caches {
        private static final Map<Item, AnimatableInstanceCache> BY_ITEM = new ConcurrentHashMap<>();

        private Caches() {
        }
    }
}
