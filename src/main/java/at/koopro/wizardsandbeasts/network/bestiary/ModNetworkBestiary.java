package at.koopro.wizardsandbeasts.network.bestiary;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import at.koopro.wizardsandbeasts.network.bestiary.BestiaryDataSyncPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkBestiary {
    private ModNetworkBestiary() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(BestiaryDataSyncPayload.TYPE, BestiaryDataSyncPayload.STREAM_CODEC, ClientPayloadHandlers::handleBestiaryDataSync);
    }
}
