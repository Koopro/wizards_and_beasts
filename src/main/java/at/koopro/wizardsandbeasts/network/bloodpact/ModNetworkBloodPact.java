package at.koopro.wizardsandbeasts.network.bloodpact;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkBloodPact {

    private ModNetworkBloodPact() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                SBreakPactPayload.TYPE,
                SBreakPactPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleSBreakPact);
        registrar.playToClient(
                SUpdateVialPayload.TYPE,
                SUpdateVialPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleSUpdateVial);
    }
}
