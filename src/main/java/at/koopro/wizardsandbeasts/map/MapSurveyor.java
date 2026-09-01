package at.koopro.wizardsandbeasts.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.map.MaraudersMapItem;
import at.koopro.wizardsandbeasts.map.discovery.MapDiscoveryRule;
import at.koopro.wizardsandbeasts.map.discovery.MapDiscoveryRules;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the world the holder walks through into parchment.
 *
 * <h2>What it will not do</h2>
 * The surveyor never loads a chunk. Everything it reads comes from {@code getChunkNow}, so the map
 * charts what the holder's own client already has streamed to it and nothing beyond. That is the
 * single rule that keeps this from being both a performance problem and a cheat: a map that could
 * force chunks would let a player chart a continent from a chair, and would generate terrain on the
 * server thread to do it.
 *
 * <p>It also never records anything below ground. A tile is one biome and one relief band sampled
 * from the surface heightmaps; caves, ravines, ore, mineshafts and the base someone dug under a
 * hill are not representable in that format at all, which is a stronger guarantee than a filter.
 *
 * <h2>Cost</h2>
 * Steady state is nearly free. A tile is surveyed once and never revisited, so a player who has
 * been standing in the same field for an hour costs one budget check per pass. The work only
 * happens at the edge of where they have been.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MapSurveyor {

    /** Ticks between passes. Half a second: fast enough to keep up with a broom. */
    private static final int SURVEY_INTERVAL_TICKS = 10;

    /** How far from the holder a tile may be surveyed, in tiles. */
    static final int SURVEY_RADIUS_TILES = 10;

    /** Tiles charted per holder per pass. Bounds the worst case at a hard number. */
    static final int TILE_BUDGET_PER_PASS = 24;

    /**
     * Chunks scanned for landmark blocks per holder per pass.
     *
     * <p>Much tighter than the tile budget because this is the one genuinely expensive pass — a
     * section that passes the palette pre-check costs a full 4096-entry count.
     */
    static final int LANDMARK_BUDGET_PER_PASS = 2;

    /**
     * Sample points per tile, as offsets inside the 16x16 chunk. The centre plus the four quadrant
     * centres: enough to notice that a tile is half water without paying for a 16x16 sweep, and
     * placed off the chunk edge so a shoreline running down a chunk border is not sampled twice on
     * the same side.
     */
    private static final int[][] SAMPLES = {{8, 8}, {4, 4}, {12, 4}, {4, 12}, {12, 12}};

    /**
     * Tile offsets within {@link #SURVEY_RADIUS_TILES}, ordered nearest-first and packed two shorts
     * to an int.
     *
     * <p>Precomputed because the ordering is what makes the budget behave: charting nearest-first
     * means the ground under the holder's feet is always on the map before the horizon is, so a
     * player who runs in a straight line leaves a solid trail rather than a dotted one.
     */
    private static final int[] RING_OFFSETS = buildRingOffsets();

    /**
     * Chunks a player has changed the landmark palette of, awaiting a re-scan.
     *
     * <p>Without this a castle only ever registers if the map arrives after it is finished. Block
     * placement is what makes a landmark, so block placement is what re-opens the question.
     */
    private static final Deque<PendingScan> LANDMARK_RESCAN = new ArrayDeque<>();

    /** A chunk awaiting a landmark re-scan, and the dimension it is in. */
    private record PendingScan(Identifier dimension, long chunkKey) {
    }

    /** Bound on the re-scan queue: a player with a fill wand must not be able to grow it forever. */
    private static final int MAX_RESCAN_QUEUE = 256;

    private static int tickCounter;

    private MapSurveyor() {
    }

    private static int[] buildRingOffsets() {
        int r = SURVEY_RADIUS_TILES;
        List<int[]> offsets = new ArrayList<>();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dz * dz <= r * r) {
                    offsets.add(new int[]{dx, dz});
                }
            }
        }
        offsets.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        int[] packed = new int[offsets.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = ((offsets.get(i)[0] & 0xFFFF) << 16) | (offsets.get(i)[1] & 0xFFFF);
        }
        return packed;
    }

    private static int offsetX(int packed) {
        return (short) (packed >> 16);
    }

    private static int offsetZ(int packed) {
        return (short) packed;
    }

    /** Queues a chunk for a landmark re-scan. Called when a landmark-palette block changes. */
    public static void queueLandmarkRescan(Identifier dimension, long chunkKey) {
        PendingScan pending = new PendingScan(dimension, chunkKey);
        if (LANDMARK_RESCAN.size() >= MAX_RESCAN_QUEUE || LANDMARK_RESCAN.contains(pending)) {
            return;
        }
        LANDMARK_RESCAN.add(pending);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS)) {
            return;
        }
        if (++tickCounter < SURVEY_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            surveyFor(player);
        }
    }

    private static void surveyFor(ServerPlayer player) {
        ItemStack stack = MaraudersMapItem.findCarried(player);
        if (stack.isEmpty()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        UUID mapId = MaraudersMapItem.mapId(stack);
        if (mapId == null) {
            return; // never activated; an unbonded map charts nothing
        }
        if (!MaraudersMapItem.isTrusted(stack, player.getUUID())) {
            return; // the map does not draw for someone it does not trust
        }

        MaraudersMapAtlasStore store = MaraudersMapAtlasStore.get(level);
        MapAtlas atlas = store.atlas(mapId);
        Identifier dimension = level.dimension().identifier();

        boolean changed = surveyTiles(player, level, atlas, mapId, dimension);
        changed |= discoverStructures(player, level, atlas, mapId, dimension);
        changed |= scanLandmarks(player, level, atlas, mapId, dimension);

        if (changed) {
            store.markChanged();
        }
    }

    // -- Terrain -----------------------------------------------------------

    private static boolean surveyTiles(ServerPlayer player, ServerLevel level, MapAtlas atlas,
                                       UUID mapId, Identifier dimension) {
        int centerTileX = MapGeometry.blockToTile(player.getBlockX());
        int centerTileZ = MapGeometry.blockToTile(player.getBlockZ());
        int paletteBefore = atlas.palette().size();
        int budget = TILE_BUDGET_PER_PASS;
        int landmarkBudget = LANDMARK_BUDGET_PER_PASS;
        boolean changed = false;

        for (int packed : RING_OFFSETS) {
            if (budget <= 0) {
                break;
            }
            int tileX = centerTileX + offsetX(packed);
            int tileZ = centerTileZ + offsetZ(packed);
            if (atlas.isSurveyed(dimension, tileX, tileZ)) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(tileX, tileZ);
            if (chunk == null) {
                continue; // not loaded: the map has not been carried close enough yet
            }
            budget--;
            Long dirty = surveyTile(level, chunk, atlas, dimension, tileX, tileZ);
            if (dirty != null) {
                changed = true;
                MapSessions.invalidateRegion(mapId, dimension, dirty);
                // Bounded separately from the tile budget: charting a tile is a handful of array
                // reads, but a landmark scan can cost a full 4096-entry section count.
                if (landmarkBudget > 0) {
                    landmarkBudget--;
                    scanLandmarksIn(chunk, atlas, mapId, dimension, level);
                }
            }
        }

        if (atlas.palette().size() != paletteBefore) {
            MapSessions.invalidatePalette(mapId);
        }
        return changed;
    }

    /**
     * Charts one tile from an already-loaded chunk.
     *
     * @return the key of the region it changed, or {@code null} when nothing changed
     */
    private static @Nullable Long surveyTile(ServerLevel level, LevelChunk chunk, MapAtlas atlas,
                                             Identifier dimension, int tileX, int tileZ) {
        int baseX = MapGeometry.tileToBlock(tileX);
        int baseZ = MapGeometry.tileToBlock(tileZ);
        int seaLevel = level.getSeaLevel();

        int surfaceSum = 0;
        int floorSum = 0;
        boolean anyWater = false;
        Holder<Biome> dominant = null;

        for (int[] sample : SAMPLES) {
            int x = baseX + sample[0];
            int z = baseZ + sample[1];
            int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            surfaceSum += surface;
            floorSum += floor;
            if (surface > floor) {
                anyWater = true;
            }
            if (dominant == null) {
                // The centre sample decides the biome; the rest only shape the relief. Averaging
                // biomes is not a thing, and a majority vote over five points costs a map
                // allocation per tile to break ties that a hand-drawn tile cannot express anyway.
                dominant = chunk.getNoiseBiome(
                        QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z));
            }
        }

        if (dominant == null) {
            return null;
        }
        Identifier biomeId = dominant.unwrapKey().map(key -> key.identifier()).orElse(null);
        if (biomeId == null) {
            return null; // an unregistered inline biome has no stable name to store
        }

        int surfaceY = surfaceSum / SAMPLES.length;
        int floorY = floorSum / SAMPLES.length;
        MapRelief relief = MapRelief.classify(surfaceY, floorY, seaLevel, anyWater);
        return atlas.survey(dimension, tileX, tileZ, biomeId, relief);
    }

    // -- Structures --------------------------------------------------------

    /**
     * Marks worldgen structures the holder is standing in or walking over.
     *
     * <p>Checked at the holder's own position rather than across the surveyed area, which is both
     * the cheap way and the right way: a structure is discovered by being there. Sweeping the
     * survey radius would hand the player a list of every fortress within 160 blocks the moment
     * they unfolded the map.
     */
    private static boolean discoverStructures(ServerPlayer player, ServerLevel level, MapAtlas atlas,
                                              UUID mapId, Identifier dimension) {
        List<MapDiscoveryRule.FromStructure> rules = MapDiscoveryRules.structures();
        if (rules.isEmpty()) {
            return false;
        }
        BlockPos pos = player.blockPosition();
        Map<Structure, ?> present = level.structureManager().getAllStructuresAt(pos);
        if (present.isEmpty()) {
            return false;
        }

        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        int seaLevel = level.getSeaLevel();
        boolean changed = false;

        for (Structure structure : present.keySet()) {
            Identifier structureId = registry.getKey(structure);
            if (structureId == null) {
                continue;
            }
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (!start.isValid()) {
                continue;
            }
            BoundingBox box = start.getBoundingBox();

            for (MapDiscoveryRule.FromStructure rule : rules) {
                if (!matches(rule, registry, structure, structureId)) {
                    continue;
                }
                if (!revealed(rule.reveal(), box, pos, seaLevel)) {
                    continue;
                }
                BlockPos center = box.getCenter();
                if (atlas.knows(rule.marker(), dimension, center.getX(), center.getZ())) {
                    continue;
                }
                atlas.discover(rule.marker(), dimension, center, rule.label(), level.getGameTime());
                MapSessions.invalidateMarkers(mapId);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean matches(MapDiscoveryRule.FromStructure rule, Registry<Structure> registry,
                                   Structure structure, Identifier structureId) {
        if (rule.structure().isPresent()) {
            return rule.structure().get().identifier().equals(structureId);
        }
        if (rule.structureTag().isPresent()) {
            return registry.wrapAsHolder(structure).is(rule.structureTag().get());
        }
        return false;
    }

    /**
     * Whether the holder has earned this structure's marker.
     *
     * <p>{@link MapDiscoveryRule.Reveal#AUTO} reads the structure's own geometry: anything topping
     * out at or above sea level is visible country and is marked from above, anything wholly below
     * it has to be entered. That keeps strongholds, mineshafts and the Chamber of Secrets off the
     * parchment until someone has actually found them, without a per-structure flag to maintain.
     */
    private static boolean revealed(MapDiscoveryRule.Reveal reveal, BoundingBox box, BlockPos pos,
                                    int seaLevel) {
        return switch (reveal) {
            case SURFACE -> true;
            case ENTERED -> box.isInside(pos);
            case AUTO -> box.maxY() >= seaLevel || box.isInside(pos);
        };
    }

    // -- Player-built landmarks --------------------------------------------

    /** Works the re-scan queue: chunks whose landmark palette a player has just changed. */
    private static boolean scanLandmarks(ServerPlayer player, ServerLevel level, MapAtlas atlas,
                                         UUID mapId, Identifier dimension) {
        if (MapDiscoveryRules.blocks().isEmpty() || LANDMARK_RESCAN.isEmpty()) {
            return false;
        }
        boolean changed = false;
        int budget = LANDMARK_BUDGET_PER_PASS;
        // Peeked rather than polled until the dimension matches: an entry for another world is
        // not this holder's to work, and polling it would throw it away.
        int examined = 0;
        int size = LANDMARK_RESCAN.size();
        while (budget > 0 && examined < size) {
            PendingScan pending = LANDMARK_RESCAN.poll();
            if (pending == null) {
                break;
            }
            examined++;
            if (!pending.dimension().equals(dimension)) {
                LANDMARK_RESCAN.addLast(pending);
                continue;
            }
            budget--;
            LevelChunk chunk = level.getChunkSource()
                    .getChunkNow(ChunkPos.getX(pending.chunkKey()), ChunkPos.getZ(pending.chunkKey()));
            if (chunk == null) {
                continue; // unloaded since it was queued; the next build there will re-queue it
            }
            changed |= scanLandmarksIn(chunk, atlas, mapId, dimension, level);
        }
        return changed;
    }

    /**
     * Counts landmark-palette blocks in one chunk and plants a marker where a rule's threshold is
     * met.
     *
     * <p>This is how Hogwarts gets onto the map. The mod does not generate a castle — it ships the
     * stone, and players build with it — so the only honest way to find one is to notice that
     * somebody has laid a great deal of it in one place. It is also why the marker is a castle and
     * not a generic structure pin: the palette a player chose is the statement of what they built.
     *
     * <p>The palette pre-check does the real work. {@link LevelChunkSection#maybeHas} tests the
     * section's palette, which is a handful of entries, so the expensive per-block count only runs
     * on sections that genuinely contain the blocks — in practice, none of them.
     */
    private static boolean scanLandmarksIn(LevelChunk chunk, MapAtlas atlas, UUID mapId,
                                           Identifier dimension, ServerLevel level) {
        List<MapDiscoveryRule.FromBlocks> rules = MapDiscoveryRules.blocks();
        if (rules.isEmpty()) {
            return false;
        }
        boolean changed = false;

        for (MapDiscoveryRule.FromBlocks rule : rules) {
            int found = 0;
            for (LevelChunkSection section : chunk.getSections()) {
                if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(rule.blocks()))) {
                    continue;
                }
                found += countIn(section, rule);
                if (found >= rule.threshold()) {
                    break;
                }
            }
            if (found < rule.threshold()) {
                continue;
            }
            BlockPos center = chunk.getPos().getMiddleBlockPosition(
                    chunk.getHeight(Heightmap.Types.WORLD_SURFACE,
                            chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()));
            if (atlas.knows(rule.marker(), dimension, center.getX(), center.getZ())) {
                continue;
            }
            atlas.discover(rule.marker(), dimension, center, rule.label(), level.getGameTime());
            MapSessions.invalidateMarkers(mapId);
            changed = true;
        }
        return changed;
    }

    private static int countIn(LevelChunkSection section, MapDiscoveryRule.FromBlocks rule) {
        int[] total = {0};
        section.getStates().count((BlockState state, int count) -> {
            if (state.is(rule.blocks())) {
                total[0] += count;
            }
        });
        return total[0];
    }
}
