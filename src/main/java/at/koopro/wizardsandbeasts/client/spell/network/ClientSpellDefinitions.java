package at.koopro.wizardsandbeasts.client.spell.network;

import at.koopro.wizardsandbeasts.network.spell.SpellDefinitionsSyncS2CPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client entry point for {@link SpellDefinitionsSyncS2CPayload}. Delegates to the payload's
 * {@code applyToClientRegistry}, which swaps the JSON slice of the (common) spell registry so the
 * HUD, the spell wheel and the beam renderer can resolve datapack spells by id.
 *
 * <p>Class-init-safe: no client-only references at load time, so it can be named from the common
 * network registration class without loading anything client-side on a dedicated server. Same shape
 * as {@code ClientBroomDefinitions}.
 */
@NullMarked
public final class ClientSpellDefinitions {

    private ClientSpellDefinitions() {}

    public static void handle(SpellDefinitionsSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(payload::applyToClientRegistry);
    }
}
