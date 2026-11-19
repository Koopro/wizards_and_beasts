package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Furniture that joins its own kind side by side into one long piece: a refectory table with legs only at its two
 * ends, a bench, a run of shelving with side boards only at the ends. Two pieces join when they are the same block
 * and face the same way.
 *
 * <p>In the authoring frame (front to the north) a run goes along x: {@link Run#START} is the end at low x, which in
 * the world is the side toward {@code facing.getCounterClockWise()}.
 */
@NullMarked
public class ConnectingFurnitureBlock extends FurnitureBlock {

    public enum Run implements StringRepresentable {
        SINGLE, START, MIDDLE, END;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Run of(boolean low, boolean high) {
            if (low && high) {
                return MIDDLE;
            }
            if (high) {
                return START;
            }
            return low ? END : SINGLE;
        }
    }

    public static final EnumProperty<Run> RUN = EnumProperty.create("run", Run.class);

    public ConnectingFurnitureBlock(BlockBehaviour.Properties properties, Spec spec) {
        super(properties, spec);
        registerDefaultState(defaultBlockState().setValue(RUN, Run.SINGLE));
    }

    @Override
    protected String shapeKey(BlockState state) {
        return spec().shapeKey() + "_" + state.getValue(RUN).getSerializedName();
    }

    @Override
    protected MapCodec<? extends ConnectingFurnitureBlock> codec() {
        return simpleCodec(properties -> new ConnectingFurnitureBlock(properties, spec()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RUN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : joined(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        BlockState updated = super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
        return updated.is(this) && direction.getAxis().isHorizontal() ? joined(updated, level, pos) : updated;
    }

    private BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction high = facing.getClockWise();
        return state.setValue(RUN, Run.of(joins(state, level.getBlockState(pos.relative(high.getOpposite()))),
                joins(state, level.getBlockState(pos.relative(high)))));
    }

    private boolean joins(BlockState self, BlockState other) {
        return other.is(this) && other.getValue(FACING) == self.getValue(FACING);
    }
}
