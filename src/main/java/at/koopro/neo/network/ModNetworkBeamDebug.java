package at.koopro.neo.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkBeamDebug {

    private ModNetworkBeamDebug() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                BeamDebugOpenS2CPacket.TYPE,
                BeamDebugOpenS2CPacket.STREAM_CODEC,
                BeamDebugOpenS2CPacket::handleClient);
    }
}
