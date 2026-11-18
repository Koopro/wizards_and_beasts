package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NullMarked;

/**
 * A curtain hung in a window or a bed's side: a run of these is one curtain, closed as a full drape, open as folds
 * gathered at the run's two ends. Using any block of the run draws the whole run open or shut. Nothing collides
 * with cloth.
 */
@NullMarked
public class CurtainBlock extends ConnectingFurnitureBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** The longest run one pull draws. */
    private static final int MAX_RUN = 16;

    public CurtainBlock(BlockBehaviour.Properties properties, Spec spec) {
        super(properties, spec);
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends CurtainBlock> codec() {
        return simpleCodec(properties -> new CurtainBlock(properties, spec()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean open = !state.getValue(OPEN);
            Direction facing = state.getValue(FACING);
            Direction along = facing.getClockWise();
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
            for (Direction way : new Direction[]{along, along.getOpposite()}) {
                BlockPos at = pos.relative(way);
                for (int i = 0; i < MAX_RUN; i++, at = at.relative(way)) {
                    BlockState other = level.getBlockState(at);
                    if (!other.is(this) || other.getValue(FACING) != facing) {
                        break;
                    }
                    level.setBlock(at, other.setValue(OPEN, open), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8f, open ? 1.2f : 0.9f);
        }
        return InteractionResult.SUCCESS;
    }
}
