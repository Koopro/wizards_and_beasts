package at.koopro.wizardsandbeasts.network.currency;

import at.koopro.wizardsandbeasts.network.currency.GringottsOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.currency.VaultActionC2SPayload;
import at.koopro.wizardsandbeasts.network.currency.VaultSyncS2CPayload;

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
