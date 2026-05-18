package at.koopro.wizardsandbeasts.azkaban.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

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
        // Enforce minimum distance from world origin (≈ 8000 blocks = 500 chunks)
        net.minecraft.world.level.ChunkPos cp = ctx.chunkPos();
        if (Math.abs(cp.x) < 500 && Math.abs(cp.z) < 500) {
            return Optional.empty();
        }

        net.minecraft.core.BlockPos origin = cp.getWorldPosition();
        return Optional.of(new GenerationStub(origin, builder ->
                builder.addPiece(new AzkabanFortressPiece(
                        AzkabanStructures.AZKABAN_FORTRESS_PIECE.get(), origin))));
    }
}
