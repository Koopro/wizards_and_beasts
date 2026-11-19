package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A piece of furniture that faces whoever put it down (or, wall-mounted, away from the wall it was put on).
 *
 * <p>Its model and its outline come from the same boxes in {@code tools/furniture_models.py}: the tool writes the
 * JSON models and {@link FurnitureShapes}, both authored front to the north, and this class turns the outline by
 * facing. So a chair's collision is the chair, not a guess next to it. Chairs, benches and armchairs carry a seat
 * height and seat whoever uses them empty-handed ({@link SeatEntity}).
 */
@NullMarked
public class FurnitureBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /**
     * How a piece behaves beyond its outline.
     *
     * @param shapeKey   the key into {@link FurnitureShapes}; connecting pieces add {@code _<shape>}
     * @param seatHeight where a sitter sits, in blocks above the floor; below zero for no seat
     * @param wall       hung on a wall: faces out from the clicked wall and falls if the wall goes
     */
    public record Spec(String shapeKey, float seatHeight, boolean wall, int width, int height) {
        public static Spec of(String shapeKey) {
            return new Spec(shapeKey, -1f, false, 1, 1);
        }

        public static Spec seat(String shapeKey, float seatHeight) {
            return new Spec(shapeKey, seatHeight, false, 1, 1);
        }

        public static Spec wall(String shapeKey) {
            return new Spec(shapeKey, -1f, true, 1, 1);
        }

        /** A piece {@code width} blocks across and {@code height} high, built of one block per cell. */
        public static Spec multi(String shapeKey, int width, int height) {
            return new Spec(shapeKey, -1f, false, width, height);
        }
    }

    private final Spec spec;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public FurnitureBlock(BlockBehaviour.Properties properties, Spec spec) {
        super(properties);
        this.spec = spec;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            shapes.put(state, rotated(FurnitureShapes.boxes(shapeKey(state)), state.getValue(FACING)));
        }
    }

    public Spec spec() {
        return spec;
    }

    /** The outline's key for this state; connecting pieces name their part. */
    protected String shapeKey(BlockState state) {
        return spec.shapeKey();
    }

    @Override
    protected MapCodec<? extends FurnitureBlock> codec() {
        return simpleCodec(properties -> new FurnitureBlock(properties, spec));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing;
        if (spec.wall()) {
            Direction clicked = context.getClickedFace();
            if (clicked.getAxis().isVertical()) {
                return null;
            }
            facing = clicked;
        } else {
            facing = context.getHorizontalDirection().getOpposite();
        }
        BlockState state = defaultBlockState().setValue(FACING, facing)
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        return spec.wall() && !canSurvive(state, context.getLevel(), context.getClickedPos()) ? null : state;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!spec.wall()) {
            return true;
        }
        Direction back = state.getValue(FACING).getOpposite();
        BlockPos wall = pos.relative(back);
        return level.getBlockState(wall).isFaceSturdy(level, wall, back.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (spec.wall() && direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.getOrDefault(state, Shapes.block());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (spec.seatHeight() < 0f || player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        return SeatEntity.sit(level, pos, state.getValue(FACING), spec.seatHeight(), player)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Boxes authored front to the north (block units 0..16), turned to a facing. A blockstate {@code y} turn is
     * clockwise seen from above, so north goes to east: {@code (x, z) -> (16 - z, x)}.
     */
    static VoxelShape rotated(List<double[]> boxes, Direction facing) {
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
            VoxelShape box = switch (facing) {
                case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
                case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
                case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
                default -> Block.box(x0, y0, z0, x1, y1, z1);
            };
            out = Shapes.or(out, box);
        }
        return out.isEmpty() ? Shapes.empty() : out.optimize();
    }
}
