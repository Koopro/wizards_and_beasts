package at.koopro.wizardsandbeasts.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * A broom rack: two posts and two rails against a wall, with a broom stood in it. Decoration, and the job site of
 * the broommaker villager ({@code ModVillager.BROOMMAKER}), who keeps the broom shop ({@code locations/broom_shop.json}).
 *
 * <p>Authored facing north (the rails at the south edge, the broom in front of them), the orientation
 * {@code ROTATION_HORIZONTAL_FACING} leaves unrotated; placed, it faces the player.
 */
@NullMarked
public class BroomRackBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<BroomRackBlock> CODEC = simpleCodec(BroomRackBlock::new);

    /** Posts and rails along the back, the broom's bristles at the foot: the north-facing shape, rotated per facing. */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(1.0, 0.0, 12.0, 15.0, 16.0, 15.0),
            Block.box(5.0, 0.0, 8.0, 11.0, 5.0, 14.0)));

    public BroomRackBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion();
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
