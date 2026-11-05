package at.koopro.wizardsandbeasts.network.creature;

import at.koopro.wizardsandbeasts.client.creature.ClientCreatureDefinitions;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Creatures: the definition table the client needs to animate the data-driven roster. */
public final class ModNetworkCreature {

    private ModNetworkCreature() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                CreatureDefinitionsSyncS2CPayload.TYPE,
                CreatureDefinitionsSyncS2CPayload.STREAM_CODEC,
                ClientCreatureDefinitions::handle);
    }
}
