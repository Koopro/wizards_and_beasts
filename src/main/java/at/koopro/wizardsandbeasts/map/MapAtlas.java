package at.koopro.wizardsandbeasts.map;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything one Marauder's Map knows: the terrain it has been carried across, in every dimension,
 * and every place it has learned the name of.
 *
 * <p>The atlas belongs to the <em>map</em>, not to a player. That is the canon reading — four
 * people made one artefact and shared what it knew — and it is also the design that behaves: a
 * per-player atlas would mean the map you hand a friend is blank in their hands, and a per-world
 * one would mean any map reveals everything anyone ever surveyed. The map item carries a
 * {@code MapId} and {@link MaraudersMapAtlasStore} keeps the atlas under it, so exploration
 * survives logout, restart, death and dimension changes for the same reason the world does: it is
 * in the save, not on the stack.
 *
 * <h2>The biome palette</h2>
 * Regions store {@code short} indices into {@link #palette}. Storing registry ids per tile would
 * multiply the region size by ten; storing a datapack-defined art ordinal would repaint the world
 * whenever the pack changed. A palette of biome ids is stable against both, and the ~1000-entry
 * ceiling of a signed short is far above any realistic biome count in one save.
 *
 * <p>Not thread-safe, and not meant to be: every mutation runs on the server thread, from the
 * surveyor or from a packet handler that has already been through {@code enqueueWork}.
 */
public final class MapAtlas {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Two markers of the same kind within this many blocks are the same place.
     *
     * <p>Half a region. Landmark detection fires per chunk, so a castle spanning six chunks would
     * otherwise plant six castles; a village found from two directions, two villages.
     */
    public static final int MERGE_TOLERANCE = 256;

    /** Ceiling on player-placed pins, so one player cannot make the map unusable for the rest. */
    public static final int MAX_WAYPOINTS_PER_PLAYER = 64;

    private final List<Identifier> palette = new ArrayList<>();
    private final Map<Identifier, Short> paletteIndex = new HashMap<>();
    private final Map<Identifier, Map<Long, MapRegion>> dimensions = new LinkedHashMap<>();
    private final Map<UUID, MapMarker> markers = new LinkedHashMap<>();

    public MapAtlas() {
    }

    // -- Codec -------------------------------------------------------------

    private record StoredDimension(Identifier dimension, List<MapRegion.Stored> regions) {
        static final Codec<StoredDimension> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(StoredDimension::dimension),
                MapRegion.Stored.CODEC.listOf().fieldOf("regions").forGetter(StoredDimension::regions)
        ).apply(instance, StoredDimension::new));
    }

    public static final Codec<MapAtlas> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("palette", List.of())
                    .forGetter(atlas -> List.copyOf(atlas.palette)),
            StoredDimension.CODEC.listOf().optionalFieldOf("dimensions", List.of())
                    .forGetter(MapAtlas::storeDimensions),
            MapMarker.CODEC.listOf().optionalFieldOf("markers", List.of())
                    .forGetter(atlas -> List.copyOf(atlas.markers.values()))
    ).apply(instance, MapAtlas::fromStored));

    private List<StoredDimension> storeDimensions() {
        List<StoredDimension> out = new ArrayList<>(dimensions.size());
        for (Map.Entry<Identifier, Map<Long, MapRegion>> dim : dimensions.entrySet()) {
            List<MapRegion.Stored> stored = new ArrayList<>(dim.getValue().size());
            for (Map.Entry<Long, MapRegion> region : dim.getValue().entrySet()) {
                if (region.getValue().isEmpty()) continue;
                stored.add(region.getValue().store(region.getKey()));
            }
            if (!stored.isEmpty()) {
                out.add(new StoredDimension(dim.getKey(), stored));
            }
        }
        return out;
    }

    private static MapAtlas fromStored(List<Identifier> palette, List<StoredDimension> dims,
                                       List<MapMarker> markers) {
        MapAtlas atlas = new MapAtlas();
        for (Identifier biome : palette) {
            atlas.internBiome(biome);
        }
        for (StoredDimension dim : dims) {
            Map<Long, MapRegion> target = atlas.dimensions
                    .computeIfAbsent(dim.dimension(), k -> new LinkedHashMap<>());
            for (MapRegion.Stored stored : dim.regions()) {
                // A region that fails to decode is dropped, not fatal. The alternative is refusing
                // to load the save because one square of parchment is corrupt.
                MapRegion.decode(stored.runs())
                        .resultOrPartial(err -> LOGGER.warn(
                                "[WizardsAndBeasts] Dropping unreadable map region {} in {}: {}",
                                stored.key(), dim.dimension(), err))
                        .ifPresent(region -> target.put(stored.key(), region));
            }
        }
        for (MapMarker marker : markers) {
            atlas.markers.put(marker.id(), marker);
        }
        return atlas;
    }

    // -- Biome palette -----------------------------------------------------

    /**
     * Index for a biome, adding it to the palette if new.
     *
     * @return the index, or {@link MapRegion#UNSURVEYED} when the palette is full — at which point
     *         the tile is simply not recorded rather than aliased onto some other biome's art
     */
    public short internBiome(Identifier biome) {
        Short existing = paletteIndex.get(biome);
        if (existing != null) {
            return existing;
        }
        if (palette.size() >= Short.MAX_VALUE) {
            LOGGER.warn("[WizardsAndBeasts] Marauder's Map biome palette is full; {} will not be drawn",
                    biome);
            return MapRegion.UNSURVEYED;
        }
        short index = (short) palette.size();
        palette.add(biome);
        paletteIndex.put(biome, index);
        return index;
    }

    /** Biome id behind a palette index, or {@code null} for an out-of-range or unsurveyed index. */
    public @Nullable Identifier biomeOf(short index) {
        return index >= 0 && index < palette.size() ? palette.get(index) : null;
    }

    public List<Identifier> palette() {
        return Collections.unmodifiableList(palette);
    }

    // -- Terrain -----------------------------------------------------------

    /** The region containing a tile, or {@code null} when nothing there has been surveyed. */
    public @Nullable MapRegion region(Identifier dimension, int regionX, int regionZ) {
        Map<Long, MapRegion> dim = dimensions.get(dimension);
        return dim == null ? null : dim.get(MapGeometry.regionKey(regionX, regionZ));
    }

    /** Every surveyed region key in a dimension. Empty for a dimension never visited. */
    public Collection<Long> regionKeys(Identifier dimension) {
        Map<Long, MapRegion> dim = dimensions.get(dimension);
        return dim == null ? List.of() : Collections.unmodifiableCollection(new ArrayList<>(dim.keySet()));
    }

    public @Nullable MapRegion region(Identifier dimension, long key) {
        Map<Long, MapRegion> dim = dimensions.get(dimension);
        return dim == null ? null : dim.get(key);
    }

    public boolean isSurveyed(Identifier dimension, int tileX, int tileZ) {
        MapRegion region = region(dimension,
                MapGeometry.tileToRegion(tileX), MapGeometry.tileToRegion(tileZ));
        return region != null && region.isSurveyed(tileX, tileZ);
    }

    /**
     * Records one surveyed tile.
     *
     * @return the key of the region it landed in when this changed anything, else {@code null} —
     *         the caller uses that to mark exactly the regions that need saving and shipping
     */
    public @Nullable Long survey(Identifier dimension, int tileX, int tileZ,
                                 Identifier biome, MapRelief relief) {
        short index = internBiome(biome);
        if (index == MapRegion.UNSURVEYED) {
            return null;
        }
        long key = MapGeometry.regionKey(
                MapGeometry.tileToRegion(tileX), MapGeometry.tileToRegion(tileZ));
        MapRegion region = dimensions
                .computeIfAbsent(dimension, k -> new LinkedHashMap<>())
                .computeIfAbsent(key, k -> new MapRegion());
        return region.set(tileX, tileZ, index, relief) ? key : null;
    }

    /** Merges a decoded region in, used when the client receives one. */
    public void putRegion(Identifier dimension, long key, MapRegion region) {
        dimensions.computeIfAbsent(dimension, k -> new LinkedHashMap<>())
                .merge(key, region, (existing, incoming) -> {
                    existing.mergeFrom(incoming);
                    return existing;
                });
    }

    /** Total surveyed tiles across every dimension. Drives the "world charted" readout. */
    public int surveyedTiles() {
        int total = 0;
        for (Map<Long, MapRegion> dim : dimensions.values()) {
            for (MapRegion region : dim.values()) {
                total += region.surveyedCount();
            }
        }
        return total;
    }

    // -- Markers -----------------------------------------------------------

    public Collection<MapMarker> markers() {
        return Collections.unmodifiableCollection(markers.values());
    }

    public @Nullable MapMarker marker(UUID id) {
        return markers.get(id);
    }

    /**
     * Adds a discovered landmark unless the map already knows about that place.
     *
     * @return the marker that is now on the map — the new one, or the existing one it merged into
     */
    public MapMarker discover(Identifier type, Identifier dimension, BlockPos pos,
                              String translationKey, long gameTime) {
        Optional<MapMarker> existing = markers.values().stream()
                .filter(m -> m.source() == MapMarkerSource.DISCOVERY)
                .filter(m -> m.sameSpot(type, dimension, pos.getX(), pos.getZ(), MERGE_TOLERANCE))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        MapMarker marker = MapMarker.discovered(type, dimension, pos, translationKey, gameTime);
        markers.put(marker.id(), marker);
        return marker;
    }

    /** True when a marker of this kind is already recorded near this spot. */
    public boolean knows(Identifier type, Identifier dimension, int x, int z) {
        return markers.values().stream()
                .anyMatch(m -> m.sameSpot(type, dimension, x, z, MERGE_TOLERANCE));
    }

    public void put(MapMarker marker) {
        markers.put(marker.id(), marker);
    }

    public boolean remove(UUID id) {
        return markers.remove(id) != null;
    }

    /** How many pins one player has placed, against {@link #MAX_WAYPOINTS_PER_PLAYER}. */
    public long waypointCount(UUID owner) {
        return markers.values().stream()
                .filter(m -> m.source() == MapMarkerSource.WAYPOINT)
                .filter(m -> m.owner().map(owner::equals).orElse(false))
                .count();
    }

    /**
     * Replaces this player's death mark, keeping only the most recent.
     *
     * <p>One grave per player rather than a growing field of them: the useful question is "where
     * did I just die", and a map that answers it with forty overlapping skulls does not answer it.
     */
    public void recordDeath(Identifier dimension, BlockPos pos, UUID owner, long gameTime) {
        markers.values().removeIf(m -> m.source() == MapMarkerSource.DEATH
                && m.owner().map(owner::equals).orElse(false));
        MapMarker marker = MapMarker.death(dimension, pos, owner, gameTime);
        markers.put(marker.id(), marker);
    }

    /** Drops every marker in a dimension the server no longer has. */
    public boolean forgetDimension(Identifier dimension) {
        boolean changed = dimensions.remove(dimension) != null;
        changed |= markers.values().removeIf(m -> m.dimension().equals(dimension));
        return changed;
    }
}
