package at.koopro.wizardsandbeasts.spell.teacher;

import at.koopro.wizardsandbeasts.network.spell.teacher.SpellTeacherLearnC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.teacher.SpellTeacherOpenS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkTeacher {

    private ModNetworkTeacher() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                SpellTeacherOpenS2CPayload.TYPE,
                SpellTeacherOpenS2CPayload.STREAM_CODEC,
                SpellTeacherOpenS2CPayload::handleClient);

        registrar.playToServer(
                SpellTeacherLearnC2SPayload.TYPE,
                SpellTeacherLearnC2SPayload.STREAM_CODEC,
                SpellTeacherLearnC2SPayload::handle);
    }
}
