package at.koopro.wizardsandbeasts.spell.beam;

import at.koopro.wizardsandbeasts.client.wand.BeamClientPayloadHandlers;
import at.koopro.wizardsandbeasts.network.debug.BeamDebugOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.debug.BeamPresetS2CPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkBeamDebug {

    private ModNetworkBeamDebug() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                BeamDebugOpenS2CPayload.TYPE,
                BeamDebugOpenS2CPayload.STREAM_CODEC,
                BeamDebugOpenS2CPayload::handleClient);
        registrar.playToClient(
                BeamPresetS2CPayload.TYPE,
                BeamPresetS2CPayload.STREAM_CODEC,
                BeamClientPayloadHandlers::handleBeamPreset);
    }
}
