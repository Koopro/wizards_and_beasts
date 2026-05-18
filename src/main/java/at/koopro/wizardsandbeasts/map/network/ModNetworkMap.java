package at.koopro.wizardsandbeasts.map.network;

import at.koopro.wizardsandbeasts.map.network.MapCloseC2SPayload;
import at.koopro.wizardsandbeasts.map.network.MapOpenS2CPayload;
import at.koopro.wizardsandbeasts.map.network.MapSyncS2CPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkMap {

    private ModNetworkMap() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                MapOpenS2CPayload.TYPE,
                MapOpenS2CPayload.STREAM_CODEC,
                MapOpenS2CPayload::handleClient);

        registrar.playToClient(
                MapSyncS2CPayload.TYPE,
                MapSyncS2CPayload.STREAM_CODEC,
                MapSyncS2CPayload::handleClient);

        registrar.playToServer(
                MapCloseC2SPayload.TYPE,
                MapCloseC2SPayload.STREAM_CODEC,
                MapCloseC2SPayload::handle);
    }
}
