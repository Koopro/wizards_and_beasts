package at.koopro.wizardsandbeasts.block.deco;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Set dressing that grows as more is put down: one to four of a thing (coins, bottles, books) in one block, placed
 * again on itself to add one, as sea pickles do. Breaking it gives back as many as it holds.
 */
@NullMarked
public class DecoPileBlock extends DecoBlock {

    public static final int MAX = 4;
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, MAX);

    private final VoxelShape[] shapes;

    /** {@code shapes[i]} is the outline at count {@code i + 1}. */
    public DecoPileBlock(BlockBehaviour.Properties properties, VoxelShape... shapes) {
        super(properties, shapes[0]);
        this.shapes = shapes;
        registerDefaultState(defaultBlockState().setValue(COUNT, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COUNT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState there = context.getLevel().getBlockState(context.getClickedPos());
        if (there.is(this)) {
            return there.setValue(COUNT, Math.min(MAX, there.getValue(COUNT) + 1));
        }
        return super.getStateForPlacement(context);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(asItem()) && state.getValue(COUNT) < MAX
                || super.canBeReplaced(state, context);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return shapes[Math.min(shapes.length, state.getValue(COUNT)) - 1];
    }
}
