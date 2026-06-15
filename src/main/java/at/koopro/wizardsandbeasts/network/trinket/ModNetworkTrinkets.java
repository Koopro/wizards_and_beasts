package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Network payloads for dark-arts trinkets (Pensieve, Two-Way Mirror, Riddle's Diary). */
public final class ModNetworkTrinkets {

    private ModNetworkTrinkets() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                PensieveOpenS2CPayload.TYPE,
                PensieveOpenS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handlePensieveOpen);
    }
}
