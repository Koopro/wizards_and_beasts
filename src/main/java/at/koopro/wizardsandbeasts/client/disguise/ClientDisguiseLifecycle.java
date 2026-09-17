package at.koopro.wizardsandbeasts.client.disguise;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Drops the client's disguise cache on disconnect.
 *
 * <p>A disguise is server-owned: it is keyed by player UUID and only the server that set it will ever
 * clear it. Carrying the map into the next world means the next server — or a single-player world —
 * inherits an appearance override for a UUID it knows nothing about, and nothing there would ever
 * send the packet that removes it.
 *
 * <p>{@code ClientDisguiseState.clear()} existed and was documented as doing exactly this throughout
 * the Polyjuice version of the system, and had no callers. This class is the caller.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientDisguiseLifecycle {

    private ClientDisguiseLifecycle() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDisguiseState.clear();
        // The renderer's profile and skin-lookup caches go too. They are keyed by target UUID and a
        // stale entry would be a face fetched for one server showing up on the next.
        DisguiseRenderHandler.forgetLookups();
    }
}
