package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.network.map.MapMarkersS2CPayload;
import at.koopro.wizardsandbeasts.network.map.MapOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.map.MapRegionS2CPayload;
import at.koopro.wizardsandbeasts.network.map.MapSyncS2CPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client end of the map's four incoming streams.
 *
 * <p>All four write into {@link ClientMapAtlas} and none of them touch the screen, with one
 * exception: the open payload has to put the screen up the first time. Everything after that is
 * data landing in a cache the screen reads on its next frame, which is why terrain arriving while
 * the player is mid-drag does not interrupt the drag.
 */
public class MapClientHandler {

    public static void handleMapOpen(MapOpenS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientMapAtlas.open(pkt.mapId(), pkt.dimension(), pkt.palette());
            Minecraft mc = Minecraft.getInstance();
            // Also fires when the holder walks through a portal with the map open, and when the
            // biome palette grows. Only the first case needs a screen; replacing a live screen in
            // the other two would throw away the player's pan and zoom mid-look.
            if (!(mc.screen instanceof MaraudersMapScreen)) {
                mc.setScreen(new MaraudersMapScreen());
            }
        });
    }

    public static void handleMapRegion(MapRegionS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientMapAtlas.acceptRegion(pkt.dimension(), pkt.regionKey(), pkt.runs()));
    }

    public static void handleMapMarkers(MapMarkersS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientMapAtlas.acceptMarkers(pkt.markers()));
    }

    public static void handleMapSync(MapSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientMapAtlas.acceptEntities(pkt.entries());
            MapTrails.record(pkt.entries());
        });
    }
}
