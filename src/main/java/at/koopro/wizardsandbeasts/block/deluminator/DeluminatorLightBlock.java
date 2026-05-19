package at.koopro.wizardsandbeasts.block.deluminator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible block that emits light level 15. Placed by the Deluminator
 * when releasing stored lights. Has no collision and is replaceable.
 */
public class DeluminatorLightBlock extends Block {

    /** Small selection hitbox so the player can target and click the block. */
    private static final VoxelShape SELECTION_SHAPE = Shapes.box(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);

    public DeluminatorLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SELECTION_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
