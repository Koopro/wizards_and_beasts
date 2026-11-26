package at.koopro.wizardsandbeasts.wand.bench;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.item.wand.WandModuleHooks;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The wandmaker's bench: a joiner's workbench two blocks wide (owner, 2026-11-26). The turning end ({@link
 * BenchPart#MAIN}) holds the lathe, the block entity and the drop. The marrying end ({@link BenchPart#SIDE}) holds the
 * rack, the vise and the lantern.
 *
 * <p>The halves hold together the way a bed's do. Placing the main half sets the side half, and placement needs both
 * blocks free. Breaking either half takes both, and the loot table pays out once, from the working part. A creative
 * break of the side half clears the main half without a drop. Every click goes to the working part ({@link #mainOf}).
 *
 * <p><b>Which side.</b> The rig is authored facing north with the side half at model +X. GeckoLib mirrors model X
 * ({@code BakedModelFactory} bakes {@code -(origin.x + size.x)}) and rotates by facing, so the side half stands at
 * {@code facing.getCounterClockWise()}: on the right of a wizard who faces the bench's front. See {@link #sideOf}.
 *
 * <p>An old bench (no properties) loads as {@link BenchPart#SOLO} and grows its side half when it can; see
 * {@link WandmakersBenchBlockEntity#serverTick}.
 */
public class WandmakersBenchBlock extends BaseEntityBlock implements EntityBlock {
    public static final MapCodec<WandmakersBenchBlock> CODEC = simpleCodec(WandmakersBenchBlock::new);

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BenchPart> PART = EnumProperty.create("part", BenchPart.class);

    /** The bench top is fifteen pixels up; the rack and lantern above it are drawn, not collided with. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    public WandmakersBenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BenchPart.SOLO));
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    // -- geometry of the pair ---------------------------------------------------------------------

    /** Where a bench facing {@code facing} with its working part at {@code main} keeps its side half. */
    public static BlockPos sideOf(BlockPos main, Direction facing) {
        return main.relative(facing.getCounterClockWise());
    }

    /** The working part (main or solo) of the bench whose block is at {@code pos}. */
    public static BlockPos mainOf(BlockState state, BlockPos pos) {
        return state.getValue(PART) == BenchPart.SIDE ? pos.relative(state.getValue(FACING).getClockWise()) : pos;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos side = sideOf(context.getClickedPos(), facing);
        Level level = context.getLevel();
        if (!level.getWorldBorder().isWithinBounds(side) || !level.getBlockState(side).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, BenchPart.MAIN);
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                            @Nullable LivingEntity placer, @NonNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && state.getValue(PART) == BenchPart.MAIN) {
            level.setBlock(sideOf(pos, state.getValue(FACING)), state.setValue(PART, BenchPart.SIDE), 3);
        }
    }

    /** Each paired half needs its partner beside it, facing the same way; a solo bench needs nothing. */
    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess tickAccess, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        BenchPart part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        Direction toPartner = part == BenchPart.MAIN ? facing.getCounterClockWise()
                : part == BenchPart.SIDE ? facing.getClockWise() : null;
        if (toPartner != null && direction == toPartner) {
            BenchPart wanted = part == BenchPart.MAIN ? BenchPart.SIDE : BenchPart.MAIN;
            boolean paired = neighborState.is(this) && neighborState.getValue(PART) == wanted
                    && neighborState.getValue(FACING) == facing;
            if (!paired) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    /** A creative break of the side half clears the main half silently, so no free bench drops (as for beds). */
    @Override
    public @NonNull BlockState playerWillDestroy(@NonNull Level level, @NonNull BlockPos pos,
                                                 @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide() && state.getValue(PART) == BenchPart.SIDE && player.preventsBlockDrops()) {
            BlockPos main = mainOf(state, pos);
            BlockState mainState = level.getBlockState(main);
            if (mainState.is(this) && mainState.getValue(PART) == BenchPart.MAIN) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, main, Block.getId(mainState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected @NonNull BlockState rotate(@NonNull BlockState state, @NonNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NonNull BlockState mirror(@NonNull BlockState state, @NonNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    // -- use --------------------------------------------------------------------------------------

    private static @Nullable WandmakersBenchBlockEntity benchAt(Level level, BlockState state, BlockPos pos) {
        return level.getBlockEntity(mainOf(state, pos)) instanceof WandmakersBenchBlockEntity bench ? bench : null;
    }

    /**
     * A finished wand in hand opens the bench's second role, refining that wand; anything else is the bench as
     * ever. The click is the bench's either way when a wand is held, so the wand does not also start a cast.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof at.koopro.wizardsandbeasts.item.wand.BenchToolItem toolItem) {
            return layTool(stack, toolItem.tool(), state, level, pos, player);
        }
        if (!at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu.isFinishedWand(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!WandModuleHooks.isWandsEnabled()) {
            return InteractionResult.FAIL;
        }
        WandmakersBenchBlockEntity bench = benchAt(level, state, pos);
        if (bench == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockPos main = bench.getBlockPos();
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, p) -> new at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu(id, inventory, bench),
                    Component.translatable("gui.wizards_and_beasts.wand_refine")), buf -> buf.writeBlockPos(main));
        }
        return InteractionResult.SUCCESS;
    }

    /** A tool in hand is laid on the bench, unless that tool is already there. Creative keeps its copy. */
    private static InteractionResult layTool(ItemStack stack, BenchTool tool, BlockState state, Level level,
                                             BlockPos pos, Player player) {
        WandmakersBenchBlockEntity bench = benchAt(level, state, pos);
        if (bench == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Component name = stack.getHoverName();
        if (!bench.placeTool(tool)) {
            player.displayClientMessage(Component.translatable("block.wizards_and_beasts.wandmakers_bench.tool_already", name), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        stack.consume(1, player);
        level.playSound(null, bench.getBlockPos(), net.minecraft.sounds.SoundEvents.WOOD_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.2f);
        player.displayClientMessage(Component.translatable("block.wizards_and_beasts.wandmakers_bench.tool_placed", name), true);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!WandModuleHooks.isWandsEnabled()) {
            return InteractionResult.FAIL;
        }
        WandmakersBenchBlockEntity bench = benchAt(level, state, pos);
        if (bench == null) {
            return InteractionResult.PASS;
        }
        // Sneaking with an empty hand takes the tools back; with none on the bench it opens the bench as ever.
        if (player.isSecondaryUseActive() && bench.toolMask() != 0) {
            if (!level.isClientSide()) {
                for (ItemStack tool : bench.takeTools()) {
                    at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, tool);
                }
                player.displayClientMessage(Component.translatable("block.wizards_and_beasts.wandmakers_bench.tools_taken"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            bench.recalcTierScore();
            List<Identifier> enhIds = new ArrayList<>();
            for (BlockPos ep : bench.getDetectedEnhancers()) {
                Identifier bid = BuiltInRegistries.BLOCK.getKey(level.getBlockState(ep).getBlock());
                if (bid != null) {
                    enhIds.add(bid);
                }
            }
            List<Identifier> enhSnapshot = List.copyOf(enhIds);
            MenuProvider provider = new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.wizards_and_beasts.wandmakers_bench");
                }

                @Override
                public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
                    return new WandmakersBenchMenu(syncId, inv, bench, enhSnapshot);
                }
            };
            BlockPos main = bench.getBlockPos();
            serverPlayer.openMenu(provider, buf -> {
                buf.writeBlockPos(main);
                buf.writeVarInt(enhSnapshot.size());
                for (Identifier id : enhSnapshot) {
                    buf.writeUtf(id.toString(), 320);
                }
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean isMoving) {
        if (level.getBlockEntity(pos) instanceof WandmakersBenchBlockEntity bench) {
            bench.recalcTierScore();
        }
    }

    /** Only the working part has a block entity: the side half is furniture. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART).isWorking() ? new WandmakersBenchBlockEntity(pos, state) : null;
    }

    /**
     * Drawn by its GeckoLib rig from the working part, both halves at once.
     *
     * <p>The JSON model is still generated and still used. The block item shows it in the inventory, and break
     * particles sample it. It is not what a placed bench renders.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!state.getValue(PART).isWorking()) {
            return null;
        }
        if (!level.isClientSide()) {
            // Two jobs: grow a solo bench into a pair when there is room, and notice when a blank or a core lands
            // on the bench, so the rig can switch to its working clip for everyone who can see it.
            return createTickerHelper(type, ModBlockEntities.WANDMAKERS_BENCH.get(),
                    WandmakersBenchBlockEntity::serverTick);
        }
        return (tickerLevel, tickerPos, tickerState, blockEntity) -> {
            if (!(blockEntity instanceof WandmakersBenchBlockEntity bench)) {
                return;
            }
            if (tickerLevel.getGameTime() % 10 != 0) {
                return;
            }
            if (bench.getDetectedEnhancers().isEmpty()) {
                return;
            }
            RandomSource random = tickerLevel.getRandom();
            for (BlockPos enhancerPos : bench.getDetectedEnhancers()) {
                double startX = enhancerPos.getX() + 0.5;
                double startY = enhancerPos.getY() + 0.75;
                double startZ = enhancerPos.getZ() + 0.5;
                double targetX = tickerPos.getX() + 0.5;
                double targetY = tickerPos.getY() + 0.8;
                double targetZ = tickerPos.getZ() + 0.5;
                double velocityX = (targetX - startX) * 0.05 + (random.nextDouble() - 0.5) * 0.01;
                double velocityY = (targetY - startY) * 0.05 + 0.01;
                double velocityZ = (targetZ - startZ) * 0.05 + (random.nextDouble() - 0.5) * 0.01;
                tickerLevel.addParticle(at.koopro.wizardsandbeasts.particle.MagicMotes.magic(), startX, startY, startZ, velocityX, velocityY, velocityZ);
            }
        };
    }
}
