package at.koopro.wizardsandbeasts.client.ability.state;

import at.koopro.wizardsandbeasts.network.ability.AbilityDefinitionsSyncS2CPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client entry point for {@link AbilityDefinitionsSyncS2CPayload}. Delegates to the payload's
 * {@code applyToClientRegistry}, which swaps the (common) {@code AbilityDefinitionRegistry} mirror so the
 * wheel can read ability metadata. Class-init-safe: no client-only references at load time, so it can be
 * registered from the common network class.
 */
@NullMarked
public final class ClientAbilityDefinitions {

    private ClientAbilityDefinitions() {}

    public static void handle(AbilityDefinitionsSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(payload::applyToClientRegistry);
    }
}
