package at.koopro.wizardsandbeasts.network.feedback;

import at.koopro.wizardsandbeasts.client.gui.toast.ToastClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ModNetworkFeedback {

    private ModNetworkFeedback() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                NotifyS2CPayload.TYPE,
                NotifyS2CPayload.STREAM_CODEC,
                ToastClientPayloadHandlers::handleNotify);
    }
}
