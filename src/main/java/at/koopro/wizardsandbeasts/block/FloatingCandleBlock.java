package at.koopro.wizardsandbeasts.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Light-emitting decorative block with no collision (for hanging candle aesthetics).
 */
public class FloatingCandleBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.box(0.35, 0.0, 0.35, 0.65, 0.5, 0.65);

    public FloatingCandleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
