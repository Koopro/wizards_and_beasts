package at.koopro.wizardsandbeasts.client.render.outline;

import at.koopro.wizardsandbeasts.network.debug.EntityOutlineS2CPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client-side handler for the outline payload. Kept separate from the common registrar for the same
 * dist-safety reason as {@code BeamClientPayloadHandlers}: client types are referenced only inside
 * method bodies, which never run on a dedicated server.
 */
@NullMarked
public final class OutlineClientPayloadHandlers {

    private OutlineClientPayloadHandlers() {}

    public static void handleEntityOutline(EntityOutlineS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.replaceAll()) {
                ClientOutlineState.replaceAll(payload.entities());
            } else {
                payload.entities().forEach(ClientOutlineState::put);
            }
        });
    }
}
