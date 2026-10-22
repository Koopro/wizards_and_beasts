package at.koopro.wizardsandbeasts.artefact.cabinet;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A Vanishing Cabinet: a tall black wardrobe, two blocks high, that opens onto its partner wherever that stands.
 *
 * <p>Click it to step through; sneak with an empty hand to share or un-share the pair; sneak with a wand to link
 * or unlink it ({@link CabinetEvents}, because vanilla never offers a sneaking, item-holding click to the block).
 * All of that is decided by {@link CabinetService}; the block only works out which cabinet was clicked -- always
 * the lower half, which carries the block entity -- and reports the answer.
 *
 * <p>The halves hold together the way a door's do: break either and both go, the loot table pays out once from
 * the lower half, and the removal of the lower half dissolves the pair.
 */
public class VanishingCabinetBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<VanishingCabinetBlock> CODEC = simpleCodec(VanishingCabinetBlock::new);

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Authored facing north (the identity of {@link Shapes#rotateHorizontal}): a cabinet a pixel short of each side. */
    private static final Map<Direction, VoxelShape> LOWER_SHAPES =
            Shapes.rotateHorizontal(Block.box(1, 0, 2, 15, 16, 16));
    private static final Map<Direction, VoxelShape> UPPER_SHAPES =
            Shapes.rotateHorizontal(Block.box(1, 0, 2, 15, 15, 16));

    public VanishingCabinetBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected @NonNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new VanishingCabinetBlockEntity(pos, state) : null;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES).get(state.getValue(FACING));
    }

    /** The lower half of the cabinet whose half sits at {@code pos}. */
    public static BlockPos lowerOf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    // ── placing and holding together ───────────────────────────────────────

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                            @Nullable LivingEntity placer, @NonNull ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof VanishingCabinetBlockEntity be) {
            be.setOwner(player.getUUID());
        }
    }

    /** Stands on the ground like a wardrobe; the upper half needs its own lower half under it. */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess tickAccess, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (direction == Direction.UP) == (half == DoubleBlockHalf.LOWER)) {
            boolean paired = neighborState.is(this)
                    && neighborState.getValue(HALF) != half
                    && neighborState.getValue(FACING) == state.getValue(FACING);
            if (!paired) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    /** Creative break of the top half clears the bottom silently, so no free cabinet drops (as for doors). */
    @Override
    public @NonNull BlockState playerWillDestroy(@NonNull Level level, @NonNull BlockPos pos,
                                                 @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER && player.preventsBlockDrops()) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(belowState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * The lower half left the world -- mined, blown up, or taken with its upper half -- so the pair is dissolved.
     * A state change that keeps the cabinet (none exists today) is not a removal.
     */
    @Override
    protected void affectNeighborsAfterRemoval(@NonNull BlockState state, @NonNull ServerLevel level,
                                               @NonNull BlockPos pos, boolean movedByPiston) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && !level.getBlockState(pos).is(this)) {
            CabinetService.onRemoved(level, pos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    // ── using ─────────────────────────────────────────────────────────────

    /** Whatever is in hand, a plain click is a step through; the item never gets a say. */
    @Override
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack stack, @NonNull BlockState state,
                                                   @NonNull Level level, @NonNull BlockPos pos,
                                                   @NonNull Player player, @NonNull InteractionHand hand,
                                                   @NonNull BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** A plain click steps through; a sneaking, empty-handed click shares or un-shares the pair. */
    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
                                                        @NonNull BlockPos pos, @NonNull Player player,
                                                        @NonNull BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos lower = lowerOf(state, pos);
        CabinetService.Result result = player.isSecondaryUseActive()
                ? CabinetService.toggleShared(sp, server, lower)
                : CabinetService.travel(sp, server, lower);
        CabinetService.tell(sp, result);
        return InteractionResult.SUCCESS_SERVER;
    }
}
