package at.koopro.neo.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkBroom {

    private ModNetworkBroom() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                BroomInputPacket.TYPE,
                BroomInputPacket.STREAM_CODEC,
                BroomInputPacket::handle);
    }
}
