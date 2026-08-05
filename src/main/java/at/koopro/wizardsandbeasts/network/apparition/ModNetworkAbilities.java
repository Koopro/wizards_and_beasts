package at.koopro.wizardsandbeasts.network.apparition;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import at.koopro.wizardsandbeasts.network.ability.AbilityDataSyncPayload;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionWardsSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.legilimency.LegilimencyVisionS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Apparition / Legilimency payloads. The two C2S request payloads are gone — both abilities now trigger
 * through the ability framework's {@code AbilityUseC2SPayload}, which carries the target; only the S2C
 * ward/vision syncs remain.
 */
public final class ModNetworkAbilities {
    private ModNetworkAbilities() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                AbilityDataSyncPayload.TYPE,
                AbilityDataSyncPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleAbilityDataSync);
        registrar.playToClient(
                ApparitionWardsSyncS2CPayload.TYPE,
                ApparitionWardsSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleApparitionWardsSync);
        registrar.playToClient(
                ApparitionPointsSyncS2CPayload.TYPE,
                ApparitionPointsSyncS2CPayload.STREAM_CODEC,
                ApparitionPointsSyncS2CPayload::handle);
        registrar.playToClient(
                OpenApparitionSelectorS2CPayload.TYPE,
                OpenApparitionSelectorS2CPayload.STREAM_CODEC,
                OpenApparitionSelectorS2CPayload::handle);
        registrar.playToServer(
                ApparitionTravelC2SPayload.TYPE,
                ApparitionTravelC2SPayload.STREAM_CODEC,
                ApparitionTravelC2SPayload::handle);
        registrar.playToServer(
                ApparitionChargeReleaseC2SPayload.TYPE,
                ApparitionChargeReleaseC2SPayload.STREAM_CODEC,
                ApparitionChargeReleaseC2SPayload::handle);
        registrar.playToClient(
                LegilimencyVisionS2CPayload.TYPE,
                LegilimencyVisionS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleLegilimencyVision);
    }
}
