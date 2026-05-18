package at.koopro.wizardsandbeasts.bestiary.network;

import at.koopro.wizardsandbeasts.bestiary.network.BestiaryDataSyncPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkBestiary {
    private ModNetworkBestiary() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(BestiaryDataSyncPayload.TYPE, BestiaryDataSyncPayload.STREAM_CODEC, BestiaryDataSyncPayload::handleClient);
    }
}
