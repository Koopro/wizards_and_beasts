package at.koopro.wizardsandbeasts.network.polyjuice;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkPolyjuice {

    private ModNetworkPolyjuice() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                PolyjuiceSyncS2CPayload.TYPE,
                PolyjuiceSyncS2CPayload.STREAM_CODEC,
                PolyjuiceSyncS2CPayload::handleClient);
    }
}
