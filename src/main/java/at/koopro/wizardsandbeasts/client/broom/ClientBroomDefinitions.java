package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.network.broom.BroomDefinitionsSyncS2CPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client entry point for {@link BroomDefinitionsSyncS2CPayload}. Delegates to the payload's
 * {@code applyToClientRegistry}, which swaps the (common) {@code BroomDefinitionRegistry} mirror so
 * the renderer can resolve a broom's model, texture, seat and trail from its synced id.
 *
 * <p>Class-init-safe: no client-only references at load time, so it can be named from the common
 * network registration class without loading anything client-side on a dedicated server. Same shape
 * as {@code ClientAbilityDefinitions}.
 */
@NullMarked
public final class ClientBroomDefinitions {

    private ClientBroomDefinitions() {}

    public static void handle(BroomDefinitionsSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(payload::applyToClientRegistry);
    }
}
