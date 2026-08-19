package at.koopro.wizardsandbeasts.client.gui.toast;

import at.koopro.wizardsandbeasts.network.feedback.NotifyS2CPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client handler for {@link NotifyS2CPayload}, split from the common registrar so client types are
 * only ever referenced inside a method body — the same dist-safety shape as
 * {@code BeamClientPayloadHandlers}.
 */
@NullMarked
public final class ToastClientPayloadHandlers {

    private ToastClientPayloadHandlers() {}

    public static void handleNotify(NotifyS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> WizardsToasts.show(payload.kind(), payload.title(), payload.body()));
    }
}
