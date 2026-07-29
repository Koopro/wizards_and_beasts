package at.koopro.wizardsandbeasts.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HouseBannerBlock extends Block {

    // Single-block slim cloth footprint that matches the crossed-pennant model. The old shape
    // stacked a second box up to Y32, walling off the block above with invisible collision.
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public HouseBannerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
