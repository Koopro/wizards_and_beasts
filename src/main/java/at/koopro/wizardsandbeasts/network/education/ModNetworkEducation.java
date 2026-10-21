package at.koopro.wizardsandbeasts.network.education;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Education: the Education tab's and examination desk's view, and their buttons. */
public final class ModNetworkEducation {

    private ModNetworkEducation() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(EducationViewS2CPayload.TYPE, EducationViewS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleEducationView);
        registrar.playToServer(EducationActionC2SPayload.TYPE, EducationActionC2SPayload.STREAM_CODEC,
                EducationActionC2SPayload::handle);
    }
}
