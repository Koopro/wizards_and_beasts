package at.koopro.wizardsandbeasts.network.bloodpact;

import at.koopro.wizardsandbeasts.network.bloodpact.SBreakPactPayload;
import at.koopro.wizardsandbeasts.network.bloodpact.SUpdateVialPayload;
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
