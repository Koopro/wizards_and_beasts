package at.koopro.wizardsandbeasts.azkaban.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Places the Azkaban Fortress at exactly one chunk per world: due north of (0,0),
 * at a seed-derived distance of 10 000–14 000 blocks.
 *
 * No JSON fields required beyond "type" and "salt".
 */
public final class AzkabanFixedPlacement extends StructurePlacement {

    public static final MapCodec<AzkabanFixedPlacement> CODEC = RecordCodecBuilder.mapCodec(
            instance -> StructurePlacement.placementCodec(instance)
                    .apply(instance, AzkabanFixedPlacement::new));

    public AzkabanFixedPlacement(
            Vec3i locateOffset,
            FrequencyReductionMethod frequencyReductionMethod,
            float frequency,
            int salt,
            Optional<ExclusionZone> exclusionZone) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
    }

    @Override
    protected boolean isPlacementChunk(
            @NonNull ChunkGeneratorStructureState state,
            int chunkX,
            int chunkZ) {
        long seed = state.getLevelSeed();
        RandomSource rng = RandomSource.create(seed ^ 0x415A4B41_415A4B41L);
        int distance = 10000 + rng.nextInt(4001); // [10000, 14000] blocks
        // Due north of (0,0) — negative Z in Minecraft
        ChunkPos target = new ChunkPos(new BlockPos(0, 0, -distance));
        return chunkX == target.x && chunkZ == target.z;
    }

    @Override
    public @NonNull StructurePlacementType<?> type() {
        return AzkabanWorldgenRegistries.AZKABAN_FIXED_PLACEMENT.get();
    }

    /** Computes the target chunk without requiring a live context (used by commands). */
    public static ChunkPos computeTargetChunk(long worldSeed) {
        RandomSource rng = RandomSource.create(worldSeed ^ 0x415A4B41_415A4B41L);
        int distance = 10000 + rng.nextInt(4001);
        return new ChunkPos(new BlockPos(0, 0, -distance));
    }
}
