package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkWand {

    private ModNetworkWand() {
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                SetFlexibilityPayload.TYPE,
                SetFlexibilityPayload.STREAM_CODEC,
                SetFlexibilityPayload::handle);
        registrar.playToServer(
                SelectTrialWandPayload.TYPE,
                SelectTrialWandPayload.STREAM_CODEC,
                SelectTrialWandPayload::handle);
        registrar.playToServer(
                ChooseTrialWandPayload.TYPE,
                ChooseTrialWandPayload.STREAM_CODEC,
                ChooseTrialWandPayload::handle);
        registrar.playToClient(
                SyncTrialResonancePayload.TYPE,
                SyncTrialResonancePayload.STREAM_CODEC,
                SyncTrialResonancePayload::handleClient);
    }
}
