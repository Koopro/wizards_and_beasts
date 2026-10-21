package at.koopro.wizardsandbeasts.client.equipment.curios;

import at.koopro.wizardsandbeasts.item.armor.WizardArmorItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jspecify.annotations.NullMarked;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Binds {@link WornGeoCurioRenderer} to every wardrobe piece worn as clothing.
 *
 * <p>Driven by {@link WizardArmorItem.Wear#CURIO}, so a new robe class picks the renderer up by
 * existing; nothing here names an item.
 *
 * <p>Runs on {@code AddLayers} at highest priority: Curios instantiates registered renderers from its
 * own {@code AddLayers} listener, and that event is also where every resource reload rebuilds them,
 * so registering earlier in the same event is the one timing that is right on first load and on F3+T.
 */
@NullMarked
public final class CuriosClientRegistration {

    private static final WornGeoCurioRenderer WORN_GEO = new WornGeoCurioRenderer();

    private CuriosClientRegistration() {}

    public static void registerRenderers(EntityRenderersEvent.AddLayers event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof WizardArmorItem piece && piece.wear() == WizardArmorItem.Wear.CURIO) {
                ICurioRenderer.register(item, () -> WORN_GEO);
            }
        }
    }
}
