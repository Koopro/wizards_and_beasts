package at.koopro.wizardsandbeasts.network.petrify;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkPetrify {

    private ModNetworkPetrify() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                PetrifiedStateSyncS2CPayload.TYPE,
                PetrifiedStateSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handlePetrifiedStateSync);
    }
}
