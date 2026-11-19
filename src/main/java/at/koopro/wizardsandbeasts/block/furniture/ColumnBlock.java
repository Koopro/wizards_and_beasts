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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

/**
 * A carved column: a stack of these grows a moulded base at the foot, a slim shaft in between and a capital at the
 * top, chosen from the blocks above and below. A lone block is a short complete column (a pedestal).
 */
@NullMarked
public class ColumnBlock extends Block {

    public enum Part implements StringRepresentable {
        SINGLE, BASE, SHAFT, CAPITAL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private final String shapeKey;
    private final Map<Part, VoxelShape> shapes = new HashMap<>();

    public ColumnBlock(BlockBehaviour.Properties properties, String shapeKey) {
        super(properties);
        this.shapeKey = shapeKey;
        registerDefaultState(stateDefinition.any().setValue(PART, Part.SINGLE));
        for (Part part : Part.values()) {
            shapes.put(part, FurnitureBlock.rotated(FurnitureShapes.boxes(shapeKey + "_" + part.getSerializedName()),
                    Direction.NORTH));
        }
    }

    @Override
    protected MapCodec<? extends ColumnBlock> codec() {
        return simpleCodec(properties -> new ColumnBlock(properties, shapeKey));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return part(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return direction.getAxis().isVertical() ? part(state, level, pos) : state;
    }

    private BlockState part(BlockState state, BlockGetter level, BlockPos pos) {
        boolean below = level.getBlockState(pos.below()).is(this);
        boolean above = level.getBlockState(pos.above()).is(this);
        Part part = below && above ? Part.SHAFT : above ? Part.BASE : below ? Part.CAPITAL : Part.SINGLE;
        return state.setValue(PART, part);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.getOrDefault(state.getValue(PART), Shapes.block());
    }
}
