package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The chart that comes with an expedition: a filled map to the nearest site of the kind the contract names whose
 * treasure has not been taken yet, the way a cartographer sells an explorer's map. It is the vanilla map, marked with
 * an X — the mod draws no map of its own and generates no tomb: the world already has them.
 *
 * <p>The search is {@link SiteSearch} over the {@link TreasureSiteLedger}. It used to be vanilla's
 * {@code findNearestMapStructure(.., false)}, which returns the nearest site every time: a second expedition from
 * the bank was drawn to the tomb the first one had emptied, where no artefact could ever be found again.
 */
@NullMarked
public final class TreasureCharts {

    /**
     * Rings of the structure's placement grid searched for a site (vanilla counts its map radius the same way, in grid
     * cells rather than chunks; its explorer maps use 50–100).
     */
    static final int SEARCH_RINGS = 64;

    private TreasureCharts() {}

    /** A contract was taken: if it is treasure work, hand over a chart to each site it names. */
    public static void onContractAccepted(ServerPlayer player, ContractDefinition contract) {
        for (Identifier siteId : Expeditions.sitesOf(contract)) {
            TreasureSite site = TreasureSites.get(siteId);
            if (site == null) {
                continue;
            }
            ItemStack chart = chart(player, siteId, site);
            if (chart == null) {
                PlayerFeedback.actionBar(player, Component.translatable("treasure.wizards_and_beasts.no_chart",
                        siteName(siteId)));
                continue;
            }
            at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, chart);
        }
    }

    /** The chart, or {@code null} when the player is not in the Overworld or no such site is near. */
    public static @Nullable ItemStack chart(ServerPlayer player, Identifier siteId, TreasureSite site) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, site.structures());
        SiteSearch.Site found = nearestUnspent(level, tag, player.blockPosition());
        if (found == null) {
            return null;
        }
        return draw(level, new BlockPos(found.locateX(), 0, found.locateZ()),
                Component.translatable("item.wizards_and_beasts.gringotts_chart", siteName(siteId)));
    }

    /** A filled explorer's map centred on {@code target} with an X on it, the way a cartographer draws one. */
    public static ItemStack draw(ServerLevel level, BlockPos target, Component name) {
        ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, target, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, name);
        return map;
    }

    /**
     * The nearest site in {@code tag} whose treasure has not been taken, or null. Only random-spread placements are
     * walked: every treasure site is one, and a ring placement (strongholds) has no grid to walk.
     */
    static SiteSearch.@Nullable Site nearestUnspent(ServerLevel level, TagKey<Structure> tag, BlockPos origin) {
        return nearest(level, tag, origin, SEARCH_RINGS, TreasureSiteLedger.get(level)::isSpent);
    }

    /** The nearest site in {@code tag} within {@code rings} grid rings that {@code passOver} does not reject, or null. */
    public static SiteSearch.@Nullable Site nearest(ServerLevel level, TagKey<Structure> tag, BlockPos origin, int rings,
                                                   java.util.function.Predicate<SiteSearch.Site> passOver) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(tag);
        if (structures.isEmpty()) {
            return null;
        }
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        Map<RandomSpreadStructurePlacement, List<Holder<Structure>>> byPlacement = new LinkedHashMap<>();
        for (Holder<Structure> holder : structures.get()) {
            for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
                if (placement instanceof RandomSpreadStructurePlacement spread) {
                    byPlacement.computeIfAbsent(spread, p -> new ArrayList<>()).add(holder);
                }
            }
        }
        if (byPlacement.isEmpty()) {
            return null;
        }
        List<RandomSpreadStructurePlacement> placements = new ArrayList<>(byPlacement.keySet());
        List<SiteSearch.Grid> grids = new ArrayList<>(placements.size());
        for (RandomSpreadStructurePlacement placement : placements) {
            grids.add(new SiteSearch.Grid(placement.spacing()));
        }
        StructureManager manager = level.structureManager();
        long seed = state.getLevelSeed();
        return SiteSearch.nearest(SectionPos.blockToSectionCoord(origin.getX()),
                SectionPos.blockToSectionCoord(origin.getZ()), grids, rings, (grid, cellX, cellZ) -> {
                    RandomSpreadStructurePlacement placement = placements.get(grid);
                    ChunkPos potential = placement.getPotentialStructureChunk(seed, cellX, cellZ);
                    return siteAt(level, manager, placement, byPlacement.get(placement), potential);
                }, passOver);
    }

    /** The same search as {@link #nearest}, one cell at a time; null when {@code tag} has no walkable grid. */
    public static SiteSearch.@Nullable Walk walk(ServerLevel level, TagKey<Structure> tag, BlockPos origin, int rings) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(tag);
        if (structures.isEmpty()) {
            return null;
        }
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        for (Holder<Structure> holder : structures.get()) {
            for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
                if (placement instanceof RandomSpreadStructurePlacement spread) {
                    return new SiteSearch.Walk(SectionPos.blockToSectionCoord(origin.getX()),
                            SectionPos.blockToSectionCoord(origin.getZ()), spread.spacing(), rings,
                            new AskedCell(level, holder, spread, state));
                }
            }
        }
        return null;
    }

    /**
     * Answers a cell without holding up the server. Whether a start stands is the dear part, up to tens of seconds on a
     * busy machine for a hamlet, and vanilla already works that out on its generation threads for any chunk that asks. So
     * this asks for the chunk, and comes back on later ticks until it has it. The server thread only reads the finished
     * chunk: no world access moves off it.
     *
     * <p>{@code getChunkFuture} waits for its chunk when the server thread calls it, which is the whole stall over again,
     * and from any other thread only queues the request on the server thread and returns. So the request is made from a
     * worker, which does nothing else; measured, the first version of this called it directly and still held the tick.
     */
    private static final class AskedCell implements SiteSearch.Probe {

        private static final int TICKET_LEVEL = ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS);

        private final ServerLevel level;
        private final Holder<Structure> structure;
        private final RandomSpreadStructurePlacement spread;
        private final ChunkGeneratorStructureState state;
        private @Nullable CompletableFuture<ChunkResult<ChunkAccess>> asked;

        private AskedCell(ServerLevel level, Holder<Structure> structure, RandomSpreadStructurePlacement spread,
                          ChunkGeneratorStructureState state) {
            this.level = level;
            this.structure = structure;
            this.spread = spread;
            this.state = state;
        }

        @Override
        public SiteSearch.@Nullable Site at(int grid, int cellX, int cellZ) {
            ChunkPos potential = spread.getPotentialStructureChunk(state.getLevelSeed(), cellX, cellZ);
            ServerChunkCache chunks = level.getChunkSource();
            if (asked == null) {
                // Two cheap refusals first, so land nothing could stand on is not generated just to hear it. An exclusion
                // zone is the placement's own check, and the biome is read where a surface site reads it.
                if (!spread.isStructureChunk(state, potential.x, potential.z) || !biomeSuits(potential)) {
                    return null;
                }
                asked = CompletableFuture.supplyAsync(() -> chunks.getChunkFuture(potential.x, potential.z,
                        ChunkStatus.STRUCTURE_STARTS, true), Util.backgroundExecutor()).thenCompose(chunk -> chunk);
            }
            // The request's own ticket lasts a tick; without a fresh one each turn the generation is dropped half done.
            chunks.addTicket(new Ticket(TicketType.UNKNOWN, TICKET_LEVEL), potential);
            if (!asked.isDone()) {
                return SiteSearch.PENDING;
            }
            ChunkAccess done = asked.join().orElse(null);
            asked = null;
            if (done == null) {
                return null;
            }
            StructureStart found = level.structureManager().getStartForStructure(SectionPos.bottomOf(done), structure.value(), done);
            if (found == null || !found.isValid()) {
                return null;
            }
            ChunkPos start = found.getChunkPos();
            BlockPos locate = spread.getLocatePos(start);
            return new SiteSearch.Site(structure.unwrapKey().map(key -> key.identifier().toString()).orElse(""), start.x, start.z,
                    locate.getX(), locate.getZ());
        }

        private boolean biomeSuits(ChunkPos chunk) {
            var generator = level.getChunkSource().getGenerator();
            return structure.value().biomes().contains(generator.getBiomeSource().getNoiseBiome(
                    QuartPos.fromBlock(chunk.getMiddleBlockX()), QuartPos.fromBlock(generator.getSeaLevel()),
                    QuartPos.fromBlock(chunk.getMiddleBlockZ()), level.getChunkSource().randomState().sampler()));
        }
    }

    /** What vanilla's {@code getStructureGeneratingAt} does, minus the reference counting. */
    private static SiteSearch.@Nullable Site siteAt(ServerLevel level, StructureManager manager,
                                                   RandomSpreadStructurePlacement placement,
                                                   List<Holder<Structure>> structures, ChunkPos potential) {
        for (Holder<Structure> holder : structures) {
            StructureCheckResult check = manager.checkStructurePresence(potential, holder.value(), placement, false);
            if (check == StructureCheckResult.START_NOT_PRESENT) {
                continue;
            }
            ChunkPos start = potential;
            if (check != StructureCheckResult.START_PRESENT) {
                ChunkAccess chunk = level.getChunk(potential.x, potential.z, ChunkStatus.STRUCTURE_STARTS);
                StructureStart found = manager.getStartForStructure(SectionPos.bottomOf(chunk), holder.value(), chunk);
                if (found == null || !found.isValid()) {
                    continue;
                }
                start = found.getChunkPos();
            }
            String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
            BlockPos locate = placement.getLocatePos(start);
            return new SiteSearch.Site(id, start.x, start.z, locate.getX(), locate.getZ());
        }
        return null;
    }

    /**
     * A treasure chest at {@code pos} has rolled its loot: the site it belongs to is spent. Called for every roll of a
     * treasure-site loot table, opened by a player or emptied by a hopper alike.
     */
    public static void spendSiteAt(ServerLevel level, BlockPos pos, TreasureSite site) {
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, site.structures());
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, tag);
        if (!start.isValid()) {
            return;
        }
        Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
        if (id == null) {
            return;
        }
        ChunkPos chunk = start.getChunkPos();
        TreasureSiteLedger.get(level).spend(new SiteSearch.Site(id.toString(), chunk.x, chunk.z, 0, 0).key());
    }

    public static Component siteName(Identifier siteId) {
        return Component.translatable("subject." + siteId.getNamespace() + "." + siteId.getPath());
    }
}
