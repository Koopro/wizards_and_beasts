package at.koopro.wizardsandbeasts.network.outline;

import at.koopro.wizardsandbeasts.client.render.outline.OutlineClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/** Both outline payloads: entity outlines (broadcast) and block highlights (per viewer). */
@NullMarked
public final class ModNetworkOutline {

    private ModNetworkOutline() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                EntityOutlineS2CPayload.TYPE,
                EntityOutlineS2CPayload.STREAM_CODEC,
                OutlineClientPayloadHandlers::handleEntityOutline);
        registrar.playToClient(
                BlockOutlineS2CPayload.TYPE,
                BlockOutlineS2CPayload.STREAM_CODEC,
                OutlineClientPayloadHandlers::handleBlockOutline);
    }
}
