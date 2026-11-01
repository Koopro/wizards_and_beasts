package at.koopro.wizardsandbeasts.chamber;

import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The heart of a Chamber: the dais stone at the far end of the hall, facing the hall. It runs the basilisk encounter
 * ({@link ChamberHeartBlockEntity}) and is where the sword is given. Its facing turns with the structure, so the hall
 * it watches is found the same way in a Chamber generated at any rotation. Unbreakable.
 */
public class ChamberHeartBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ChamberHeartBlock> CODEC = simpleCodec(ChamberHeartBlock::new);

    public ChamberHeartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected @NonNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new ChamberHeartBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NonNull Level level,
            @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CHAMBER_HEART.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((ChamberHeartBlockEntity) be).serverTick(lvl, pos, st);
    }
}
