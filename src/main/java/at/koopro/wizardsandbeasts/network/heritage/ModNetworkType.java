package at.koopro.wizardsandbeasts.network.heritage;

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
                HeritageDataSyncS2CPayload::handleClient);

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
    }
}
