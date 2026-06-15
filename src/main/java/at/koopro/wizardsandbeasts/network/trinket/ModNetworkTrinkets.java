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

        // Two-Way Mirror
        registrar.playToClient(
                MirrorOpenS2CPayload.TYPE,
                MirrorOpenS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleMirrorOpen);
        registrar.playToClient(
                MirrorPresenceS2CPayload.TYPE,
                MirrorPresenceS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleMirrorPresence);
        registrar.playToClient(
                MirrorCloseS2CPayload.TYPE,
                MirrorCloseS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleMirrorClose);
        registrar.playToServer(
                MirrorConnectC2SPayload.TYPE,
                MirrorConnectC2SPayload.STREAM_CODEC,
                MirrorConnectC2SPayload::handle);
        registrar.playToServer(
                MirrorCloseC2SPayload.TYPE,
                MirrorCloseC2SPayload.STREAM_CODEC,
                MirrorCloseC2SPayload::handle);
    }
}
