package at.koopro.wizardsandbeasts.chamber;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The serpent's gate: a portcullis of stone that stands open only while something holds it. In a Chamber, that is a
 * comparator reading the basin beside it. Fill the basin (Aguamenti fills a cauldron; so does a bucket of water from
 * the pipes) and the gate rises; empty it and the gate falls again. The rule lives in the world's own redstone, so it
 * survives every death, logout and restart, and it cannot softlock: water is always one room back.
 *
 * <p>No {@code OPEN} property, on purpose: Alohomora does not lift it. The whole gate (every gate block touching) is up
 * while any of its blocks is powered. It waits for anyone standing in it before it falls, so one player emptying the
 * basin cannot crush another. Unbreakable.
 */
public class ChamberGateBlock extends Block {

    public static final MapCodec<ChamberGateBlock> CODEC = simpleCodec(ChamberGateBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    static final double WITNESS_RADIUS = 10.0;
    /** Ticks between looks while someone stands in a gate that should fall. */
    static final int HOLD_RECHECK = 20;

    public ChamberGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected @NonNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                                           @NonNull CollisionContext context) {
        return state.getValue(POWERED) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return state.getValue(POWERED) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        return state.getValue(POWERED) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return state.getValue(POWERED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected boolean propagatesSkylightDown(@NonNull BlockState state) {
        return state.getValue(POWERED);
    }

    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level instanceof ServerLevel server) {
            settle(server, pos);
        }
    }

    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        settle(level, pos);
    }

    /** Raises or lowers the whole gate to match whether any of its blocks is powered now. */
    static void settle(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (!(here.getBlock() instanceof ChamberGateBlock gate)) {
            return;
        }
        var members = ChamberSeals.group(level, pos, gate);
        boolean powered = false;
        for (BlockPos member : members) {
            if (level.hasNeighborSignal(member)) {
                powered = true;
                break;
            }
        }
        if (powered == here.getValue(POWERED)) {
            return;
        }
        if (!powered && occupied(level, members)) {
            level.scheduleTick(pos, gate, HOLD_RECHECK);
            return;
        }
        for (BlockPos member : members) {
            BlockState memberState = level.getBlockState(member);
            if (memberState.getValue(POWERED) != powered) {
                // Clients only: the gate's blocks emit no signal, so their neighbours have nothing to learn.
                level.setBlock(member, memberState.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            }
        }
        level.playSound(null, pos, powered ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT,
                SoundSource.BLOCKS, 1.0f, 0.5f);
        if (powered) {
            ChamberQuestService.reachNear(level, pos, WITNESS_RADIUS, ChamberStage.GATE_OPENED);
        }
    }

    /** Whether anything alive stands in one of the gate's blocks. */
    private static boolean occupied(ServerLevel level, List<BlockPos> members) {
        for (BlockPos member : members) {
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(member), LivingEntity::isAlive).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
