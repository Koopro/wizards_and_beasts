package at.koopro.wizardsandbeasts.network.career;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Career tracks: the Career tab's view and its buttons. */
public final class ModNetworkCareer {

    private ModNetworkCareer() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(CareerViewS2CPayload.TYPE, CareerViewS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleCareerView);
        registrar.playToServer(CareerActionC2SPayload.TYPE, CareerActionC2SPayload.STREAM_CODEC,
                CareerActionC2SPayload::handle);
    }
}
