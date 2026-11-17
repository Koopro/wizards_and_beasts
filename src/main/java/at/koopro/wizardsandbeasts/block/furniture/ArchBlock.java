package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import at.koopro.wizardsandbeasts.registry.FurnitureBlocks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The head of a pointed (Gothic) arch: a row of these laid over a doorway or window forms the arch by itself.
 *
 * <p>Each block is the stone above the opening with the arch's curve cut from its underside. A lone block is a narrow
 * lancet; two side by side meet in a point over the joint; three spring at the ends and rise to a crown in the middle
 * block. Wider runs repeat the middle (flatter, like a Tudor arch). The part follows the neighbours, so a builder
 * only lays the row. Curves and outlines come from {@code tools/furniture_models.py}.
 *
 * <p>Over a window the curve is glazed: when a leaded pane stands under the arch, the space under its curve takes the
 * same glass, so the light runs up into the point instead of ending flat under an empty arch. Over a door it stays
 * open.
 */
@NullMarked
public class ArchBlock extends HorizontalDirectionalBlock {

    public enum Part implements StringRepresentable {
        SINGLE, PAIR_START, PAIR_END, START, MIDDLE, END;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** The glass filling the curve, taken from the pane under the arch. */
    public enum Glass implements StringRepresentable {
        NONE, CLEAR, CRIMSON, AZURE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Glass under(BlockState below) {
            if (below.is(FurnitureBlocks.LEADED_GLASS_PANE.get())) {
                return CLEAR;
            }
            if (below.is(FurnitureBlocks.CRIMSON_LEADED_GLASS_PANE.get())) {
                return CRIMSON;
            }
            return below.is(FurnitureBlocks.AZURE_LEADED_GLASS_PANE.get()) ? AZURE : NONE;
        }
    }

    public static final EnumProperty<Glass> GLASS = EnumProperty.create("glass", Glass.class);

    private final String shapeKey;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public ArchBlock(BlockBehaviour.Properties properties, String shapeKey) {
        super(properties);
        this.shapeKey = shapeKey;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.SINGLE)
                .setValue(GLASS, Glass.NONE));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            shapes.put(state, FurnitureBlock.rotated(
                    FurnitureShapes.boxes(shapeKey + "_" + state.getValue(PART).getSerializedName()),
                    state.getValue(FACING)));
        }
    }

    @Override
    protected MapCodec<? extends ArchBlock> codec() {
        return simpleCodec(properties -> new ArchBlock(properties, shapeKey));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, GLASS);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        // Laid against an arch already standing, take its facing so the row joins.
        for (Direction side : new Direction[]{state.getValue(FACING).getClockWise(),
                state.getValue(FACING).getCounterClockWise()}) {
            BlockState neighbour = context.getLevel().getBlockState(context.getClickedPos().relative(side));
            if (neighbour.is(this) && neighbour.getValue(FACING).getAxis() == state.getValue(FACING).getAxis()) {
                state = state.setValue(FACING, neighbour.getValue(FACING));
                break;
            }
        }
        BlockPos pos = context.getClickedPos();
        return part(state, context.getLevel(), pos).setValue(GLASS, Glass.under(context.getLevel().getBlockState(pos.below())));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (direction == Direction.DOWN) {
            return state.setValue(GLASS, Glass.under(neighbor));
        }
        return direction.getAxis().isHorizontal() ? part(state, level, pos) : state;
    }

    private BlockState part(BlockState state, BlockGetter level, BlockPos pos) {
        Direction high = state.getValue(FACING).getClockWise();
        boolean lowJoined = joins(state, level.getBlockState(pos.relative(high.getOpposite())));
        boolean highJoined = joins(state, level.getBlockState(pos.relative(high)));
        Part part;
        if (lowJoined && highJoined) {
            part = Part.MIDDLE;
        } else if (highJoined) {
            boolean wide = joins(state, level.getBlockState(pos.relative(high, 2)));
            part = wide ? Part.START : Part.PAIR_START;
        } else if (lowJoined) {
            boolean wide = joins(state, level.getBlockState(pos.relative(high.getOpposite(), 2)));
            part = wide ? Part.END : Part.PAIR_END;
        } else {
            part = Part.SINGLE;
        }
        return state.setValue(PART, part);
    }

    private boolean joins(BlockState self, BlockState other) {
        return other.is(this) && other.getValue(FACING) == self.getValue(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.getOrDefault(state, Shapes.block());
    }
}
