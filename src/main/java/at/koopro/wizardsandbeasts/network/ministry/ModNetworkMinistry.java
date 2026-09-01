package at.koopro.wizardsandbeasts.network.ministry;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkMinistry {

    private ModNetworkMinistry() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                MinistryRecordSyncS2CPayload.TYPE,
                MinistryRecordSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleMinistryRecordSync);
        registrar.playToClient(
                LicenceOpenS2CPayload.TYPE,
                LicenceOpenS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleLicenceOpen);
    }
}
