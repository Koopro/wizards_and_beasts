package at.koopro.wizardsandbeasts.chamber;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A Chamber's hidden seal: brickwork that is a door. Closed it looks like the wall around it; open it is gone.
 *
 * <p>Two ways through, both from systems that already exist:
 * <ul>
 *   <li><b>Revelio</b> outlines it (it is in {@code revelio_reveals}), and <b>Alohomora</b> opens it: the spell cycles
 *       any block with an {@code OPEN} property at lock tier 0;</li>
 *   <li>without a wand, the knocker: a signal rising on any of its blocks (a button set in the wall beside it)
 *       opens it the same way.</li>
 * </ul>
 * Opening one block opens the whole doorway ({@link ChamberSeals#group}). The state is the block's, saved with the
 * chunk, so a seal opened before a death, a logout or a restart is still open after. It never closes in play: an open
 * seal has no shape for Alohomora to hit, and a second knock does nothing. Otherwise one player's knock would shut the
 * doorway on another standing in it, or wall them in on the far side with no knocker. Unbreakable, so the puzzle is not
 * dug past through the seal itself.
 */
public class ChamberSealBlock extends Block {

    public static final MapCodec<ChamberSealBlock> CODEC = simpleCodec(ChamberSealBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Players this close to a seal as it opens are counted as having opened it. */
    static final double WITNESS_RADIUS = 10.0;

    public ChamberSealBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(POWERED, false));
    }

    @Override
    protected @NonNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(OPEN, POWERED);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                                           @NonNull CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return state.getValue(OPEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected boolean propagatesSkylightDown(@NonNull BlockState state) {
        return state.getValue(OPEN);
    }

    /** The knocker: a rising signal opens the seal, as Alohomora does. A falling one, or a second knock, does nothing. */
    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        BlockState next = state.setValue(POWERED, powered);
        if (powered) {
            next = next.setValue(OPEN, true);
        }
        level.setBlock(pos, next, Block.UPDATE_ALL);
    }

    /** One block's {@code OPEN} changed (Alohomora, the knocker): the whole doorway follows, once. */
    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        if (!(level instanceof ServerLevel server) || !oldState.is(this)
                || oldState.getValue(OPEN) == state.getValue(OPEN)) {
            return;
        }
        boolean open = state.getValue(OPEN);
        for (BlockPos member : ChamberSeals.group(level, pos, this)) {
            BlockState memberState = level.getBlockState(member);
            if (!member.equals(pos) && memberState.getValue(OPEN) != open) {
                // Skip-on-place: the members follow this block; they do not each spread again.
                level.setBlock(member, memberState.setValue(OPEN, open),
                        Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ON_PLACE);
            }
        }
        level.playSound(null, pos, open ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                SoundSource.BLOCKS, 1.0f, 0.5f);
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8f, 0.6f);
        if (open) {
            ChamberQuestService.reachNear(server, pos, WITNESS_RADIUS, ChamberStage.SEAL_OPENED);
        }
    }
}
