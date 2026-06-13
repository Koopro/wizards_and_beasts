package at.koopro.wizardsandbeasts.azkaban.structure;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stronghold-style placement that generates <em>exactly one</em> structure per
 * world, pinned to a target biome.
 *
 * <p>The single target chunk is derived deterministically from the level seed
 * and {@code salt}: a random angle plus a random radius inside the
 * {@code [min_chunk_radius, max_chunk_radius]} band picks a candidate point,
 * which is then snapped to the nearest {@code target_biomes} match via
 * {@link net.minecraft.world.level.biome.BiomeSource#findBiomeHorizontal}. Up
 * to {@value #ATTEMPTS} candidates are tried, so a world with any ocean at all
 * is effectively guaranteed a hit. The result is cached per level seed —
 * {@code /locate structure} resolves instantly after the first query.
 *
 * <p>Requires the {@code ChunkGeneratorStructureState.biomeSource} access
 * transformer (see {@code META-INF/accesstransformer.cfg}).
 */
public final class OnePerWorldPlacement extends StructurePlacement {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Block radius handed to findBiomeHorizontal around each candidate point. */
    private static final int BIOME_SNAP_RADIUS = 256;
    /** Sample at sea level so the multi-noise sampler returns surface biomes, not cave biomes. */
    private static final int SAMPLE_Y = 64;
    /** Candidate points tried before giving up (DFU codec chain is full at 8 fields, so not data-driven). */
    private static final int ATTEMPTS = 64;

    public static final MapCodec<OnePerWorldPlacement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            placementCodec(instance).and(instance.group(
                    RegistryCodecs.homogeneousList(Registries.BIOME)
                            .fieldOf("target_biomes").forGetter((OnePerWorldPlacement p) -> p.targetBiomes),
                    ExtraCodecs.POSITIVE_INT
                            .fieldOf("min_chunk_radius").forGetter((OnePerWorldPlacement p) -> p.minChunkRadius),
                    ExtraCodecs.POSITIVE_INT
                            .fieldOf("max_chunk_radius").forGetter((OnePerWorldPlacement p) -> p.maxChunkRadius)
            )).apply(instance, OnePerWorldPlacement::new));

    private final HolderSet<Biome> targetBiomes;
    private final int minChunkRadius;
    private final int maxChunkRadius;

    /** Target chunk per level seed. Placement instances live for a datapack-load. */
    private final Map<Long, ChunkPos> targetBySeed = new ConcurrentHashMap<>();

    public OnePerWorldPlacement(@NonNull Vec3i locateOffset,
                                @NonNull FrequencyReductionMethod frequencyReductionMethod,
                                float frequency,
                                int salt,
                                @NonNull Optional<ExclusionZone> exclusionZone,
                                @NonNull HolderSet<Biome> targetBiomes,
                                int minChunkRadius,
                                int maxChunkRadius) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        this.targetBiomes = targetBiomes;
        this.minChunkRadius = minChunkRadius;
        this.maxChunkRadius = Math.max(maxChunkRadius, minChunkRadius);
    }

    @Override
    protected boolean isPlacementChunk(@NonNull ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        ChunkPos target = targetBySeed.computeIfAbsent(state.getLevelSeed(), seed -> computeTarget(state, seed));
        return target.x == chunkX && target.z == chunkZ;
    }

    private ChunkPos computeTarget(ChunkGeneratorStructureState state, long seed) {
        RandomSource random = RandomSource.create(seed ^ (long) salt());
        ChunkPos fallback = null;

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int radius = minChunkRadius + random.nextInt(maxChunkRadius - minChunkRadius + 1);
            int blockX = (int) Math.round(Math.cos(angle) * radius) * 16 + 8;
            int blockZ = (int) Math.round(Math.sin(angle) * radius) * 16 + 8;
            if (fallback == null) {
                fallback = new ChunkPos(blockX >> 4, blockZ >> 4);
            }

            Pair<BlockPos, Holder<Biome>> hit = state.biomeSource.findBiomeHorizontal(
                    blockX, SAMPLE_Y, blockZ, BIOME_SNAP_RADIUS,
                    targetBiomes::contains, random, state.randomState().sampler());
            if (hit != null) {
                return new ChunkPos(hit.getFirst());
            }
        }

        LOGGER.warn("[WizardsAndBeasts] OnePerWorldPlacement found no target biome in {} attempts "
                + "(seed {}, salt {}); falling back to an unsnapped position — the structure's own "
                + "biome check may reject it.", ATTEMPTS, seed, salt());
        return fallback;
    }

    @Override
    public @NonNull StructurePlacementType<?> type() {
        return AzkabanStructures.ONE_PER_WORLD_PLACEMENT.get();
    }
}
