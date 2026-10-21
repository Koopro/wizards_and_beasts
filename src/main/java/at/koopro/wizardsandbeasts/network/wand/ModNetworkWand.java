package at.koopro.wizardsandbeasts.network.wand;


import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkWand {

    private ModNetworkWand() {
    }

    public static void register(PayloadRegistrar registrar) {
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
        registrar.playToClient(
                WandGlobalsSyncS2CPayload.TYPE,
                WandGlobalsSyncS2CPayload.STREAM_CODEC,
                WandGlobalsSyncS2CPayload::handleClient);
    }
}
