package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A longcase clock two blocks high: its hands keep the day's time on the dial in the hood and its pendulum swings
 * behind the glass of the trunk. Both are drawn by {@link at.koopro.wizardsandbeasts.client.furniture.MovingPropRenderer}
 * from the lower half's block entity.
 */
@NullMarked
public class GrandfatherClockBlock extends TallFurnitureBlock implements EntityBlock {

    public GrandfatherClockBlock(BlockBehaviour.Properties properties, Spec spec) {
        super(properties, spec);
    }

    @Override
    protected MapCodec<? extends GrandfatherClockBlock> codec() {
        return simpleCodec(properties -> new GrandfatherClockBlock(properties, spec()));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new MovingPropBlockEntity(pos, state) : null;
    }
}
