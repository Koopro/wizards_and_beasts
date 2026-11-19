package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A piece bigger than a block, sized from the films (a common-room hearth, an Atrium fireplace): one item places a
 * {@code width} x {@code height} wall of cells, each a block of this kind knowing its column and row, and the cells
 * stand or fall together. The bottom-middle cell is the one clicked and the only one that drops the item.
 *
 * <p>Cells are authored as one model in {@code tools/furniture_models.py} and sliced per cell; in the authoring frame
 * (front to the north) column 0 is the low-x end, which in the world lies toward {@code facing.getCounterClockWise()}.
 */
@NullMarked
public class MultiblockFurnitureBlock extends FurnitureBlock {

    public static final int MAX_WIDTH = 5, MAX_HEIGHT = 7;
    public static final IntegerProperty COL = IntegerProperty.create("col", 0, MAX_WIDTH - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, MAX_HEIGHT - 1);

    public MultiblockFurnitureBlock(BlockBehaviour.Properties properties, Spec spec) {
        super(properties, spec);
        registerDefaultState(defaultBlockState().setValue(COL, spec.width() / 2).setValue(ROW, 0));
    }

    @Override
    protected String shapeKey(BlockState state) {
        int col = Math.min(state.getValue(COL), spec().width() - 1);
        int row = Math.min(state.getValue(ROW), spec().height() - 1);
        return spec().shapeKey() + "_c" + col + "_r" + row;
    }

    @Override
    protected MapCodec<? extends MultiblockFurnitureBlock> codec() {
        return simpleCodec(properties -> new MultiblockFurnitureBlock(properties, spec()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COL, ROW);
    }

    /** The world position of cell (col, row) of a piece whose clicked (bottom-middle) cell is at {@code origin}. */
    public BlockPos cell(BlockPos origin, Direction facing, int col, int row) {
        return origin.relative(facing.getClockWise(), col - spec().width() / 2).above(row);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        Direction facing = state.getValue(FACING);
        BlockPos origin = context.getClickedPos();
        for (int col = 0; col < spec().width(); col++) {
            for (int row = 0; row < spec().height(); row++) {
                BlockPos at = cell(origin, facing, col, row);
                if (at.getY() > context.getLevel().getMaxY() || !context.getLevel().getBlockState(at).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return state.setValue(COL, spec().width() / 2).setValue(ROW, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int col = 0; col < spec().width(); col++) {
            for (int row = 0; row < spec().height(); row++) {
                if (col == spec().width() / 2 && row == 0) {
                    continue;
                }
                level.setBlock(cell(pos, facing, col, row),
                        state.setValue(COL, col).setValue(ROW, row).setValue(WATERLOGGED, false), Block.UPDATE_ALL);
            }
        }
    }

    /** A cell falls when a neighbouring cell of its own piece has gone, so breaking any cell takes the whole piece. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        Direction facing = state.getValue(FACING);
        int col = state.getValue(COL), row = state.getValue(ROW);
        int dc = direction == facing.getClockWise() ? 1 : direction == facing.getCounterClockWise() ? -1 : 0;
        int dr = direction == Direction.UP ? 1 : direction == Direction.DOWN ? -1 : 0;
        if (dc != 0 || dr != 0) {
            int ncol = col + dc, nrow = row + dr;
            boolean inside = ncol >= 0 && ncol < spec().width() && nrow >= 0 && nrow < spec().height();
            if (inside && !(neighbor.is(this) && neighbor.getValue(FACING) == facing && neighbor.getValue(COL) == ncol
                    && neighbor.getValue(ROW) == nrow)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    /** Whether this cell is the one that drops the item (the bottom-middle). */
    public boolean isOrigin(BlockState state) {
        return state.getValue(COL) == spec().width() / 2 && state.getValue(ROW) == 0;
    }
}
