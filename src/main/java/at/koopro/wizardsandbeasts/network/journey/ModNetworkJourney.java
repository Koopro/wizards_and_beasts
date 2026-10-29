package at.koopro.wizardsandbeasts.network.journey;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The Apprentice's Path: the server's view of it, for the character sheet. */
public final class ModNetworkJourney {

    private ModNetworkJourney() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(ApprenticePathS2CPayload.TYPE, ApprenticePathS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleApprenticePath);
    }
}
