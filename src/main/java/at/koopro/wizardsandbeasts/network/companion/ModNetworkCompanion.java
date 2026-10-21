package at.koopro.wizardsandbeasts.network.companion;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Companions: the owner's roster and the Beasts tab's orders. */
public final class ModNetworkCompanion {

    private ModNetworkCompanion() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(CompanionRosterS2CPayload.TYPE, CompanionRosterS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleCompanionRoster);
        registrar.playToServer(CompanionActionC2SPayload.TYPE, CompanionActionC2SPayload.STREAM_CODEC,
                CompanionActionC2SPayload::handle);
    }
}
