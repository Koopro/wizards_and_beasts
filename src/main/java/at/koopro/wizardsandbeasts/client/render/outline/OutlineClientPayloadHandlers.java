package at.koopro.wizardsandbeasts.client.render.outline;

import at.koopro.wizardsandbeasts.network.outline.BlockOutlineS2CPayload;
import at.koopro.wizardsandbeasts.network.outline.EntityOutlineS2CPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client-side handlers for the outline payloads. Kept separate from the common registrars for the same
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

    public static void handleBlockOutline(BlockOutlineS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            switch (payload.action()) {
                case ADD -> ClientBlockOutlineState.add(payload.highlightId(), payload.outline(), payload.positions());
                case REMOVE -> ClientBlockOutlineState.remove(payload.highlightId());
                case CLEAR -> ClientBlockOutlineState.clear();
            }
        });
    }
}
