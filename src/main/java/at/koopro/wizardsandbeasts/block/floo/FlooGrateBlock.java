package at.koopro.wizardsandbeasts.block.floo;

import at.koopro.wizardsandbeasts.floo.FlooCues;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import at.koopro.wizardsandbeasts.effect.FlooProtectedEffect;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooAddress;
import at.koopro.wizardsandbeasts.floo.FlooDeparture;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.floo.hearth.HearthPattern;
import at.koopro.wizardsandbeasts.floo.hearth.HearthPatterns;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The iron basket grate that stands in a hearth's opening: the heart of a player-built Floo hearth (Floo remake,
 * 2026-11-26). Logs go on it ({@link #FUEL}), an ordinary fire burns on them ({@link HearthFire#WARM}), and Floo Powder
 * turns that fire emerald ({@link HearthFire#FLOO}). The fire itself is drawn by {@code FlooGrateRenderer} so the green
 * roar can fill the whole opening and rise, not just this one block.
 *
 * <p>Low collision (4 px) so a traveller steps onto the grate and stands in the fire, as Harry and Ron stand upright in
 * the Burrow hearth (CANON.md).
 */
@NullMarked
public class FlooGrateBlock extends BaseEntityBlock {

    public static final MapCodec<FlooGrateBlock> CODEC = simpleCodec(FlooGrateBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FUEL = BooleanProperty.create("fuel");
    public static final EnumProperty<HearthFire> FIRE = EnumProperty.create("fire", HearthFire.class);

    private static final VoxelShape OUTLINE_NS = Block.box(2, 0, 3, 14, 10, 13);
    private static final VoxelShape OUTLINE_EW = Block.box(3, 0, 2, 13, 10, 14);
    private static final VoxelShape STEP = Block.box(0, 0, 0, 16, 4, 16);

    public FlooGrateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FUEL, false)
                .setValue(FIRE, HearthFire.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FUEL, FIRE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // The grate's front faces the room, i.e. the player placing it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? OUTLINE_NS : OUTLINE_EW;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return STEP;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlooGrateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.FLOO_GRATE.get(), FlooGrateBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Placing the grate last into a finished build is the usual way to finish a hearth: answer at once.
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof FlooGrateBlockEntity grate) {
            grate.recheck(server, state);
        }
    }

    // ── the ritual at the grate (P3) ───────────────────────────────────────

    /** How long before the same player is offered the destination list again. */
    private static final int REOFFER_COOLDOWN_TICKS = 60;
    private static final PlayerScopedState<Long> LAST_OFFER = PlayerScopedState.create("floo_grate_offer");
    /** A douse is armed by the first crouch-click and done by a second within five seconds. */
    private static final int DOUSE_CONFIRM_TICKS = 100;
    private static final PlayerScopedState<Long> DOUSE_ARMED = PlayerScopedState.create("floo_douse_confirm");

    /**
     * Logs, a light, a pinch, a name tag. Every item the ritual uses is answered here; anything else falls through to
     * the empty-hand answer. Crouching with powder never reaches this ({@code ServerPlayerGameMode} skips block use for
     * a sneaking player holding an item): a crouched pinch does nothing at a grate.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        boolean ritual = stack.is(ItemTags.LOGS) || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)
                || stack.is(MiscItemRegistry.FLOO_POWDER.get()) || stack.is(Items.NAME_TAG);
        if (!ritual) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)
                || !(level.getBlockEntity(pos) instanceof FlooGrateBlockEntity grate)) {
            return InteractionResult.SUCCESS;
        }
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            say(sp, "floo.wizards_and_beasts.fail.module_off");
            return InteractionResult.SUCCESS;
        }
        HearthFire fire = state.getValue(FIRE);
        if (stack.is(ItemTags.LOGS)) {
            if (state.getValue(FUEL)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            level.setBlock(pos, state.setValue(FUEL, true), 3);
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
            stack.consume(1, sp);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
            if (!state.getValue(FUEL)) {
                say(sp, "floo.wizards_and_beasts.hearth.needs_logs");
                return InteractionResult.SUCCESS;
            }
            if (fire.burning()) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            level.setBlock(pos, state.setValue(FIRE, HearthFire.WARM), 3);
            if (stack.is(Items.FLINT_AND_STEEL)) {
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.hurtAndBreak(1, sp, hand.asEquipmentSlot());
            } else {
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.consume(1, sp);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(MiscItemRegistry.FLOO_POWDER.get())) {
            if (pinch(server, pos, grate, sp)) {
                stack.consume(1, sp);
            }
            return InteractionResult.SUCCESS;
        }
        // Name tag: an address, only on a hearth that is not burning green (a green hearth is a travel prompt).
        if (fire == HearthFire.FLOO) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return nameHearth(server, pos, grate, stack, sp);
    }

    /**
     * Throw a pinch into this grate's fire. Every refusal comes before anything is spent, so a pinch is only ever lost
     * to a fire that can carry somebody: lit, in a finished hearth, on the network, not sealed.
     */
    private static boolean pinch(ServerLevel level, BlockPos pos, FlooGrateBlockEntity grate, @Nullable ServerPlayer by) {
        HearthFire fire = level.getBlockState(pos).getValue(FIRE);
        if (fire != HearthFire.WARM) {
            if (by != null) {
                say(by, fire == HearthFire.NONE ? "floo.wizards_and_beasts.hearth.needs_fire"
                        : "floo.wizards_and_beasts.hearth.already_green");
            }
            return false;
        }
        if (!grate.recheck(level, level.getBlockState(pos))) {
            if (by != null) {
                hint(level, pos, by);
            }
            return false;
        }
        if (!grate.isRegistered() || !grate.isEnabled()) {
            if (by != null) {
                say(by, grate.isRegistered() ? "floo.wizards_and_beasts.fail.sealed"
                        : "floo.wizards_and_beasts.fail.unregistered");
            }
            return false;
        }
        grate.throwPinch(level);
        FlooCues.ignite(level, pos);
        return true;
    }

    /** Name the hearth onto the network with a named name tag (moved from the retired 1x1 hearth). */
    private static InteractionResult nameHearth(ServerLevel level, BlockPos pos, FlooGrateBlockEntity grate,
                                                ItemStack stack, ServerPlayer sp) {
        if (!grate.recheck(level, level.getBlockState(pos))) {
            hint(level, pos, sp);
            return InteractionResult.SUCCESS;
        }
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom == null) {
            say(sp, "floo.wizards_and_beasts.fail.unnamed_tag");
            return InteractionResult.SUCCESS;
        }
        String address = custom.getString();
        FlooAddress.Validity validity = FlooAddress.validate(address);
        if (!validity.ok()) {
            sp.displayClientMessage(validity.message(), true);
            return InteractionResult.SUCCESS;
        }
        FlooNetworkManager manager = FlooNetworkManager.get(level);
        // Renaming is the owner's privilege, checked against the entry: reaching the grate is what an intruder can do.
        FlooRegistryEntry existing = manager.findByPos(level.dimension().identifier(), pos);
        if (existing != null && !FlooAccess.mayAdminister(sp, existing)) {
            say(sp, "floo.wizards_and_beasts.fail.not_owner");
            return InteractionResult.SUCCESS;
        }
        FlooNetworkManager.RegisterResult result = manager.register(
                address, level.dimension().identifier(), pos, true, Optional.of(sp.getUUID()));
        if (result == FlooNetworkManager.RegisterResult.ADDRESS_TAKEN) {
            sp.displayClientMessage(Component.translatable("floo.wizards_and_beasts.fail.address_taken", address), true);
            return InteractionResult.SUCCESS;
        }
        grate.setNetworkAddress(FlooAddress.display(address));
        grate.setRegistered(true);
        grate.setEnabled(true);
        stack.consume(1, sp);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.2F);
        sp.displayClientMessage(Component.translatable("floo.wizards_and_beasts.registered", FlooAddress.display(address)), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * An empty hand: crouching puts the fire out (asked twice); otherwise the grate says what it needs next, and a
     * green, registered hearth opens its destination list (as stepping in does).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)
                || !(level.getBlockEntity(pos) instanceof FlooGrateBlockEntity grate)) {
            return InteractionResult.SUCCESS;
        }
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            say(sp, "floo.wizards_and_beasts.fail.module_off");
            return InteractionResult.SUCCESS;
        }
        HearthFire fire = state.getValue(FIRE);
        if (sp.isShiftKeyDown() && fire.burning()) {
            return douse(server, pos, state, sp);
        }
        if (!grate.recheck(server, state)) {
            hint(server, pos, sp);
            return InteractionResult.SUCCESS;
        }
        if (fire == HearthFire.FLOO && grate.isRegistered() && grate.isEnabled()) {
            FlooAccess.openPicker(sp, FlooNetworkManager.get(server), grate.getNetworkAddress());
            return InteractionResult.SUCCESS;
        }
        say(sp, !grate.isRegistered() ? "floo.wizards_and_beasts.fail.unregistered"
                : !grate.isEnabled() ? "floo.wizards_and_beasts.fail.sealed"
                : !state.getValue(FUEL) ? "floo.wizards_and_beasts.hearth.needs_logs"
                : fire == HearthFire.NONE ? "floo.wizards_and_beasts.hearth.light_it"
                : "floo.wizards_and_beasts.hearth.throw_pinch");
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult douse(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        long now = level.getGameTime();
        Long armed = DOUSE_ARMED.get(player.getUUID());
        if (armed == null || now - armed > DOUSE_CONFIRM_TICKS) {
            DOUSE_ARMED.put(player.getUUID(), now);
            say(player, "floo.wizards_and_beasts.douse.confirm");
            return InteractionResult.SUCCESS;
        }
        DOUSE_ARMED.remove(player.getUUID());
        level.setBlock(pos, state.setValue(FIRE, HearthFire.NONE), 3);
        FlooDeparture.cancelAllFrom(level, pos);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.1F);
        say(player, "floo.wizards_and_beasts.douse.done");
        return InteractionResult.SUCCESS;
    }

    /**
     * The build hint: against the style the build is closest to, mark the next missing block with green motes (seen
     * only by this player) and say what belongs there and how many are still to go. Building is the point of the
     * hearth (owner 11-26), so the grate teaches it rather than refusing in silence.
     */
    private static void hint(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Direction facing = level.getBlockState(pos).getValue(FACING);
        Optional<Map.Entry<Identifier, HearthPattern>> closest = HearthPatterns.all().entrySet().stream()
                .min(Comparator.<Map.Entry<Identifier, HearthPattern>>comparingInt(e -> e.getValue()
                        .mismatches(pos, facing, HearthPatterns.check(level, e.getValue())).size())
                        .thenComparing(e -> e.getKey().toString()));
        if (closest.isEmpty()) {
            say(player, "floo.wizards_and_beasts.hearth.no_styles");
            return;
        }
        HearthPattern pattern = closest.get().getValue();
        List<HearthPattern.Cell> missing = pattern.mismatches(pos, facing, HearthPatterns.check(level, pattern));
        if (missing.isEmpty()) {
            return;
        }
        HearthPattern.Cell next = missing.getFirst();
        BlockPos at = HearthPattern.place(pos, facing, next);
        level.sendParticles(player, new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), FlooCues.EMERALD),
                true, true, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.0);
        player.displayClientMessage(Component.translatable("floo.wizards_and_beasts.hearth.unfinished",
                part(pattern, next.symbol()), missing.size()), true);
    }

    /** What a symbol asks for, in words. */
    private static Component part(HearthPattern pattern, char symbol) {
        if (symbol == HearthPattern.OPEN) {
            return Component.translatable("floo.wizards_and_beasts.hearth.part.open");
        }
        String entry = pattern.key().getOrDefault(String.valueOf(symbol), "?");
        String name = entry.substring(entry.indexOf(':') + 1).replace('/', '.');
        return Component.translatableWithFallback("floo.wizards_and_beasts.hearth.part." + name, entry);
    }

    private static void say(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    /**
     * What stands in the grate's cell. Thrown Floo Powder lands in the fire and turns it (the canon gesture: "threw it
     * into the fire"). In green fire the living are shielded and a player is offered the destination list; in an
     * ordinary fire they burn, as at a campfire: the powder is what makes it safe.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier applier, boolean actuallyInside) {
        if (!actuallyInside || !(level instanceof ServerLevel server) || !ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            return;
        }
        HearthFire fire = state.getValue(FIRE);
        if (entity instanceof ItemEntity item && item.getItem().is(MiscItemRegistry.FLOO_POWDER.get())) {
            if (fire == HearthFire.WARM && level.getBlockEntity(pos) instanceof FlooGrateBlockEntity grate
                    && pinch(server, pos, grate, item.getOwner() instanceof ServerPlayer thrower ? thrower : null)) {
                item.getItem().shrink(1);
                if (item.getItem().isEmpty()) {
                    item.discard();
                }
            }
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (fire == HearthFire.FLOO) {
            living.addEffect(new MobEffectInstance(ModEffects.FLOO_PROTECTED, FlooProtectedEffect.REFRESH_TICKS, 0,
                    true, false, false));
            if (entity instanceof ServerPlayer player && offerDue(player, level.getGameTime())
                    && level.getBlockEntity(pos) instanceof FlooGrateBlockEntity grate && grate.formed()) {
                LAST_OFFER.put(player.getUUID(), level.getGameTime());
                if (!grate.isRegistered()) {
                    say(player, "floo.wizards_and_beasts.fail.unregistered");
                } else if (!grate.isEnabled()) {
                    say(player, "floo.wizards_and_beasts.fail.sealed");
                    FlooCues.sealed(player);
                } else {
                    FlooAccess.openPicker(player, FlooNetworkManager.get(server), grate.getNetworkAddress());
                }
            }
        } else if (fire == HearthFire.WARM && !living.hasEffect(ModEffects.FLOO_PROTECTED)) {
            living.hurtServer(server, level.damageSources().campfire(), 1.0F);
        }
    }

    private static boolean offerDue(ServerPlayer player, long now) {
        Long last = LAST_OFFER.get(player.getUUID());
        return last == null || now - last >= REOFFER_COOLDOWN_TICKS;
    }

    /** Called on arrival, so the list does not reopen in the face of somebody who has just come out of this fire. */
    public static void suppressOfferFor(UUID playerId, long now) {
        LAST_OFFER.put(playerId, now);
    }

    /** The grate gone (mined, blown up, pushed): its address goes with it and nobody leaves from it. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) {
            FlooNetworkManager.get(level).unregisterByPos(level.dimension().identifier(), pos);
            FlooDeparture.cancelAllFrom(level, pos);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        HearthFire fire = state.getValue(FIRE);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        switch (fire) {
            case WARM -> {
                if (random.nextInt(10) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                            0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
                }
                for (int i = 0; i < 2; i++) {
                    level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.5,
                            y + 0.4 + random.nextDouble() * 0.4, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
                }
                if (random.nextInt(6) == 0) {
                    level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
                }
            }
            case FLOO -> {
                // Green motes rising across the whole opening (three wide), the roar's sparks.
                Direction across = state.getValue(FACING).getClockWise();
                for (int i = 0; i < 3; i++) {
                    double t = (random.nextDouble() - 0.5) * 2.6;
                    level.addParticle(new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), FlooCues.EMERALD),
                            x + across.getStepX() * t, y + random.nextDouble() * 2.4, z + across.getStepZ() * t,
                            0, 0.06 + random.nextDouble() * 0.05, 0);
                }
                if (random.nextInt(8) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                            1.0F, 0.6F + random.nextFloat() * 0.2F, false);
                }
            }
            case NONE -> {
            }
        }
    }
}
