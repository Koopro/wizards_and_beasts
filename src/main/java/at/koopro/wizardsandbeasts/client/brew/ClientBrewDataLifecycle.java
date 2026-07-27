package at.koopro.wizardsandbeasts.client.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Drops the client's brew data on disconnect.
 *
 * <p>It is server-owned content. Carrying one server's brews into the next would show a recipe viewer
 * entries that do not exist there, and the stale set would survive until that server happened to send its
 * own — which on a server without this mod's data is never.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientBrewDataLifecycle {

    private ClientBrewDataLifecycle() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBrewData.clear();
    }
}
