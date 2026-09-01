package at.koopro.wizardsandbeasts.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

/**
 * A single Occamy eggshell, set down.
 *
 * <p>Purely decorative — the silver in it is worth something only in a cauldron, and a shell sitting
 * on a shelf is a trophy. It is a small shape rather than a full cube for the obvious reason, and it
 * faces the way it was placed so a row of them on a mantelpiece does not look stamped out.
 *
 * <p><b>Not a turtle egg.</b> Vanilla's egg breaks when stepped on and hatches on a timer; neither is
 * wanted here. An Occamy shell is what is left <em>after</em> the hatching, so it has nothing to
 * become, and a decoration that shatters when its owner walks past it would be an unusable one. It is
 * fragile in the player's hands (see {@code OccamyEggshellFragility}), not underfoot.
 */
@NullMarked
public class OccamyEggshellBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<OccamyEggshellBlock> CODEC = simpleCodec(OccamyEggshellBlock::new);

    /** Six pixels across, five tall, centred. Reads as an egg at arm's length. */
    private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 5.0, 11.0);

    public OccamyEggshellBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    /** Default properties, so the registry line and any datapack copy of it cannot drift apart. */
    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .strength(0.2f)
                .sound(SoundType.AMETHYST_CLUSTER)
                .noOcclusion()
                .instabreak();
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
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        // No collision: a five-pixel bump that catches the player's feet is a nuisance, and an
        // ornament nobody can walk over is an ornament nobody places indoors.
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state,
                                     net.minecraft.world.level.LevelReader level,
                                     net.minecraft.world.level.ScheduledTickAccess ticks,
                                     BlockPos pos,
                                     net.minecraft.core.Direction direction,
                                     BlockPos neighbourPos,
                                     BlockState neighbourState,
                                     RandomSource random) {
        if (direction == net.minecraft.core.Direction.DOWN && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }
}
