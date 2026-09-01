package at.koopro.wizardsandbeasts.client.ministry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.ministry.state.ClientMinistryRecordState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Drops the cached Ministry record on disconnect.
 *
 * <p>Matters more here than for most caches: a record is per-world, and a server that does not run this mod
 * — or runs it with the Ministry module off — never sends one. Without this, the Character Sheet would keep
 * showing the last world's convictions and outstanding fine indefinitely.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientMinistryLifecycle {

    private ClientMinistryLifecycle() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMinistryRecordState.clear();
    }
}
