package at.koopro.wizardsandbeasts.equipment.curios;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosSlotTypes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Works around a Curios {@code 14.0.0+1.21.11} bug that empties every player's slots on {@code /reload}.
 *
 * <p><b>The bug.</b> Curios builds a <em>new</em> {@code CuriosSlotResources} for each datapack reload
 * ({@code AddServerReloadListenersEvent}); its {@code apply} only stores the JSON as pending data, and
 * the one call that turns pending data into slots, {@code populateData()}, is made on
 * {@code ServerAboutToStartEvent} — once per server, never after a reload. So after any runtime
 * reload the slot registry is empty, and Curios' own {@code OnDatapackSyncEvent} handler then rebuilds
 * every online player's inventory from that empty registry: all slots gone, worn pieces evicted.
 * This mod triggers reloads itself (admin profile/preset application), so it is not a corner case.
 *
 * <p><b>The workaround.</b> At highest priority on the same sync event — after the reload has
 * finished, before Curios rebuilds inventories — populate the new registry if, and only if, it is
 * still empty. {@code populateData} consumes its pending data, so calling it on an already-populated
 * registry would wipe it; the emptiness check is what keeps this a no-op on every normal join and on
 * startup. Reached by reflection because the class is Curios-internal; if Curios renames it the guard
 * logs once and stands down, and the next Curios release should make it unnecessary — delete this
 * class when the upstream fix lands.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class CuriosReloadGuard {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCES = "top.theillusivec4.curios.common.data.CuriosSlotResources";

    private static boolean unavailable;

    private CuriosReloadGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (unavailable || !CuriosSlotTypes.getSlotTypes(false).isEmpty()) {
            return;
        }
        try {
            Class<?> type = Class.forName(RESOURCES);
            Field server = type.getField("SERVER");
            Object resources = server.get(null);
            if (resources == null) {
                return;
            }
            Method populate = type.getMethod("populateData");
            populate.invoke(resources);
            LOGGER.info("[Equipment] Curios slot registry was empty after a datapack reload; repopulated ({} slots)",
                    CuriosSlotTypes.getSlotTypes(false).size());
        } catch (ReflectiveOperationException | LinkageError e) {
            unavailable = true;
            LOGGER.warn("[Equipment] Curios reload guard unavailable ({}); a /reload may empty Curios slots until restart",
                    e.toString());
        }
    }
}
