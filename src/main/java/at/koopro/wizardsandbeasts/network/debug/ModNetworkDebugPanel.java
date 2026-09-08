package at.koopro.wizardsandbeasts.network.debug;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/**
 * Payload registration for the in-world debug panel: the debug-mode flag, the client's poll and the
 * server's answer. Registered from {@code ModNetwork#register}, matching every other domain
 * registrar.
 */
@NullMarked
public final class ModNetworkDebugPanel {

    private ModNetworkDebugPanel() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                DebugModeS2CPayload.TYPE,
                DebugModeS2CPayload.STREAM_CODEC,
                DebugModeS2CPayload::handleClient);
        registrar.playToClient(
                DebugInspectResultPayload.TYPE,
                DebugInspectResultPayload.STREAM_CODEC,
                DebugInspectResultPayload::handleClient);
        registrar.playToServer(
                DebugInspectRequestPayload.TYPE,
                DebugInspectRequestPayload.STREAM_CODEC,
                DebugInspectRequestPayload::handle);
    }
}
