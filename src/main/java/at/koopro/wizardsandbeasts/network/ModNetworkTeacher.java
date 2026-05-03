package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkTeacher {

    private ModNetworkTeacher() {
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                SpellTeacherOpenS2CPacket.TYPE,
                SpellTeacherOpenS2CPacket.STREAM_CODEC,
                SpellTeacherOpenS2CPacket::handleClient);

        registrar.playToServer(
                SpellTeacherLearnC2SPacket.TYPE,
                SpellTeacherLearnC2SPacket.STREAM_CODEC,
                SpellTeacherLearnC2SPacket::handle);
    }
}
