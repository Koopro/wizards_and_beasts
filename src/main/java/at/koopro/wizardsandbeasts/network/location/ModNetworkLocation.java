package at.koopro.wizardsandbeasts.network.location;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Locations: a place's board and its buttons. */
public final class ModNetworkLocation {

    private ModNetworkLocation() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(LocationViewS2CPayload.TYPE, LocationViewS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleLocationView);
        registrar.playToServer(LocationActionC2SPayload.TYPE, LocationActionC2SPayload.STREAM_CODEC,
                LocationActionC2SPayload::handle);
        registrar.playToServer(PlaceStandingsC2SPayload.TYPE, PlaceStandingsC2SPayload.STREAM_CODEC,
                PlaceStandingsC2SPayload::handle);
        registrar.playToClient(PlaceStandingsS2CPayload.TYPE, PlaceStandingsS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handlePlaceStandings);
    }
}
