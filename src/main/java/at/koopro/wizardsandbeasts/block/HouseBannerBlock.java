package at.koopro.wizardsandbeasts.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A house banner: two blocks of cloth hanging from a rod, flat against whatever is behind it.
 *
 * <p>This used to be a single block drawn on {@code minecraft:block/cross} — two crossed
 * diagonal planes, i.e. the sapling model — which read as a plant rather than as fabric and
 * gave a banner square proportions. It is now the shape a banner actually has: a thin quad
 * one half-pixel off the back face, stacked {@link DoubleBlockHalf#LOWER} under
 * {@link DoubleBlockHalf#UPPER}, with the silhouette (inset cloth, swallowtail hem) living in
 * the texture's alpha so the geometry stays one quad per half.
 *
 * <p>Nothing has to support it. The pair is held together only by each half checking for the
 * other, so a banner can hang in a doorway or off a beam; break or blow up either half and the
 * other goes with it, and the loot table ({@code createDoorTable}) pays out once, from the
 * lower half, no matter which end was hit.
 */
public class HouseBannerBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<HouseBannerBlock> CODEC = simpleCodec(HouseBannerBlock::new);

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /**
     * Authored for {@code FACING == NORTH}, which is the identity entry of
     * {@link Shapes#rotateHorizontal} — the same convention the model's blockstate rotation
     * uses. The cloth sits at the far side of the block so its visible face points along
     * {@code FACING}, and it is one pixel deep because that is all the cloth is.
     */
    private static final Map<Direction, VoxelShape> SHAPES =
            Shapes.rotateHorizontal(Block.box(0, 0, 15, 16, 16, 16));

    public HouseBannerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected @NonNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                            @Nullable LivingEntity placer, @NonNull ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    /**
     * The upper half needs its own lower half under it; the lower half needs nothing, so a
     * banner survives the wall behind it being remodelled.
     */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess tickAccess, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        // Only the one vertical neighbour that should hold the other half is interesting:
        // reacting to the rest is what would make this a wall-mounted block.
        if (direction.getAxis() == Direction.Axis.Y && (direction == Direction.UP) == (half == DoubleBlockHalf.LOWER)) {
            boolean paired = neighborState.is(this)
                    && neighborState.getValue(HALF) != half
                    && neighborState.getValue(FACING) == state.getValue(FACING);
            if (!paired) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    /**
     * Breaking the top half in creative clears the bottom one silently. Without this the
     * bottom is removed by {@link #updateShape} instead, which destroys it <em>with</em>
     * drops and hands a creative player a free banner.
     */
    @Override
    public @NonNull BlockState playerWillDestroy(@NonNull Level level, @NonNull BlockPos pos,
                                                 @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER && player.preventsBlockDrops()) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(belowState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
