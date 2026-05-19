package at.koopro.wizardsandbeasts.azkaban.structure;

import at.koopro.wizardsandbeasts.azkaban.data.AzkabanWorldData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * The Azkaban Fortress structure. Placement is handled entirely by
 * {@link at.koopro.wizardsandbeasts.azkaban.worldgen.AzkabanFixedPlacement} — this class
 * simply builds the piece tree when the correct chunk generates.
 */
public final class AzkabanFortressStructure extends Structure {

    public static final MapCodec<AzkabanFortressStructure> CODEC =
            StructureSettings.CODEC.xmap(AzkabanFortressStructure::new, s -> s.settings);

    private final StructureSettings settings;

    public AzkabanFortressStructure(StructureSettings settings) {
        super(settings);
        this.settings = settings;
    }

    @Override
    public @NonNull StructureType<?> type() {
        return AzkabanStructures.AZKABAN_FORTRESS.get();
    }

    @Override
    public @NonNull Optional<GenerationStub> findGenerationPoint(@NonNull GenerationContext ctx) {
        BlockPos origin = ctx.chunkPos().getWorldPosition();

        // Compute the island center (origin is SW chunk corner; fortress center is in middle of the 36-block island)
        BlockPos center = origin.offset(AzkabanFortressPiece.ISLAND / 2, 3, AzkabanFortressPiece.ISLAND / 2);

        // Warm the in-memory cache; SavedData is written in postProcess where WorldGenLevel is available
        AzkabanStructures.cachedFortressCenter = center;

        return Optional.of(new GenerationStub(origin, builder ->
                builder.addPiece(new AzkabanFortressPiece(
                        AzkabanStructures.AZKABAN_FORTRESS_PIECE.get(), origin))));
    }
}
