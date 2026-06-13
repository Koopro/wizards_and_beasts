package at.koopro.wizardsandbeasts.network.bestiary.niffler;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import at.koopro.wizardsandbeasts.network.bestiary.niffler.NifflerCarrySyncS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkNiffler {

    private ModNetworkNiffler() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                NifflerCarrySyncS2CPayload.TYPE,
                NifflerCarrySyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleNifflerCarrySync);
    }
}
