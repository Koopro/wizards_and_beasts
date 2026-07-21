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

/** World Cup–class showy tent — turrets, chimney, flagpole. See {@link AbstractTentBlock}. */
public final class TentGrandBlock extends AbstractTentBlock {

    public static final MapCodec<TentGrandBlock> CODEC = simpleCodec(TentGrandBlock::new);

    public TentGrandBlock(BlockBehaviour.@NonNull Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new TentBlockEntity(ModBlockEntities.TENT_GRAND.get(), pos, state);
    }
}
