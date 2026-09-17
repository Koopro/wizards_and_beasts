package at.koopro.wizardsandbeasts.network.disguise;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkDisguise {

    private ModNetworkDisguise() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                DisguiseSyncS2CPayload.TYPE,
                DisguiseSyncS2CPayload.STREAM_CODEC,
                DisguiseSyncS2CPayload::handleClient);
    }
}
