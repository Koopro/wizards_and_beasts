package at.koopro.wizardsandbeasts.client.creature;

import at.koopro.wizardsandbeasts.network.creature.CreatureDefinitionsSyncS2CPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client entry point for {@link CreatureDefinitionsSyncS2CPayload}. Class-init-safe (no client-only types), so the
 * common network registration can name it on a dedicated server. Same shape as {@code ClientBroomDefinitions}.
 */
@NullMarked
public final class ClientCreatureDefinitions {

    private ClientCreatureDefinitions() {}

    public static void handle(CreatureDefinitionsSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(payload::applyToClientRegistry);
    }
}
