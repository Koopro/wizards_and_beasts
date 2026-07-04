package at.koopro.wizardsandbeasts.network.handbook;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkHandbook {
    private ModNetworkHandbook() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                SyncHandbookPayload.TYPE,
                SyncHandbookPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleSyncHandbook);
    }
}
