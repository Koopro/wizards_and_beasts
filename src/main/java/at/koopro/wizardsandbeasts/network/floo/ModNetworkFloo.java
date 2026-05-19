package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.network.floo.FlooArrivalEffectsS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooBlockSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooTravelRequestC2SPayload;
import at.koopro.wizardsandbeasts.network.floo.OpenFlooGuiS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkFloo {
    private ModNetworkFloo() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                OpenFlooGuiS2CPayload.TYPE,
                OpenFlooGuiS2CPayload.STREAM_CODEC,
                OpenFlooGuiS2CPayload::handleClient);
        registrar.playToServer(
                FlooTravelRequestC2SPayload.TYPE,
                FlooTravelRequestC2SPayload.STREAM_CODEC,
                FlooTravelRequestC2SPayload::handleServer);
        registrar.playToClient(
                FlooArrivalEffectsS2CPayload.TYPE,
                FlooArrivalEffectsS2CPayload.STREAM_CODEC,
                FlooArrivalEffectsS2CPayload::handleClient);
        registrar.playToClient(
                FlooBlockSyncS2CPayload.TYPE,
                FlooBlockSyncS2CPayload.STREAM_CODEC,
                FlooBlockSyncS2CPayload::handleClient);
    }
}
