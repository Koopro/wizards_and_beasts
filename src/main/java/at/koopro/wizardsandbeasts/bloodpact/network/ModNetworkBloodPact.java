package at.koopro.wizardsandbeasts.bloodpact.network;

import at.koopro.wizardsandbeasts.bloodpact.network.SBreakPactPayload;
import at.koopro.wizardsandbeasts.bloodpact.network.SUpdateVialPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkBloodPact {

    private ModNetworkBloodPact() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                SBreakPactPayload.TYPE,
                SBreakPactPayload.STREAM_CODEC,
                SBreakPactPayload::handleClient);
        registrar.playToClient(
                SUpdateVialPayload.TYPE,
                SUpdateVialPayload.STREAM_CODEC,
                SUpdateVialPayload::handleClient);
    }
}
