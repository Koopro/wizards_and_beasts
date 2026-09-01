package at.koopro.wizardsandbeasts.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every Marauder's Map currently open, keyed by the player looking at it.
 *
 * <p>One place owns the lifecycle so the three things that stream to an open map — terrain regions,
 * markers and moving dots — can never disagree about whether a map is open, which dimension it is
 * showing, or whether the player is still connected. They used to be one map inside the entity
 * tracker, which is why a dimension change silently killed the sweep while leaving the screen up.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MapSessions {

    private static final Map<UUID, MapSession> SESSIONS = new HashMap<>();

    private MapSessions() {
    }

    public static MapSession open(ServerPlayer player, UUID mapId) {
        MapSession session = new MapSession(mapId, player.level().dimension().identifier());
        SESSIONS.put(player.getUUID(), session);
        return session;
    }

    public static @Nullable MapSession get(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    public static void close(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public static boolean isEmpty() {
        return SESSIONS.isEmpty();
    }

    public static Set<Map.Entry<UUID, MapSession>> entries() {
        return Collections.unmodifiableSet(SESSIONS.entrySet());
    }

    /**
     * Marks a region stale for every open map that draws from the same atlas.
     *
     * <p>Two players reading the same map — the whole point of the trusted-players list — must both
     * see the corridor the other just walked down.
     */
    public static void invalidateRegion(UUID mapId, Identifier dimension, long regionKey) {
        for (MapSession session : SESSIONS.values()) {
            if (session.mapId().equals(mapId) && session.dimension().equals(dimension)) {
                session.invalidateRegion(regionKey);
            }
        }
    }

    /** Marks the marker list stale for every open view of one atlas. */
    public static void invalidateMarkers(UUID mapId) {
        for (MapSession session : SESSIONS.values()) {
            if (session.mapId().equals(mapId)) {
                session.markMarkersDirty();
            }
        }
    }

    /** Marks the biome palette stale for every open view of one atlas. */
    public static void invalidatePalette(UUID mapId) {
        for (MapSession session : SESSIONS.values()) {
            if (session.mapId().equals(mapId)) {
                session.markPaletteDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        close(event.getEntity().getUUID());
    }

    /**
     * Respawn closes the map. The screen is gone client-side anyway — the death screen replaced it —
     * and a session left open would keep sweeping for a player who is no longer where it thinks.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        close(event.getEntity().getUUID());
    }

    /**
     * A dimension change repoints the open session rather than killing it, and the client is told
     * so it can redraw. Walking into the Nether with the map open is a thing players will do.
     */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        MapSession session = SESSIONS.get(event.getEntity().getUUID());
        if (session != null) {
            session.moveTo(event.getTo().identifier());
        }
    }
}
