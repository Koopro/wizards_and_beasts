package at.koopro.wizardsandbeasts.network.form;

import at.koopro.wizardsandbeasts.network.form.DebugOverlayToggleS2CPayload;
import at.koopro.wizardsandbeasts.network.form.FormChangeRequestC2SPayload;
import at.koopro.wizardsandbeasts.network.form.FormSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.form.ObscurialStressVentC2SPayload;
import at.koopro.wizardsandbeasts.network.form.ObscurialToggleFormC2SPayload;
import at.koopro.wizardsandbeasts.network.form.SizeOverrideC2SPayload;
import at.koopro.wizardsandbeasts.network.form.TransitionEndS2CPayload;
import at.koopro.wizardsandbeasts.network.form.TransitionStartS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkForm {

    private ModNetworkForm() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                FormSyncS2CPayload.TYPE,
                FormSyncS2CPayload.STREAM_CODEC,
                FormSyncS2CPayload::handleClient);

        registrar.playToServer(
                FormChangeRequestC2SPayload.TYPE,
                FormChangeRequestC2SPayload.STREAM_CODEC,
                FormChangeRequestC2SPayload::handle);

        registrar.playToServer(
                ObscurialToggleFormC2SPayload.TYPE,
                ObscurialToggleFormC2SPayload.STREAM_CODEC,
                ObscurialToggleFormC2SPayload::handle);

        registrar.playToServer(
                ObscurialStressVentC2SPayload.TYPE,
                ObscurialStressVentC2SPayload.STREAM_CODEC,
                ObscurialStressVentC2SPayload::handle);

        registrar.playToServer(
                SizeOverrideC2SPayload.TYPE,
                SizeOverrideC2SPayload.STREAM_CODEC,
                SizeOverrideC2SPayload::handle);

        registrar.playToClient(
                TransitionStartS2CPayload.TYPE,
                TransitionStartS2CPayload.STREAM_CODEC,
                TransitionStartS2CPayload::handleClient);

        registrar.playToClient(
                TransitionEndS2CPayload.TYPE,
                TransitionEndS2CPayload.STREAM_CODEC,
                TransitionEndS2CPayload::handleClient);

        registrar.playToClient(
                DebugOverlayToggleS2CPayload.TYPE,
                DebugOverlayToggleS2CPayload.STREAM_CODEC,
                DebugOverlayToggleS2CPayload::handleClient);
    }
}
