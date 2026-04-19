package at.koopro.neo.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkWizType {

    private ModNetworkWizType() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                TypeDataSyncS2CPacket.TYPE,
                TypeDataSyncS2CPacket.STREAM_CODEC,
                TypeDataSyncS2CPacket::handleClient);

        registrar.playToServer(
                TypeSelectC2SPacket.TYPE,
                TypeSelectC2SPacket.STREAM_CODEC,
                TypeSelectC2SPacket::handle);
    }
}
