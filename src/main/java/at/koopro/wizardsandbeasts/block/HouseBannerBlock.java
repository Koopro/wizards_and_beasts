package at.koopro.wizardsandbeasts.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HouseBannerBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.join(
            Block.box(4, 0, 4, 12, 16, 12),
            Block.box(4, 16, 4, 12, 32, 12),
            BooleanOp.OR);

    public HouseBannerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
