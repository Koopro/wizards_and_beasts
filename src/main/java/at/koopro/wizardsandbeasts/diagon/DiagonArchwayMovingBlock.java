package at.koopro.wizardsandbeasts.diagon;

import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A brick of the archway while it moves or stands open: drawn by {@code DiagonArchwayRenderer} from its
 * {@link DiagonArchwayBlockEntity} (never by the chunk, so it can fold away), occluding nothing, and with no shape at
 * all while {@link #OPEN}, so the arch is walked through.
 *
 * <p>The key brick watches the arch: once it has stood open {@link #OPEN_TICKS} and nobody stands in it, the arch
 * closes. A closing brick turns back into a resting {@link DiagonArchwayBlock} once its part is played.
 */
@NullMarked
public final class DiagonArchwayMovingBlock extends BaseEntityBlock {

    public static final MapCodec<DiagonArchwayMovingBlock> CODEC = simpleCodec(DiagonArchwayMovingBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty KEY = DiagonArchwayBlock.KEY;

    /** How long the arch stands open, at the least, before it may close behind the last one through. */
    static final int OPEN_TICKS = 160;
    /** Long enough for every ring's closing part to have played, before the bricks are plain bricks again. */
    static final int SETTLE_TICKS = 32;

    public DiagonArchwayMovingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(KEY, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, KEY);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiagonArchwayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                             BlockEntityType<T> type) {
        if (level.isClientSide() || !state.getValue(KEY) || type != ModBlockEntities.DIAGON_ARCHWAY.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> closeWhenClear((ServerLevel) lvl, pos, st, (DiagonArchwayBlockEntity) be);
    }

    /** A closing brick, its part played, is a plain brick again. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN)) {
            level.setBlock(pos, ModBlocks.DIAGON_ARCHWAY.get().defaultBlockState()
                    .setValue(DiagonArchwayBlock.KEY, state.getValue(KEY)), Block.UPDATE_ALL);
        }
    }

    /** Closes the arch once it has stood open long enough and nobody stands in it. */
    private static void closeWhenClear(ServerLevel level, BlockPos key, BlockState state, DiagonArchwayBlockEntity brick) {
        if (!state.getValue(OPEN) || level.getGameTime() % 10 != 0
                || level.getGameTime() - brick.changedAt() < OPEN_TICKS) {
            return;
        }
        List<BlockPos> bricks = DiagonArchwayBlock.group(level, key);
        AABB arch = new AABB(key);
        for (BlockPos pos : bricks) {
            arch = arch.minmax(new AABB(pos));
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, arch.inflate(0.4)).isEmpty()) {
            return;
        }
        DiagonArchwayBlock.setArch(level, key, false);
    }
}
