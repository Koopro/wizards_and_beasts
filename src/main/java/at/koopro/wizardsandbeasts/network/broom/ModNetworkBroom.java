package at.koopro.wizardsandbeasts.network.broom;

import at.koopro.wizardsandbeasts.network.broom.BroomInputC2SPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkBroom {

    private ModNetworkBroom() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                BroomInputC2SPayload.TYPE,
                BroomInputC2SPayload.STREAM_CODEC,
                BroomInputC2SPayload::handle);
    }
}
