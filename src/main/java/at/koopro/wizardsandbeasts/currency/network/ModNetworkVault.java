package at.koopro.wizardsandbeasts.currency.network;

import at.koopro.wizardsandbeasts.currency.network.GringottsOpenS2CPayload;
import at.koopro.wizardsandbeasts.currency.network.VaultActionC2SPayload;
import at.koopro.wizardsandbeasts.currency.network.VaultSyncS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkVault {

    private ModNetworkVault() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                GringottsOpenS2CPayload.TYPE,
                GringottsOpenS2CPayload.STREAM_CODEC,
                GringottsOpenS2CPayload::handleClient);

        registrar.playToServer(
                VaultActionC2SPayload.TYPE,
                VaultActionC2SPayload.STREAM_CODEC,
                VaultActionC2SPayload::handle);

        registrar.playToClient(
                VaultSyncS2CPayload.TYPE,
                VaultSyncS2CPayload.STREAM_CODEC,
                VaultSyncS2CPayload::handleClient);
    }
}
