package at.koopro.wizardsandbeasts.network.standing;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkStanding {

    private ModNetworkStanding() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                StandingSyncS2CPayload.TYPE,
                StandingSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleStandingSync);
    }
}
