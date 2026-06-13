package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import at.koopro.wizardsandbeasts.network.azkaban.AzkabanTrespasserSyncPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkAzkaban {

    private ModNetworkAzkaban() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                AzkabanTrespasserSyncPayload.TYPE,
                AzkabanTrespasserSyncPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleAzkabanTrespasserSync);
    }
}
