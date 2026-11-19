package at.koopro.wizardsandbeasts.block.furniture;

import at.koopro.wizardsandbeasts.registry.FurnitureBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

/**
 * Marks a furniture piece with a part that moves (clock hands, a turning globe, a floating book). It holds no data
 * and never ticks: the renderer reads the time from the level. One type serves every such piece.
 */
@NullMarked
public class MovingPropBlockEntity extends BlockEntity {

    public MovingPropBlockEntity(BlockPos pos, BlockState state) {
        super(FurnitureBlocks.MOVING_PROP.get(), pos, state);
    }
}
