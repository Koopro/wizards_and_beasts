package at.koopro.wizardsandbeasts.network.visual;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/** Payload registration for server-distributed visual configuration (beam looks). */
@NullMarked
public final class ModNetworkVisual {

    private ModNetworkVisual() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(BeamVisualSyncS2CPayload.TYPE, BeamVisualSyncS2CPayload.STREAM_CODEC,
                BeamVisualSyncS2CPayload::handleClient);
    }
}
