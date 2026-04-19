package at.koopro.neo.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkVault {

    private ModNetworkVault() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                GringottsOpenS2CPacket.TYPE,
                GringottsOpenS2CPacket.STREAM_CODEC,
                GringottsOpenS2CPacket::handleClient);

        registrar.playToServer(
                VaultActionC2SPacket.TYPE,
                VaultActionC2SPacket.STREAM_CODEC,
                VaultActionC2SPacket::handle);

        registrar.playToClient(
                VaultSyncS2CPacket.TYPE,
                VaultSyncS2CPacket.STREAM_CODEC,
                VaultSyncS2CPacket::handleClient);
    }
}
