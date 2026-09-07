package at.koopro.wizardsandbeasts.network.heritage;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.HeritageSelectC2SPayload;
import at.koopro.wizardsandbeasts.network.heritage.ProfessionSelectC2SPayload;
import at.koopro.wizardsandbeasts.network.heritage.ProfessionUnlockC2SPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkType {

    private ModNetworkType() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                HeritageDataSyncS2CPayload.TYPE,
                HeritageDataSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleHeritageDataSync);

        registrar.playToServer(
                HeritageSelectC2SPayload.TYPE,
                HeritageSelectC2SPayload.STREAM_CODEC,
                HeritageSelectC2SPayload::handle);

        registrar.playToServer(
                ProfessionUnlockC2SPayload.TYPE,
                ProfessionUnlockC2SPayload.STREAM_CODEC,
                ProfessionUnlockC2SPayload::handle);

        registrar.playToServer(
                ProfessionSelectC2SPayload.TYPE,
                ProfessionSelectC2SPayload.STREAM_CODEC,
                ProfessionSelectC2SPayload::handle);

        registrar.playToClient(
                SyncHeritageAppearancePayload.TYPE,
                SyncHeritageAppearancePayload.STREAM_CODEC,
                ClientPayloadHandlers::handleSyncHeritageAppearance);

        registrar.playToClient(
                HeritageIdentitySyncS2CPayload.TYPE,
                HeritageIdentitySyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleHeritageIdentitySync);

        registrar.playToClient(
                BloodDataSyncS2CPayload.TYPE,
                BloodDataSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleBloodDataSync);
    }
}
