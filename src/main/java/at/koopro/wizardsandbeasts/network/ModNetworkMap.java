package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkMap {

    private ModNetworkMap() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                MapOpenS2CPacket.TYPE,
                MapOpenS2CPacket.STREAM_CODEC,
                MapOpenS2CPacket::handleClient);

        registrar.playToClient(
                MapSyncS2CPacket.TYPE,
                MapSyncS2CPacket.STREAM_CODEC,
                MapSyncS2CPacket::handleClient);

        registrar.playToServer(
                MapCloseC2SPacket.TYPE,
                MapCloseC2SPacket.STREAM_CODEC,
                MapCloseC2SPacket::handle);
    }
}
