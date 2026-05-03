package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkBroom {

    private ModNetworkBroom() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                BroomInputC2SPacket.TYPE,
                BroomInputC2SPacket.STREAM_CODEC,
                BroomInputC2SPacket::handle);
    }
}
