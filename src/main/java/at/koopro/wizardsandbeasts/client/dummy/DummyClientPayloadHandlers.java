package at.koopro.wizardsandbeasts.client.dummy;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.network.dummy.DamageNumberS2CPayload;

/**
 * Client handler for {@link DamageNumberS2CPayload}, split from the common registrar so client
 * types are only ever referenced inside a method body - the same dist-safety shape as
 * {@code ToastClientPayloadHandlers}.
 */
@NullMarked
public final class DummyClientPayloadHandlers {

    private DummyClientPayloadHandlers() {}

    public static void handleDamageNumber(DamageNumberS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DamageNumberOverlay.add(payload));
    }
}
