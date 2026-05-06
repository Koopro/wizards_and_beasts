package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkPocket {
    private ModNetworkPocket() {
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                PocketStatusS2CPacket.TYPE,
                PocketStatusS2CPacket.STREAM_CODEC,
                PocketStatusS2CPacket::handleClient);
    }
}
