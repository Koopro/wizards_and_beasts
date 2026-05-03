package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkType {

    private ModNetworkType() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                TypeDataSyncS2CPacket.TYPE,
                TypeDataSyncS2CPacket.STREAM_CODEC,
                TypeDataSyncS2CPacket::handleClient);

        registrar.playToServer(
                TypeSelectC2SPacket.TYPE,
                TypeSelectC2SPacket.STREAM_CODEC,
                TypeSelectC2SPacket::handle);

        registrar.playToServer(
                ProfessionUnlockC2SPacket.TYPE,
                ProfessionUnlockC2SPacket.STREAM_CODEC,
                ProfessionUnlockC2SPacket::handle);

        registrar.playToServer(
                ProfessionSelectC2SPacket.TYPE,
                ProfessionSelectC2SPacket.STREAM_CODEC,
                ProfessionSelectC2SPacket::handle);
    }
}
