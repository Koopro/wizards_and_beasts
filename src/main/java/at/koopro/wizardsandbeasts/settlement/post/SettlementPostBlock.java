package at.koopro.wizardsandbeasts.settlement.post;

import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Where a settlement's person belongs: a worn paving stone behind a counter. Placed only by a structure, and
 * unbreakable, because a hamlet may be taken apart like a village but its identity must not be. The role is written
 * into the template, on the block entity.
 */
public class SettlementPostBlock extends BaseEntityBlock {

    public static final MapCodec<SettlementPostBlock> CODEC = simpleCodec(SettlementPostBlock::new);

    public SettlementPostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new SettlementPostBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NonNull Level level,
            @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.SETTLEMENT_POST.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((SettlementPostBlockEntity) be).serverTick(lvl, pos);
    }
}
