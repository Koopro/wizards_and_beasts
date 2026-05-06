package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkAbilities {
    private ModNetworkAbilities() {
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                AbilityDataSyncPayload.TYPE,
                AbilityDataSyncPayload.STREAM_CODEC,
                AbilityDataSyncPayload::handleClient);
        registrar.playToClient(
                ApparitionWardsSyncS2CPayload.TYPE,
                ApparitionWardsSyncS2CPayload.STREAM_CODEC,
                ApparitionWardsSyncS2CPayload::handleClient);
        registrar.playToServer(
                ApparitionRequestPayload.TYPE,
                ApparitionRequestPayload.STREAM_CODEC,
                ApparitionRequestPayload::handle);
        registrar.playToServer(
                LegilimencyRequestPayload.TYPE,
                LegilimencyRequestPayload.STREAM_CODEC,
                LegilimencyRequestPayload::handle);
        registrar.playToClient(
                LegilimencyVisionS2CPayload.TYPE,
                LegilimencyVisionS2CPayload.STREAM_CODEC,
                LegilimencyVisionS2CPayload::handleClient);
    }
}
