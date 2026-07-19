package at.koopro.wizardsandbeasts.network.ability;

import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityDefinitions;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilitySelectionState;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/**
 * Payload registration for the ability framework (definitions sync, wheel-state sync, and the two C2S
 * request payloads). Registered from {@code ModNetwork#register}. The S2C client handlers live on
 * class-init-safe mirrors (only common-typed static state), so referencing them here does not load a
 * client-only class on a dedicated server — the handler bodies run client-side only.
 */
@NullMarked
public final class ModNetworkAbilityFramework {

    private ModNetworkAbilityFramework() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                AbilityDefinitionsSyncS2CPayload.TYPE,
                AbilityDefinitionsSyncS2CPayload.STREAM_CODEC,
                ClientAbilityDefinitions::handle);
        registrar.playToClient(
                AbilitySelectionSyncS2CPayload.TYPE,
                AbilitySelectionSyncS2CPayload.STREAM_CODEC,
                ClientAbilitySelectionState::handle);
        registrar.playToServer(
                AbilitySelectionC2SPayload.TYPE,
                AbilitySelectionC2SPayload.STREAM_CODEC,
                AbilitySelectionC2SPayload::handle);
        registrar.playToServer(
                AbilityUseC2SPayload.TYPE,
                AbilityUseC2SPayload.STREAM_CODEC,
                AbilityUseC2SPayload::handle);
    }
}
