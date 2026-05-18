package at.koopro.wizardsandbeasts.owl.network;

import at.koopro.wizardsandbeasts.owl.network.ChooseProfessionPacket;
import at.koopro.wizardsandbeasts.owl.network.OWLDataSyncPayload;
import at.koopro.wizardsandbeasts.owl.network.ProfessionSyncPayload;
import at.koopro.wizardsandbeasts.owl.network.RequestOWLExamPacket;

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
