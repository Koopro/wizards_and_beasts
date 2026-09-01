package at.koopro.wizardsandbeasts.map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The one gate every waypoint edit goes through.
 *
 * <p>It exists so the authorization rules are written once. The client sends what the player asked
 * for; nothing it sends is trusted. A payload handler that checked its own permissions would be
 * four copies of the same three checks, and the copy that forgot one would be the one that lets a
 * player rename someone else's pins — or erase the map's own discoveries, which are not a player's
 * to rewrite at all.
 */
public final class MapMarkerService {

    /** Outcome of an edit, so the caller can tell the player why nothing happened. */
    public enum Result {
        OK,
        /** No open session, no such map, or the map does not trust this player. */
        DENIED,
        /** The marker id does not exist on this map. */
        NOT_FOUND,
        /** A discovery: real geography, not an editable pin. */
        NOT_EDITABLE,
        /** This player is already at {@link MapAtlas#MAX_WAYPOINTS_PER_PLAYER}. */
        FULL
    }

    private MapMarkerService() {
    }

    /**
     * Adds a pin.
     *
     * <p>The position comes from the payload rather than from the player because pinning a spot you
     * can see on the parchment but are not standing on is the whole point. It is clamped to the
     * world border so a crafted packet cannot park a marker at two billion blocks and make every
     * client's coordinate label overflow its panel.
     */
    public static Result createWaypoint(ServerPlayer player, MapAtlas atlas, Identifier type,
                                        Identifier dimension, int x, int z, String label) {
        UUID owner = player.getUUID();
        if (atlas.waypointCount(owner) >= MapAtlas.MAX_WAYPOINTS_PER_PLAYER) {
            return Result.FULL;
        }
        Identifier resolved = MapMarkerTypes.WAYPOINT_ICONS.contains(type) ? type : MapMarkerTypes.WAYPOINT;
        BlockPos pos = clampToBorder(player.level(), x, z);
        atlas.put(MapMarker.waypoint(resolved, dimension, pos, sanitize(label), owner,
                player.level().getGameTime()));
        return Result.OK;
    }

    public static Result rename(ServerPlayer player, MapAtlas atlas, UUID markerId, String label) {
        return edit(player, atlas, markerId, marker -> marker.withLabel(sanitize(label)));
    }

    public static Result retype(ServerPlayer player, MapAtlas atlas, UUID markerId, Identifier type) {
        if (!MapMarkerTypes.WAYPOINT_ICONS.contains(type)) {
            return Result.DENIED;
        }
        return edit(player, atlas, markerId, marker -> marker.withType(type));
    }

    /**
     * Hiding is allowed on discoveries as well as on pins.
     *
     * <p>The reason it is not routed through {@link #edit} is that hiding is not editing: the
     * marker's content is untouched and the player is only saying they do not want to look at it.
     * Refusing to let someone tidy a cluttered map would be the wrong reading of "not editable".
     */
    public static Result setHidden(ServerPlayer player, MapAtlas atlas, UUID markerId, boolean hidden) {
        MapMarker marker = atlas.marker(markerId);
        if (marker == null) {
            return Result.NOT_FOUND;
        }
        if (!marker.visibleTo(player.getUUID())) {
            return Result.DENIED;
        }
        atlas.put(marker.withHidden(hidden));
        return Result.OK;
    }

    public static Result delete(ServerPlayer player, MapAtlas atlas, UUID markerId) {
        MapMarker marker = atlas.marker(markerId);
        if (marker == null) {
            return Result.NOT_FOUND;
        }
        if (!marker.visibleTo(player.getUUID())) {
            return Result.DENIED;
        }
        if (!marker.source().playerEditable()) {
            return Result.NOT_EDITABLE;
        }
        atlas.remove(markerId);
        return Result.OK;
    }

    private interface Edit {
        MapMarker apply(MapMarker marker);
    }

    private static Result edit(ServerPlayer player, MapAtlas atlas, UUID markerId, Edit edit) {
        MapMarker marker = atlas.marker(markerId);
        if (marker == null) {
            return Result.NOT_FOUND;
        }
        if (!marker.visibleTo(player.getUUID())) {
            return Result.DENIED;
        }
        if (!marker.source().playerEditable()) {
            return Result.NOT_EDITABLE;
        }
        atlas.put(edit.apply(marker));
        return Result.OK;
    }

    /**
     * Trims a player-typed label to something a tooltip can hold and strips the section sign, so a
     * waypoint name cannot inject formatting codes into every other viewer's map.
     */
    private static String sanitize(String label) {
        String cleaned = label.replace('§', ' ').trim();
        return cleaned.length() > MapMarker.MAX_LABEL_LENGTH
                ? cleaned.substring(0, MapMarker.MAX_LABEL_LENGTH)
                : cleaned;
    }

    private static BlockPos clampToBorder(net.minecraft.world.level.Level level, int x, int z) {
        var border = level.getWorldBorder();
        int minX = (int) Math.floor(border.getMinX());
        int maxX = (int) Math.ceil(border.getMaxX());
        int minZ = (int) Math.floor(border.getMinZ());
        int maxZ = (int) Math.ceil(border.getMaxZ());
        return new BlockPos(Math.clamp(x, minX, maxX), 0, Math.clamp(z, minZ, maxZ));
    }

    /** Records where a player died on every map they were carrying. */
    public static void recordDeath(ServerPlayer player, ServerLevel level) {
        MaraudersMapAtlasStore store = MaraudersMapAtlasStore.get(level);
        for (UUID mapId : at.koopro.wizardsandbeasts.item.map.MaraudersMapItem.carriedMapIds(player)) {
            MapAtlas atlas = store.peek(mapId);
            if (atlas == null) {
                continue;
            }
            atlas.recordDeath(level.dimension().identifier(), player.blockPosition(),
                    player.getUUID(), level.getGameTime());
            MapSessions.invalidateMarkers(mapId);
            store.markChanged();
        }
    }
}
