package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkType {

    private ModNetworkType() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                HeritageDataSyncS2CPacket.TYPE,
                HeritageDataSyncS2CPacket.STREAM_CODEC,
                HeritageDataSyncS2CPacket::handleClient);

        registrar.playToServer(
                HeritageSelectC2SPacket.TYPE,
                HeritageSelectC2SPacket.STREAM_CODEC,
                HeritageSelectC2SPacket::handle);

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
