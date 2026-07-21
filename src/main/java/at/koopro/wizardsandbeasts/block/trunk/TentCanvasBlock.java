package at.koopro.wizardsandbeasts.block.trunk;

import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Perkins's tent — plain canvas A-frame. See {@link AbstractTentBlock}. */
public final class TentCanvasBlock extends AbstractTentBlock {

    public static final MapCodec<TentCanvasBlock> CODEC = simpleCodec(TentCanvasBlock::new);

    public TentCanvasBlock(BlockBehaviour.@NonNull Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new TentBlockEntity(ModBlockEntities.TENT_CANVAS.get(), pos, state);
    }
}
