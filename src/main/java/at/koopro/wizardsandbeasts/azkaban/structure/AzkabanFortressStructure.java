package at.koopro.wizardsandbeasts.azkaban.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public final class AzkabanFortressStructure extends Structure {

    public static final MapCodec<AzkabanFortressStructure> CODEC =
            Structure.simpleCodec(AzkabanFortressStructure::new);

    public AzkabanFortressStructure(@NonNull StructureSettings settings) {
        super(settings);
    }

    @Override
    public @NonNull StructureType<?> type() {
        return AzkabanStructures.AZKABAN_FORTRESS.get();
    }

    @Override
    protected @NonNull Optional<GenerationStub> findGenerationPoint(@NonNull GenerationContext context) {
        BlockPos origin = context.chunkPos().getMiddleBlockPosition(
                context.chunkGenerator().getSeaLevel() + 1);
        return Optional.of(new GenerationStub(origin, builder ->
                builder.addPiece(new AzkabanFortressPiece(
                        AzkabanStructures.AZKABAN_FORTRESS_PIECE.get(), origin))));
    }
}
