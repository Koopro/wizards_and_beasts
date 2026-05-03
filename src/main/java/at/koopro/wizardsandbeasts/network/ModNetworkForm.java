package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkForm {

    private ModNetworkForm() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                FormSyncS2CPacket.TYPE,
                FormSyncS2CPacket.STREAM_CODEC,
                FormSyncS2CPacket::handleClient);

        registrar.playToServer(
                FormChangeRequestC2SPacket.TYPE,
                FormChangeRequestC2SPacket.STREAM_CODEC,
                FormChangeRequestC2SPacket::handle);

        registrar.playToServer(
                ObscurialToggleFormC2SPacket.TYPE,
                ObscurialToggleFormC2SPacket.STREAM_CODEC,
                ObscurialToggleFormC2SPacket::handle);

        registrar.playToServer(
                ObscurialStressVentC2SPacket.TYPE,
                ObscurialStressVentC2SPacket.STREAM_CODEC,
                ObscurialStressVentC2SPacket::handle);

        registrar.playToServer(
                SizeOverrideC2SPacket.TYPE,
                SizeOverrideC2SPacket.STREAM_CODEC,
                SizeOverrideC2SPacket::handle);

        registrar.playToClient(
                TransitionStartS2CPacket.TYPE,
                TransitionStartS2CPacket.STREAM_CODEC,
                TransitionStartS2CPacket::handleClient);

        registrar.playToClient(
                TransitionEndS2CPacket.TYPE,
                TransitionEndS2CPacket.STREAM_CODEC,
                TransitionEndS2CPacket::handleClient);

        registrar.playToClient(
                DebugOverlayToggleS2CPacket.TYPE,
                DebugOverlayToggleS2CPacket.STREAM_CODEC,
                DebugOverlayToggleS2CPacket::handleClient);
    }
}
