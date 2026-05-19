package at.koopro.wizardsandbeasts.network.owl;

import at.koopro.wizardsandbeasts.network.owl.ChooseProfessionPacket;
import at.koopro.wizardsandbeasts.network.owl.OWLDataSyncPayload;
import at.koopro.wizardsandbeasts.network.owl.ProfessionSyncPayload;
import at.koopro.wizardsandbeasts.network.owl.RequestOWLExamPacket;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkOWLs {
    private ModNetworkOWLs() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(RequestOWLExamPacket.TYPE, RequestOWLExamPacket.STREAM_CODEC, RequestOWLExamPacket::handleServer);
        registrar.playToClient(OWLDataSyncPayload.TYPE, OWLDataSyncPayload.STREAM_CODEC, OWLDataSyncPayload::handleClient);
        registrar.playToServer(ChooseProfessionPacket.TYPE, ChooseProfessionPacket.STREAM_CODEC, ChooseProfessionPacket::handleServer);
        registrar.playToClient(ProfessionSyncPayload.TYPE, ProfessionSyncPayload.STREAM_CODEC, ProfessionSyncPayload::handleClient);
    }
}
