package at.koopro.wizardsandbeasts.block.floo;

import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooAddress;
import at.koopro.wizardsandbeasts.floo.FlooCues;
import at.koopro.wizardsandbeasts.floo.FlooDeparture;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;


/**
 * Functional Floo Network travel block.
 * LORE: A fireplace connected to the Floo Network. Players throw Floo Powder into
 * the cold fireplace to light it, then step in and announce their destination.
 * Emits emerald-green dust particles and light level 10 when lit.
 * Managed by {@link at.koopro.wizardsandbeasts.floo.FlooNetworkManager} — must be
 * registered via {@code /wandb world floo register} before it accepts travel.
 */
public class FlooFireplaceBlock extends BaseEntityBlock {

    public static final MapCodec<FlooFireplaceBlock> CODEC = simpleCodec(FlooFireplaceBlock::new);

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FlooFireplaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIT, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new FlooFireplaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.FLOO_FIREPLACE.get(),
                FlooFireplaceBlockEntity::serverTick);
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
                                                         @NonNull BlockPos pos, @NonNull Player player,
                                                         @NonNull BlockHitResult hit) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            // Was a bare PASS. Right-clicking a fireplace is deliberate player input, and a
            // deliberate input that produces nothing at all is indistinguishable from a bug — the
            // player has no way to learn that the whole network is switched off on this world.
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("floo.wizards_and_beasts.fail.module_off"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(LIT)) {
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                if (level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be) {
                    // Dousing is checked before registration and before the seal, and deliberately:
                    // putting out a fire you can see is not a network operation, and refusing to let
                    // someone extinguish their own unregistered hearth would leave green fire burning
                    // in a room with no way at all to stop it.
                    if (sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty()) {
                        return douse((ServerLevel) level, pos, state, sp);
                    }
                    if (!be.isRegistered()) {
                        sp.displayClientMessage(
                                Component.translatable("floo.wizards_and_beasts.fail.unregistered"), true);
                        return InteractionResult.SUCCESS;
                    }
                    if (!be.isEnabled()) {
                        sp.displayClientMessage(
                                Component.translatable("floo.wizards_and_beasts.fail.sealed"), true);
                        FlooCues.sealed(sp);
                        return InteractionResult.SUCCESS;
                    }
                    FlooAccess.openPicker(sp, FlooNetworkManager.get((ServerLevel) sp.level()),
                            be.getNetworkAddress(), false);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.cold"), true);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * How long a douse stays armed. Five seconds.
     *
     * <p>Long enough to be a deliberate second press, short enough that an arming from across the
     * room cannot still be live when the player wanders back and crouches for some other reason.
     */
    private static final int DOUSE_CONFIRM_TICKS = 100;

    /** Game tick at which each player last armed a douse. Cleared with the player. */
    private static final PlayerScopedState<Long> DOUSE_ARMED =
            PlayerScopedState.create("floo_douse_confirm");

    /**
     * Put a hearth out by hand, on the second ask.
     *
     * <h2>Why it is confirmed</h2>
     * <p>Crouch-clicking is something players do constantly and by accident, and the cost of an
     * accidental douse is not symmetric with the cost of an accidental refusal: a fire put out by
     * mistake costs a pinch of powder to replace and strands anyone mid-departure from it, while a
     * douse that needs asking twice costs a second click. So it arms first and acts second.
     *
     * <p>The arming is per-player rather than per-hearth. Two people crouching at the same fire are
     * two separate intentions, and one of them arming should not hand the other a live douse they
     * never asked for.
     */
    private static InteractionResult douse(ServerLevel level, BlockPos pos, BlockState state,
                                            ServerPlayer player) {
        long now = level.getGameTime();
        Long armed = DOUSE_ARMED.get(player.getUUID());
        if (armed == null || now - armed > DOUSE_CONFIRM_TICKS) {
            DOUSE_ARMED.put(player.getUUID(), now);
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.douse.confirm"), true);
            return InteractionResult.SUCCESS;
        }
        DOUSE_ARMED.remove(player.getUUID());

        // extinguishAt takes the hearth dark with the flames, so LIT is not cleared here as well.
        // The explicit darken below is only for a hearth burning with no flames in front of it — a
        // world saved before flames existed, or a /setblock — which extinguishAt has nothing to find.
        FlooFlamesBlock.extinguishAt(level, pos, state.getValue(FACING));
        FlooFlamesBlock.darkenHearth(level, pos);
        FlooDeparture.cancelAllFrom(level, pos);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.1f);
        player.displayClientMessage(
                Component.translatable("floo.wizards_and_beasts.douse.done"), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * Name a fireplace onto the network with a name tag.
     *
     * <p>Registration was <b>admin-only</b> before this. {@code /wandb world floo register} sits
     * under {@code /wandb world}, which requires ADMIN, so a survival player could craft a Floo
     * Fireplace, place it, light it with powder — and never give it an address. Every hearth they
     * built was unreachable and the destination list stayed empty forever. The travel half of the
     * feature worked; the half that creates something to travel to could not be reached at all.
     *
     * <p>A vanilla name tag rather than a new item: naming a thing with a name tag is a convention a
     * player already knows, it costs an anvil level to write on, and it adds no registry entry, no
     * recipe and no art. The tag is consumed, so an address has a real cost.
     *
     * <p>Only on a <em>cold</em> hearth. A lit one is a travel prompt, and stealing that click to
     * rename would make the commonest interaction in the system ambiguous.
     */
    @Override
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack stack,
                                                   @NonNull BlockState state, @NonNull Level level,
                                                   @NonNull BlockPos pos, @NonNull Player player,
                                                   @NonNull InteractionHand hand,
                                                   @NonNull BlockHitResult hit) {
        // Floo Powder belongs to the item, and this is what lets it get there.
        //
        // The default return from BlockBehaviour.useItemOn is TRY_WITH_EMPTY_HAND, and
        // ServerPlayerGameMode answers that by running useWithoutItem *before* the held item's own
        // useOn. useWithoutItem returns SUCCESS on every path — the cold hearth's "you need Floo
        // Powder" line included — so the block consumed the click every time and
        // FlooPowderItem.useOn was never reached. Right-clicking a cold hearth with a pinch of
        // powder, which is the first step of the entire ritual, did nothing but tell the player to
        // use the item they were already holding.
        //
        // PASS rather than TRY_WITH_EMPTY_HAND: it neither consumes the action nor asks for the
        // empty-hand interaction, so the item runs. Only powder is diverted, so an empty hand and
        // every other item keep the block's own behaviour untouched.
        if (stack.is(MiscItemRegistry.FLOO_POWDER.get())) {
            return InteractionResult.PASS;
        }
        if (!stack.is(Items.NAME_TAG) || state.getValue(LIT)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("floo.wizards_and_beasts.fail.module_off"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }

        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom == null) {
            sp.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.unnamed_tag"), true);
            return InteractionResult.SUCCESS;
        }
        String address = custom.getString();
        FlooAddress.Validity validity = FlooAddress.validate(address);
        if (!validity.ok()) {
            sp.displayClientMessage(validity.message(), true);
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be)) {
            return InteractionResult.SUCCESS;
        }

        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) level);

        // Renaming an existing hearth is an owner's privilege. Checked against the entry rather than
        // against the block, because reaching the block is exactly what an intruder can already do -
        // and a hearth that could be renamed by anyone standing in front of it would make a private
        // address worth nothing.
        FlooRegistryEntry existing = manager.findByPos(level.dimension().identifier(), pos);
        if (existing != null && !FlooAccess.mayAdminister(sp, existing)) {
            sp.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.not_owner"), true);
            return InteractionResult.SUCCESS;
        }

        FlooNetworkManager.RegisterResult result = manager.register(
                address, level.dimension().identifier(), pos, true, Optional.of(sp.getUUID()));
        if (result == FlooNetworkManager.RegisterResult.ADDRESS_TAKEN) {
            sp.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.address_taken", address), true);
            return InteractionResult.SUCCESS;
        }

        be.setNetworkAddress(FlooAddress.display(address));
        be.setRegistered(true);
        be.setEnabled(true);
        if (!sp.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 1.2f);
        sp.displayClientMessage(Component.translatable(
                "floo.wizards_and_beasts.registered", FlooAddress.display(address)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(@NonNull BlockState state, @NonNull ServerLevel level,
                                               @NonNull BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        // Purge the network entry when the fireplace is gone (mined, blown up, pushed).
        if (!level.getBlockState(pos).is(this)) {
            FlooNetworkManager.get(level).unregisterByPos(level.dimension().identifier(), pos);
            // Anyone leaving from this hearth stops leaving. Their powder has not been spent yet, so
            // breaking a grate under a traveller costs them the trip and nothing else.
            FlooDeparture.cancelAllFrom(level, pos);
            // Put out this hearth's flames explicitly rather than relying on the neighbour update
            // FlooFlamesBlock.updateShape listens for. That update is the safety net for the ways a
            // hearth can leave without passing through here at all — /setblock, a worldgen overwrite
            // — while this is the ordinary case, and doing it here means the fire dies in the same
            // tick as the hearth instead of whenever the next neighbour notification happens to land.
            FlooFlamesBlock.extinguishAt(level, pos, state.getValue(FACING));
        }
    }

    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level,
                             @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (!state.getValue(LIT)) return;
        // VISUAL: emerald flame column that rises and roars (lore: "flames rose higher").
        DustParticleOptions greenDust = FlooCues.emerald(1.3f);
        int count = 6 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            double cx = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.55;
            double cy = pos.getY() + 0.15 + random.nextDouble() * 1.0;
            double cz = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.55;
            // Upward velocity sells the "rising" column.
            level.addParticle(greenDust, cx, cy, cz, 0, 0.06 + random.nextDouble() * 0.05, 0);
        }
        // Occasional ember flicker for the roaring look.
        if (random.nextInt(3) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    pos.getY() + 0.3 + random.nextDouble() * 0.6,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    0, 0.03, 0);
        }
    }
}
