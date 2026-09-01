package at.koopro.wizardsandbeasts.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.map.MapMarkersS2CPayload;
import at.koopro.wizardsandbeasts.network.map.MapOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.map.MapRegionS2CPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ships charted parchment to open maps, a few regions at a time.
 *
 * <p>The alternative was one packet on open carrying the whole atlas. On a world someone has
 * actually explored that is megabytes, which is a visible stall at exactly the moment the player
 * expects the map to snap open — and it is bytes that are almost all thrown away, because they are
 * looking at where they are standing.
 *
 * <p>So the screen opens immediately on nothing, and fills in. Regions are queued nearest-relevant
 * first by {@code MaraudersMapItem}, a small number go out per tick, and the visible effect is ink
 * developing across the page. What would have been a stall is the artefact's best animation, and it
 * is not an animation — it is the load.
 *
 * <p>Separate from {@link MaraudersMapTracker} because the two have opposite shapes: the dots are a
 * small payload resent constantly, the parchment is a large payload sent once and then only when it
 * changes.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MapAtlasStreamer {

    /**
     * Regions per viewer per tick.
     *
     * <p>Three RLE regions is a few hundred bytes in the common case and 12KB in the pathological
     * one, against a 2MB packet ceiling. The limit is not the wire, it is that a burst large enough
     * to matter would arrive as a visible pop rather than as ink spreading.
     */
    private static final int REGIONS_PER_TICK = 3;

    private MapAtlasStreamer() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (MapSessions.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<Map.Entry<UUID, MapSession>> snapshot = new ArrayList<>(MapSessions.entries());

        for (Map.Entry<UUID, MapSession> entry : snapshot) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                MapSessions.close(entry.getKey());
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            MapSession session = entry.getValue();
            MapAtlas atlas = MaraudersMapAtlasStore.get(level).peek(session.mapId());
            if (atlas == null) {
                continue;
            }

            if (session.paletteDirty()) {
                sendPalette(player, session, atlas);
            }
            if (session.markersDirty()) {
                sendMarkers(player, session, atlas);
            }
            sendRegions(player, session, atlas);
        }
    }

    /**
     * Re-sends the header when the palette has grown.
     *
     * <p>Reusing the open payload rather than adding a palette-only one: it also carries the
     * dimension and the holder's position, which is exactly what the client needs when this fires
     * because the holder stepped through a portal. One message, both cases, and the client's
     * handler is already idempotent about it.
     */
    private static void sendPalette(ServerPlayer player, MapSession session, MapAtlas atlas) {
        // Everything charted in the new dimension is re-queued: moveTo cleared the sent set, so
        // without this the client would sit on an empty page until the surveyor happened to change
        // a region.
        session.queueRegions(atlas.regionKeys(session.dimension()));
        PacketDistributor.sendToPlayer(player, new MapOpenS2CPayload(
                session.mapId(),
                session.dimension(),
                player.getBlockX(),
                player.getBlockZ(),
                MapSession.SENSE_RADIUS,
                atlas.palette()));
        session.clearPaletteDirty();
    }

    /** Sends the markers this viewer is allowed to see, filtered before encoding. */
    private static void sendMarkers(ServerPlayer player, MapSession session, MapAtlas atlas) {
        UUID viewer = player.getUUID();
        List<MapMarker> visible = new ArrayList<>();
        for (MapMarker marker : atlas.markers()) {
            if (marker.visibleTo(viewer)) {
                visible.add(marker);
            }
        }
        PacketDistributor.sendToPlayer(player, new MapMarkersS2CPayload(visible));
        session.clearMarkersDirty();
    }

    private static void sendRegions(ServerPlayer player, MapSession session, MapAtlas atlas) {
        for (int i = 0; i < REGIONS_PER_TICK && session.hasPendingRegions(); i++) {
            Long key = session.pollRegion();
            if (key == null) {
                return;
            }
            MapRegion region = atlas.region(session.dimension(), key);
            if (region == null || region.isEmpty()) {
                continue; // queued then emptied, or a dimension change raced the queue
            }
            PacketDistributor.sendToPlayer(player,
                    new MapRegionS2CPayload(session.dimension(), key, region.encode()));
        }
    }
}
