package at.koopro.wizardsandbeasts.network.dummy;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.client.dummy.DummyClientPayloadHandlers;

/** Payload registration for the duelling dummy. */
@NullMarked
public final class ModNetworkDummy {

    private ModNetworkDummy() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                DamageNumberS2CPayload.TYPE,
                DamageNumberS2CPayload.STREAM_CODEC,
                DummyClientPayloadHandlers::handleDamageNumber);
    }
}
