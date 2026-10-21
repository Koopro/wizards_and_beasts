package at.koopro.wizardsandbeasts.network.form;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkForm {

    private ModNetworkForm() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                FormSyncS2CPayload.TYPE,
                FormSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleFormSync);

        registrar.playToServer(
                FormChangeRequestC2SPayload.TYPE,
                FormChangeRequestC2SPayload.STREAM_CODEC,
                FormChangeRequestC2SPayload::handle);

        // The Obscurial form toggle / stress vent and both Animagus payloads are gone: those abilities
        // now trigger through the ability framework's AbilityUseC2SPayload (see AbilityIds).

        registrar.playToServer(
                SizeOverrideC2SPayload.TYPE,
                SizeOverrideC2SPayload.STREAM_CODEC,
                SizeOverrideC2SPayload::handle);

        registrar.playToClient(
                TransitionStartS2CPayload.TYPE,
                TransitionStartS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleTransitionStart);

        registrar.playToClient(
                TransitionEndS2CPayload.TYPE,
                TransitionEndS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleTransitionEnd);

        registrar.playToClient(
                DebugOverlayToggleS2CPayload.TYPE,
                DebugOverlayToggleS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleDebugOverlayToggle);
    }
}
