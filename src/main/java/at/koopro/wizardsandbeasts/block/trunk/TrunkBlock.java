package at.koopro.wizardsandbeasts.block.trunk;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.trunk.ExtensionCharmService;
import at.koopro.wizardsandbeasts.trunk.TrunkAccessMode;
import at.koopro.wizardsandbeasts.trunk.TrunkArchetype;
import at.koopro.wizardsandbeasts.trunk.TrunkRecord;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;
import at.koopro.wizardsandbeasts.trunk.TrunkTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Placed, directional trunk block (all tiers: Traveller's / Expanded / Master's / Moody's / Newt's Case).
 * Lore: you do not enter a trunk you are holding — you set it down and climb in. Primary interaction
 * descends into the pocket dimension at the selected lock's compartment; sneak-interaction cycles the
 * active lock (multi-lock trunks) or toggles the Muggle-Worthy disguise (single-lock trunks). A
 * picked/broken block drops a {@link net.minecraft.world.item.BlockItem} carrying the packed base case
 * id ({@link ModDataComponents#POCKET_CASE_ID}) plus its Muggle-Worthy flag, so re-placing re-links the
 * identical compartments with zero content loss.
 *
 * <p>{@link #radiusTier} sets the pocket size; {@link #lockCount} is independent (Newt's Case is a large
 * single-habitat space, Expanded is 3 locks, Master's/Moody's are 7). Master's-grade trunks (≥7 locks)
 * designate one deterministic decoy lock that opens a small empty compartment; no trap, no damage.
 */
public class TrunkBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Trunks with this many locks or more hide one dead compartment behind a decoy lock. */
    private static final int DECOY_MIN_LOCKS = 7;

    public static final MapCodec<TrunkBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TrunkTier.CODEC.fieldOf("radiusTier").forGetter(block -> block.radiusTier),
            Codec.INT.fieldOf("lockCount").forGetter(block -> block.lockCount),
            TrunkArchetype.CODEC.fieldOf("archetype").forGetter(block -> block.archetype),
            propertiesCodec()
    ).apply(instance, TrunkBlock::new));

    private final TrunkTier radiusTier;
    private final int lockCount;
    private final TrunkArchetype archetype;

    public TrunkBlock(@NonNull TrunkTier radiusTier, int lockCount, @NonNull TrunkArchetype archetype,
                      BlockBehaviour.@NonNull Properties properties) {
        super(properties);
        this.radiusTier = radiusTier;
        this.lockCount = Math.max(1, lockCount);
        this.archetype = archetype;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public @NonNull TrunkTier radiusTier() {
        return radiusTier;
    }

    public @NonNull TrunkArchetype archetype() {
        return archetype;
    }

    public int lockCount() {
        return lockCount;
    }

    /** Stable per-lock compartment id: same derivation as the legacy Moody's trunk item. Public so the
     *  pocket-ingress spell can target the identical lock-1 compartment the block itself descends into. */
    public static @NonNull UUID lockCaseId(@NonNull UUID base, int lock) {
        return new UUID(base.getMostSignificantBits(), base.getLeastSignificantBits() ^ (long) lock);
    }

    public boolean hasDecoy() {
        return lockCount >= DECOY_MIN_LOCKS;
    }

    /** Single-lock trunks (Traveller's, Newt's Case) offer a Muggle-Worthy toggle instead of lock-cycling. */
    private boolean isSingleLock() {
        return lockCount == 1;
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new TrunkBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    /** Body plus lid, inset one pixel all round — the chest the model actually draws. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
                                                        @NonNull BlockPos pos, @NonNull Player player,
                                                        @NonNull BlockHitResult hit) {
        // Registration is never gated; entry and lock-cycle are. An inert placed block is acceptable.
        if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.literal("The trunk's latches will not budge.").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof TrunkBlockEntity trunkEntity)) {
            return InteractionResult.PASS;
        }

        if (serverPlayer.isShiftKeyDown()) {
            if (isSingleLock()) {
                toggleMuggleWorthy(serverLevel, pos, serverPlayer, trunkEntity);
            } else {
                cycleLock(serverLevel, pos, serverPlayer, trunkEntity);
            }
        } else {
            descend(serverLevel, pos, serverPlayer, trunkEntity);
        }
        return InteractionResult.SUCCESS;
    }

    private void cycleLock(ServerLevel level, BlockPos pos, ServerPlayer player, TrunkBlockEntity trunkEntity) {
        int next = trunkEntity.cycleActiveLock();
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0f, 1.0f);
        player.displayClientMessage(
                Component.literal("Lock " + next + " of " + lockCount() + " selected.").withStyle(ChatFormatting.GOLD),
                true);
    }

    private void toggleMuggleWorthy(ServerLevel level, BlockPos pos, ServerPlayer player, TrunkBlockEntity trunkEntity) {
        boolean next = !trunkEntity.isMuggleWorthy();
        trunkEntity.setMuggleWorthy(next);
        // Keep the bound record in sync so players already inside see the change.
        UUID base = trunkEntity.getBaseCaseId();
        if (base != null) {
            UUID caseId = lockCaseId(base, 1);
            TrunkRegistryData data = TrunkRegistryData.get(level);
            data.getCasePocket(caseId).ifPresent(record ->
                    data.updateRecord(record.pocketId(), rec -> rec.withMuggleWorthy(next)));
        }
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0f, 1.0f);
        player.displayClientMessage(next
                        ? Component.literal("Muggle-Worthy switch engaged.").withStyle(ChatFormatting.DARK_GREEN)
                        : Component.literal("The lock clicks over. The case is open.").withStyle(ChatFormatting.AQUA),
                true);
    }

    private void descend(ServerLevel level, BlockPos pos, ServerPlayer player, TrunkBlockEntity trunkEntity) {
        UUID base = trunkEntity.getOrCreateBaseCaseId();
        int lock = trunkEntity.getActiveLock();
        boolean decoy = hasDecoy() && lock == decoyLock(base);
        boolean muggleWorthy = trunkEntity.isMuggleWorthy();

        UUID compartmentId = lockCaseId(base, lock);
        TrunkArchetype compartmentArchetype = decoy ? TrunkArchetype.FIELD_CAMP : archetype;
        String templateId = decoy ? "trunk_decoy" : "trunk_lock_" + lock;
        // Decoy is a small, plain shell (smallest preset); real compartments use this trunk's radius tier.
        TrunkTier spaceTier = decoy ? TrunkTier.TIER_1 : radiusTier;

        TrunkRecord record = ExtensionCharmService.getOrCreatePocket(
                player, compartmentId, compartmentArchetype, templateId,
                TrunkAccessMode.SEALED, muggleWorthy, false, spaceTier);

        TrunkRegistryData data = TrunkRegistryData.get(level);
        if (data.isImpounded(record.pocketId())) {
            player.displayClientMessage(
                    Component.literal("This compartment has been impounded by the Ministry of Magic.")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }
        // Muggle-Worthy blocks non-owner entry; the owner always gets in.
        if (record.muggleWorthy() && !record.owner().equals(player.getUUID())) {
            player.displayClientMessage(
                    Component.literal("The case appears to contain only travel supplies."), true);
            return;
        }
        if (decoy) {
            player.displayClientMessage(
                    Component.literal("The compartment is empty. Wrong lock.").withStyle(ChatFormatting.DARK_GRAY), true);
        }

        // Return anchor = the trunk itself, so climbing out lands the player back at the block.
        ExtensionCharmService.enterPocket(player, record, level, pos.above());
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                            @Nullable LivingEntity placer, @NonNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        if (!(level.getBlockEntity(pos) instanceof TrunkBlockEntity trunkEntity)) return;
        UUID packed = stack.get(ModDataComponents.POCKET_CASE_ID.get());
        if (packed != null) {
            trunkEntity.setBaseCaseId(packed);
        }
        if (stack.getOrDefault(ModDataComponents.POCKET_MUGGLE_WORTHY.get(), false)) {
            trunkEntity.setMuggleWorthy(true);
        }
    }

    @Override
    public @NonNull BlockState playerWillDestroy(@NonNull Level level, @NonNull BlockPos pos,
                                                 @NonNull BlockState state, @NonNull Player player) {
        // Pack-up: drop a BlockItem carrying the packed base id + Muggle-Worthy flag so re-placing re-links
        // the same compartments. The block has no loot table, so this manual drop is the only one (no dupe).
        if (!level.isClientSide() && !player.isCreative()
                && level.getBlockEntity(pos) instanceof TrunkBlockEntity trunkEntity) {
            ItemStack drop = new ItemStack(this);
            UUID base = trunkEntity.getBaseCaseId();
            if (base != null) {
                drop.set(ModDataComponents.POCKET_CASE_ID.get(), base);
            }
            if (trunkEntity.isMuggleWorthy()) {
                drop.set(ModDataComponents.POCKET_MUGGLE_WORTHY.get(), true);
            }
            popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Deterministic decoy lock index (1..lockCount), varying per trunk so it is not always the same lock. */
    private int decoyLock(UUID base) {
        long hash = base.getMostSignificantBits() ^ base.getLeastSignificantBits();
        return 1 + Math.floorMod((int) hash, lockCount());
    }
}
