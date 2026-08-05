package at.koopro.wizardsandbeasts.network.animagus;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ModNetworkAnimagus {

    private ModNetworkAnimagus() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                SyncAnimagusFormsPayload.TYPE,
                SyncAnimagusFormsPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleSyncAnimagusForms);
    }
}
