package at.koopro.wizardsandbeasts.settlement.structure;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A wizarding settlement: a vanilla jigsaw structure whose generation is gated on {@link Module#SETTLEMENTS} and on the
 * ground and pieces it actually gets ({@link SettlementSite}). Registration is never gated.
 *
 * <p>Composition for the same reason as {@code ChamberOfSecretsStructure}: {@link JigsawStructure} is {@code final}.
 * The codec is vanilla's, mapped onto this wrapper, so every jigsaw field and its range checks stay vanilla's.
 *
 * <p>Gating only suppresses new placement. A hamlet already in a world's chunks stays there.
 *
 * <p>Ground the site check cannot see is met after placing, in {@link #afterPlace}; see {@link SettlementGround}.
 *
 * <p>Nothing but a placement's exclusion zone keeps structures apart, so a ruined portal, or the Chamber of Secrets, placed in
 * a later step with no terrain adaptation, writes through a finished hamlet where the two meet. Hamlets give way
 * ({@link #occupied}): the other structure's placement stays as it was.
 */
@NullMarked
public final class SettlementStructure extends Structure {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far off a start chunk can lie and still reach a hamlet: the eight a start can reference, and the hamlet's half-width. */
    private static final int STRUCTURE_REACH_CHUNKS = 12;
    /** The biome readings around a start: past its chunk's edge, across, and up the column. */
    private static final int BIOME_PAST_CHUNK = 16;
    private static final int BIOME_GRID = 12;
    private static final int BIOME_LADDER = 64;
    /** What terrain adaptation adds around a hamlet's pieces: {@link Structure#adjustBoundingBox}. */
    private static final int TERRAIN_MARGIN = 12;

    public static final MapCodec<SettlementStructure> CODEC =
            JigsawStructure.CODEC.xmap(SettlementStructure::new, structure -> structure.jigsaw);

    private final JigsawStructure jigsaw;

    public SettlementStructure(JigsawStructure jigsaw) {
        super(jigsaw.modifiableStructureInfo().getOriginalStructureInfo().structureSettings());
        this.jigsaw = jigsaw;
    }

    /** What the rules made of one candidate: why it was refused, or the stub that stands. */
    public record Assessment(SettlementSite.Verdict verdict, Optional<GenerationStub> stub) {
        private static Assessment refused(SettlementSite.Verdict verdict) {
            return new Assessment(verdict, Optional.empty());
        }
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return ModuleManager.isEnabled(Module.SETTLEMENTS) ? assess(context).stub() : Optional.empty();
    }

    /** Every site rule, in one place, so that a probe reads the reasons rather than guessing at them. */
    public Assessment assess(GenerationContext context) {
        // The cheap answer first: a chart's search asks this of hundreds of cells, and most fail on biome alone.
        int middleX = context.chunkPos().getMiddleBlockX();
        int middleZ = context.chunkPos().getMiddleBlockZ();
        if (!biomes().contains(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(middleX),
                QuartPos.fromBlock(context.chunkGenerator().getSeaLevel()), QuartPos.fromBlock(middleZ),
                context.randomState().sampler()))) {
            return Assessment.refused(SettlementSite.Verdict.BIOME);
        }
        Optional<GenerationStub> stub = jigsaw.findGenerationPoint(context);
        if (stub.isEmpty()) {
            return Assessment.refused(SettlementSite.Verdict.NO_ROOM);
        }
        SettlementSite.Verdict ground = ground(context, stub.get().position());
        return ground == SettlementSite.Verdict.STANDS ? whole(context, stub.get()) : Assessment.refused(ground);
    }

    /** Measures the ground over the footprint for {@link SettlementSite}; {@code at} is the plaza's centre. */
    private SettlementSite.Verdict ground(GenerationContext context, BlockPos at) {
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        RandomState random = context.randomState();
        int x = at.getX();
        int z = at.getZ();
        int surface = generator.getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random);
        var biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface),
                QuartPos.fromBlock(z), random.sampler());
        if (!biomes().contains(biome)) {
            return SettlementSite.Verdict.BIOME;
        }
        int lowest = surface;
        int highest = surface;
        for (int[] sample : SettlementSite.SAMPLES) {
            int h = generator.getFirstFreeHeight(x + sample[0], z + sample[1], Heightmap.Types.WORLD_SURFACE_WG, height, random);
            lowest = Math.min(lowest, h);
            highest = Math.max(highest, h);
            if (!SettlementSite.suits(true, false, lowest, highest)) {
                return SettlementSite.Verdict.RELIEF;
            }
        }
        if (isLiquid(generator, x, z, surface, height, random)) {
            return SettlementSite.Verdict.LIQUID;
        }
        for (int[] sample : SettlementSite.SAMPLES) {
            int h = generator.getFirstFreeHeight(x + sample[0], z + sample[1], Heightmap.Types.WORLD_SURFACE_WG, height, random);
            if (isLiquid(generator, x + sample[0], z + sample[1], h, height, random)) {
                return SettlementSite.Verdict.LIQUID;
            }
        }
        return SettlementSite.Verdict.STANDS;
    }

    private static boolean isLiquid(ChunkGenerator generator, int x, int z, int surface, LevelHeightAccessor height,
                                    RandomState random) {
        return !generator.getBaseColumn(x, z, height, random).getBlock(surface - 1).getFluidState().isEmpty();
    }

    /** Runs the jigsaw assembly now, keeps its result, and refuses a site that lost a required building. */
    private Assessment whole(GenerationContext context, GenerationStub stub) {
        StructurePiecesBuilder pieces = stub.getPiecesBuilder();
        Set<String> roles = new TreeSet<>();
        for (StructurePiece piece : pieces.build().pieces()) {
            if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement element) {
                roles.add(SettlementSite.roleOf(element.getTemplateLocation().getPath()));
            }
        }
        if (!SettlementSite.complete(roles)) {
            LOGGER.warn("[Settlement] Refused a site at {}: only {}", stub.position(), roles);
            return Assessment.refused(SettlementSite.Verdict.INCOMPLETE);
        }
        if (occupied(context, pieces.getBoundingBox())) {
            return Assessment.refused(SettlementSite.Verdict.OCCUPIED);
        }
        return new Assessment(SettlementSite.Verdict.STANDS, Optional.of(new GenerationStub(stub.position(), Either.right(pieces))));
    }

    /**
     * Whether another structure truly starts on {@code hamlet}'s ground. Its placement says where one may start and its own
     * check says whether it does, module included, so a hamlet gives way to nothing but a real structure. Only spread
     * placements can land there: stronghold rings run deep and would have to be searched for, and Azkaban stays in the ocean.
     */
    private boolean occupied(GenerationContext context, BoundingBox hamlet) {
        var sets = context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        ChunkGeneratorStructureState state = context.chunkGenerator().createState(sets, context.randomState(), context.seed());
        BoundingBox claimed = hamlet.inflatedBy(TERRAIN_MARGIN);
        ChunkPos here = context.chunkPos();
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            for (int x = here.x - STRUCTURE_REACH_CHUNKS; x <= here.x + STRUCTURE_REACH_CHUNKS; x++) {
                for (int z = here.z - STRUCTURE_REACH_CHUNKS; z <= here.z + STRUCTURE_REACH_CHUNKS; z++) {
                    if (spread.isStructureChunk(state, x, z) && startsOn(context, set.value(), new ChunkPos(x, z), claimed)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Any of a set's structures counts: generation picks one by weight, and asking of the rest costs a site at worst. */
    private boolean startsOn(GenerationContext context, StructureSet set, ChunkPos start, BoundingBox claimed) {
        Set<Holder<Biome>> around = biomesAround(context, start);
        for (StructureSet.StructureSelectionEntry entry : set.structures()) {
            Structure other = entry.structure().value();
            if (other == this || around.stream().noneMatch(other.biomes()::contains)) {
                continue;
            }
            var found = other.findValidGenerationPoint(new GenerationContext(context.registryAccess(), context.chunkGenerator(),
                    context.biomeSource(), context.randomState(), context.structureTemplateManager(), context.seed(), start,
                    context.heightAccessor(), other.biomes()::contains));
            if (found.isPresent()) {
                StructurePiecesBuilder pieces = found.get().getPiecesBuilder();
                if (!pieces.isEmpty() && pieces.getBoundingBox().intersects(claimed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The cheap no. A structure's own check reads the terrain first and the biome last, so a desert portal costs a forest
     * site as much as a real one would. A start can stand outside its chunk, and at a height of the structure's choosing,
     * so this reads past the chunk's edges and up the whole column.
     */
    private static Set<Holder<Biome>> biomesAround(GenerationContext context, ChunkPos start) {
        LevelHeightAccessor height = context.heightAccessor();
        var sampler = context.randomState().sampler();
        Set<Holder<Biome>> biomes = new HashSet<>();
        for (int x = start.getMinBlockX() - BIOME_PAST_CHUNK; x <= start.getMaxBlockX() + BIOME_PAST_CHUNK; x += BIOME_GRID) {
            for (int z = start.getMinBlockZ() - BIOME_PAST_CHUNK; z <= start.getMaxBlockZ() + BIOME_PAST_CHUNK; z += BIOME_GRID) {
                for (int y = height.getMinY(); y <= height.getMaxY(); y += BIOME_LADDER) {
                    biomes.add(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                            QuartPos.fromBlock(z), sampler));
                }
            }
        }
        return biomes;
    }

    /** Runs as each chunk of the hamlet is placed, so caves and ravines, carved by now, can be met: see the class. */
    @Override
    public void afterPlace(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                           BoundingBox chunkBox, ChunkPos chunk, PiecesContainer pieces) {
        int floorY = Integer.MAX_VALUE;
        for (StructurePiece piece : pieces.pieces()) {
            if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement element
                    && SettlementSite.roleOf(element.getTemplateLocation().getPath()).equals("plaza")) {
                floorY = piece.getBoundingBox().minY();
            }
        }
        if (floorY == Integer.MAX_VALUE) {
            return;
        }
        SettlementGround.foot(level, chunkBox, floorY, pieces::isInsidePiece);
        for (StructurePiece piece : pieces.pieces()) {
            // A cellar is a floor of its own, lower down: the Undercroft should not hang over a ravine either.
            BoundingBox box = piece.getBoundingBox();
            if (box.minY() < floorY && box.intersects(chunkBox)) {
                SettlementGround.foot(level, chunkBox, box.minY(), box::isInside);
            }
        }
    }

    @Override
    public StructureType<?> type() {
        return SettlementStructures.SETTLEMENT.get();
    }
}
