package at.koopro.wizardsandbeasts.network.trunk;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import at.koopro.wizardsandbeasts.network.trunk.PocketConfigC2SPayload;
import at.koopro.wizardsandbeasts.network.trunk.PocketStatusS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkPocket {
    private ModNetworkPocket() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                PocketStatusS2CPayload.TYPE,
                PocketStatusS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handlePocketStatus);
        registrar.playToServer(
                PocketConfigC2SPayload.TYPE,
                PocketConfigC2SPayload.STREAM_CODEC,
                PocketConfigC2SPayload::handleServer);
    }
}
