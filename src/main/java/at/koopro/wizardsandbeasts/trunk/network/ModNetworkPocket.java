package at.koopro.wizardsandbeasts.trunk.network;

import at.koopro.wizardsandbeasts.trunk.network.PocketConfigC2SPayload;
import at.koopro.wizardsandbeasts.trunk.network.PocketStatusS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkPocket {
    private ModNetworkPocket() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                PocketStatusS2CPayload.TYPE,
                PocketStatusS2CPayload.STREAM_CODEC,
                PocketStatusS2CPayload::handleClient);
        registrar.playToServer(
                PocketConfigC2SPayload.TYPE,
                PocketConfigC2SPayload.STREAM_CODEC,
                PocketConfigC2SPayload::handleServer);
    }
}
