package at.koopro.wizardsandbeasts.network.map;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Marauder's Map payloads.
 *
 * <p>Three streams to the client, deliberately separate rather than one omnibus message: the dots
 * resend constantly, the parchment ships once per region and then only on change, and the markers
 * ship only when someone discovers or pins something. Folding them together would mean resending
 * the terrain at the rate of the fastest-moving thing on it.
 */
public final class ModNetworkMap {

    private ModNetworkMap() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                MapOpenS2CPayload.TYPE,
                MapOpenS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleMapOpen);

        registrar.playToClient(
                MapSyncS2CPayload.TYPE,
                MapSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleMapSync);

        registrar.playToClient(
                MapRegionS2CPayload.TYPE,
                MapRegionS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleMapRegion);

        registrar.playToClient(
                MapMarkersS2CPayload.TYPE,
                MapMarkersS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleMapMarkers);

        registrar.playToServer(
                MapCloseC2SPayload.TYPE,
                MapCloseC2SPayload.STREAM_CODEC,
                MapCloseC2SPayload::handle);

        registrar.playToServer(
                MapWaypointC2SPayload.TYPE,
                MapWaypointC2SPayload.STREAM_CODEC,
                MapWaypointC2SPayload::handle);
    }
}
