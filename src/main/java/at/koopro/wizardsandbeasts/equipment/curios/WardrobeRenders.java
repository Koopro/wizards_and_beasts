package at.koopro.wizardsandbeasts.equipment.curios;

import at.koopro.wizardsandbeasts.equipment.WardrobeLayout;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * The wardrobe's show/hide eye: Curios' per-slot render flag, flipped through the API.
 *
 * <p>Curios keeps the flag on the slot and saves it with the player, but its API offers no way to sync a change (its
 * own toggle packet is internal), so the wardrobe sends its own: {@link #toggle} flips the flag on the server and
 * returns what every tracking client must apply with {@link #apply}.
 */
@NullMarked
public final class WardrobeRenders {

    /** A render flag to apply on a client: whose, which slot, and its new value. */
    public record Change(int entityId, String slotId, int index, boolean shown) {}

    private WardrobeRenders() {}

    /**
     * Flips place {@code placeIndex}'s render flag on {@code player}'s own Curios slot.
     *
     * @return the change to broadcast, or null when the place is out of range, missing, or not toggleable
     */
    public static @Nullable Change toggle(ServerPlayer player, int placeIndex) {
        if (placeIndex < 0 || placeIndex >= WardrobeLayout.PLACES.size()) {
            return null;
        }
        WardrobeLayout.Place place = WardrobeLayout.PLACES.get(placeIndex);
        ICurioStacksHandler handler = handler(player, place.slotId());
        if (handler == null || !handler.canToggleRendering()) {
            return null;
        }
        NonNullList<Boolean> renders = handler.getRenders();
        if (place.index() >= renders.size()) {
            return null;
        }
        boolean shown = !renders.get(place.index());
        renders.set(place.index(), shown);
        return new Change(player.getId(), place.slotId(), place.index(), shown);
    }

    /** Applies a broadcast change to {@code entity}'s Curios copy (client side). */
    public static void apply(Entity entity, String slotId, int index, boolean shown) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        ICurioStacksHandler handler = handler(living, slotId);
        if (handler != null && index >= 0 && index < handler.getRenders().size()) {
            handler.getRenders().set(index, shown);
        }
    }

    private static @Nullable ICurioStacksHandler handler(LivingEntity entity, String slotId) {
        ICuriosItemHandler curios = CuriosApi.getCuriosInventoryOrNull(entity);
        return curios == null ? null : curios.getStacksHandler(slotId).orElse(null);
    }
}
