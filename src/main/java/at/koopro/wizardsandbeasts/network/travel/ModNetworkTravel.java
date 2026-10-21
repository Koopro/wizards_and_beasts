package at.koopro.wizardsandbeasts.network.travel;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payloads for travel progression: time trials and the Travel tab. */
public final class ModNetworkTravel {

    private ModNetworkTravel() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                TimeTrialS2CPayload.TYPE,
                TimeTrialS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleTimeTrial);
        registrar.playToServer(
                TravelProfileC2SPayload.TYPE,
                TravelProfileC2SPayload.STREAM_CODEC,
                TravelProfileC2SPayload::handle);
        registrar.playToClient(
                TravelProfileS2CPayload.TYPE,
                TravelProfileS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleTravelProfile);
    }
}
