package at.koopro.wizardsandbeasts.settlement.structure;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

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
 */
@NullMarked
public final class SettlementStructure extends Structure {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<SettlementStructure> CODEC =
            JigsawStructure.CODEC.xmap(SettlementStructure::new, structure -> structure.jigsaw);

    private final JigsawStructure jigsaw;

    public SettlementStructure(JigsawStructure jigsaw) {
        super(jigsaw.modifiableStructureInfo().getOriginalStructureInfo().structureSettings());
        this.jigsaw = jigsaw;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!ModuleManager.isEnabled(Module.SETTLEMENTS)) {
            return Optional.empty();
        }
        // The cheap answer first: a chart's search asks this of hundreds of cells, and most fail on biome alone.
        int middleX = context.chunkPos().getMiddleBlockX();
        int middleZ = context.chunkPos().getMiddleBlockZ();
        if (!biomes().contains(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(middleX),
                QuartPos.fromBlock(context.chunkGenerator().getSeaLevel()), QuartPos.fromBlock(middleZ),
                context.randomState().sampler()))) {
            return Optional.empty();
        }
        return jigsaw.findGenerationPoint(context)
                .filter(stub -> groundSuits(context, stub.position()))
                .flatMap(this::whole);
    }

    /** Measures the ground over the footprint for {@link SettlementSite}; {@code at} is the plaza's centre. */
    private boolean groundSuits(GenerationContext context, BlockPos at) {
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        RandomState random = context.randomState();
        int x = at.getX();
        int z = at.getZ();
        int surface = generator.getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random);
        var biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface),
                QuartPos.fromBlock(z), random.sampler());
        if (!biomes().contains(biome)) {
            return false;
        }
        int lowest = surface;
        int highest = surface;
        for (int[] sample : SettlementSite.SAMPLES) {
            int h = generator.getFirstFreeHeight(x + sample[0], z + sample[1], Heightmap.Types.WORLD_SURFACE_WG, height, random);
            lowest = Math.min(lowest, h);
            highest = Math.max(highest, h);
            if (!SettlementSite.suits(true, false, lowest, highest)) {
                return false;
            }
        }
        if (isLiquid(generator, x, z, surface, height, random)) {
            return false;
        }
        for (int[] sample : SettlementSite.SAMPLES) {
            int h = generator.getFirstFreeHeight(x + sample[0], z + sample[1], Heightmap.Types.WORLD_SURFACE_WG, height, random);
            if (isLiquid(generator, x + sample[0], z + sample[1], h, height, random)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLiquid(ChunkGenerator generator, int x, int z, int surface, LevelHeightAccessor height,
                                    RandomState random) {
        return !generator.getBaseColumn(x, z, height, random).getBlock(surface - 1).getFluidState().isEmpty();
    }

    /** Runs the jigsaw assembly now, keeps its result, and refuses a site that lost a required building. */
    private Optional<GenerationStub> whole(GenerationStub stub) {
        StructurePiecesBuilder pieces = stub.getPiecesBuilder();
        Set<String> roles = new TreeSet<>();
        for (StructurePiece piece : pieces.build().pieces()) {
            if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement element) {
                roles.add(SettlementSite.roleOf(element.getTemplateLocation().getPath()));
            }
        }
        if (!SettlementSite.complete(roles)) {
            LOGGER.warn("[Settlement] Refused a site at {}: only {}", stub.position(), roles);
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(stub.position(), Either.right(pieces)));
    }

    @Override
    public StructureType<?> type() {
        return SettlementStructures.SETTLEMENT.get();
    }
}
