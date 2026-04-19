package at.koopro.neo.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkMap {

    private ModNetworkMap() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                MapOpenS2CPacket.TYPE,
                MapOpenS2CPacket.STREAM_CODEC,
                MapOpenS2CPacket::handleClient);

        registrar.playToClient(
                MapSyncPacket.TYPE,
                MapSyncPacket.STREAM_CODEC,
                MapSyncPacket::handleClient);

        registrar.playToServer(
                MapCloseC2SPacket.TYPE,
                MapCloseC2SPacket.STREAM_CODEC,
                MapCloseC2SPacket::handleServer);
    }
}
