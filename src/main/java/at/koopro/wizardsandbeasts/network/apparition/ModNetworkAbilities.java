package at.koopro.wizardsandbeasts.network.apparition;

import at.koopro.wizardsandbeasts.network.ability.AbilityDataSyncPayload;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionRequestPayload;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionWardsSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.legilimency.LegilimencyRequestPayload;
import at.koopro.wizardsandbeasts.network.legilimency.LegilimencyVisionS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkAbilities {
    private ModNetworkAbilities() {
    }

    public static void register(PayloadRegistrar registrar) {
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
