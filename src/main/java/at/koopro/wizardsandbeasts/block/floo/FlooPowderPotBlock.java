package at.koopro.wizardsandbeasts.block.floo;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

/**
 * The pot of Floo Powder on the mantelpiece that guests take a pinch from: the Burrow keeps it in a flowerpot
 * (CoS ch. 4; CANON.md). Vanilla flower pot shape and clay, powder heaped inside at one of three levels.
 * Taking and filling pinches lands in P3.
 */
@NullMarked
public class FlooPowderPotBlock extends Block {

    public static final MapCodec<FlooPowderPotBlock> CODEC = simpleCodec(FlooPowderPotBlock::new);
    public static final int MAX_FILL = 3;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MAX_FILL);

    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 6, 11);

    public FlooPowderPotBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILL, MAX_FILL));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
