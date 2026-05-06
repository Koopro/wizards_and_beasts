package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkBestiary {
    private ModNetworkBestiary() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(BestiaryDataSyncPayload.TYPE, BestiaryDataSyncPayload.STREAM_CODEC, BestiaryDataSyncPayload::handleClient);
    }
}
