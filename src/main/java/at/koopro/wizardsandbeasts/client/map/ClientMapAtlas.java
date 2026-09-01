package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.map.MapGeometry;
import at.koopro.wizardsandbeasts.map.MapMarker;
import at.koopro.wizardsandbeasts.map.MapRegion;
import at.koopro.wizardsandbeasts.map.MapRelief;
import at.koopro.wizardsandbeasts.map.TrackedEntityEntry;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The client's copy of the open map: whatever parchment has arrived so far, plus the markers and
 * the live dots.
 *
 * <p>A separate class from the server's {@code MapAtlas} rather than a shared one, because the two
 * hold different things. The server's is authoritative, complete and persistent; this one is a
 * partial view of a single dimension that is thrown away when the screen closes, and it holds the
 * live entity sweep, which has no business in a saved object.
 *
 * <p>Static, not a field on the screen. Region packets can arrive between the screen being
 * constructed and being shown, and a dimension change re-opens the screen; hanging the cache off
 * the screen instance means both cases silently drop parchment.
 */
public final class ClientMapAtlas {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static @Nullable UUID mapId;
    private static Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static List<Identifier> palette = List.of();
    private static final Map<Long, MapRegion> REGIONS = new HashMap<>();
    private static List<MapMarker> markers = List.of();
    private static List<TrackedEntityEntry> entities = List.of();

    private ClientMapAtlas() {
    }

    /**
     * Called on every open payload, which covers three cases: the map was just unfolded, the holder
     * changed dimension, or the palette grew.
     *
     * <p>Only clears the cached parchment when the identity actually changed. The palette-growth
     * case is by far the most common — a new biome charted — and throwing away every region the
     * client has for it would mean re-downloading the whole map every time the holder walked into a
     * biome they had not seen before.
     */
    public static void open(UUID newMapId, Identifier newDimension, List<Identifier> newPalette) {
        boolean identityChanged = !newMapId.equals(mapId) || !newDimension.equals(dimension);
        mapId = newMapId;
        dimension = newDimension;
        palette = List.copyOf(newPalette);
        if (identityChanged) {
            REGIONS.clear();
            markers = List.of();
            entities = List.of();
        }
    }

    public static void close() {
        mapId = null;
        REGIONS.clear();
        palette = List.of();
        markers = List.of();
        entities = List.of();
    }

    public static boolean isOpen() {
        return mapId != null;
    }

    public static Identifier dimension() {
        return dimension;
    }

    /** Merges an arriving region, discarding one addressed to a dimension the holder has left. */
    public static void acceptRegion(Identifier regionDimension, long key, byte[] runs) {
        if (!regionDimension.equals(dimension)) {
            return;
        }
        MapRegion.decode(runs)
                .resultOrPartial(err -> LOGGER.warn(
                        "[WizardsAndBeasts] Discarding malformed map region {}: {}", key, err))
                .ifPresent(region -> REGIONS.merge(key, region, (existing, incoming) -> {
                    existing.mergeFrom(incoming);
                    return existing;
                }));
    }

    public static void acceptMarkers(Collection<MapMarker> incoming) {
        List<MapMarker> visible = new ArrayList<>(incoming.size());
        for (MapMarker marker : incoming) {
            if (marker.dimension().equals(dimension)) {
                visible.add(marker);
            }
        }
        markers = List.copyOf(visible);
    }

    public static void acceptEntities(List<TrackedEntityEntry> incoming) {
        entities = List.copyOf(incoming);
    }

    public static List<MapMarker> markers() {
        return markers;
    }

    public static List<TrackedEntityEntry> entities() {
        return entities;
    }

    /** Biome id behind a palette index, or {@code null} for an index this client has no name for. */
    public static @Nullable Identifier biomeOf(short index) {
        return index >= 0 && index < palette.size() ? palette.get(index) : null;
    }

    /** The region containing a tile, or {@code null} when it has not arrived (or is uncharted). */
    public static @Nullable MapRegion region(int tileX, int tileZ) {
        return REGIONS.get(MapGeometry.regionKey(
                MapGeometry.tileToRegion(tileX), MapGeometry.tileToRegion(tileZ)));
    }

    /** Biome of a tile, or {@code null} when uncharted. */
    public static @Nullable Identifier biomeAt(int tileX, int tileZ) {
        MapRegion region = region(tileX, tileZ);
        return region == null ? null : biomeOf(region.biomeAt(tileX, tileZ));
    }

    /** Relief of a tile, or {@code null} when uncharted. */
    public static @Nullable MapRelief reliefAt(int tileX, int tileZ) {
        MapRegion region = region(tileX, tileZ);
        return region == null ? null : region.reliefAt(tileX, tileZ);
    }

    /** Tiles charted in this dimension — the "world charted" readout in the footer. */
    public static int chartedTiles() {
        int total = 0;
        for (MapRegion region : REGIONS.values()) {
            total += region.surveyedCount();
        }
        return total;
    }
}
