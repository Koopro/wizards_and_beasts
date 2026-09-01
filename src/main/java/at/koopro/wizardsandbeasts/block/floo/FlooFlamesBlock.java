package at.koopro.wizardsandbeasts.block.floo;

import at.koopro.wizardsandbeasts.effect.FlooProtectedEffect;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooCues;
import at.koopro.wizardsandbeasts.floo.FlooDeparture;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.floo.FlooFlamePlacement;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.floo.FlooBlockSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * The emerald fire a wizard steps into.
 *
 * <h2>Why it is not fire</h2>
 * <p>This extends {@link Block}, never {@code BaseFireBlock}, and that is the whole safety design.
 * {@code BaseFireBlock.entityInside} applies {@code InsideBlockEffectType.FIRE_IGNITE} and then hurts
 * the entity with {@code damageSources().inFire()}; {@code FireBlock} adds spreading and block burning
 * on top. Overriding "all the damage paths" of a fire block is a list nobody can be sure they have
 * finished, and one vanilla update adds another. Being a plain block means there is no path to
 * override: nothing in the game asks this block to burn anything, because nothing in the game thinks
 * it is fire.
 *
 * <p>It is likewise not in the {@code #minecraft:fire} block tag, so vanilla's own fire logic — spread,
 * extinguishing by water, the campfire and candle interactions — never sees it either.
 *
 * <h2>Charges, not a timer alone</h2>
 * <p>{@link #CHARGES} counts hops. A pinch of powder buys {@value #MAX_CHARGES} of them; each
 * departure spends one, and the fire dies when they run out. On top of that each charge burns down on
 * its own after {@value #TICKS_PER_CHARGE} ticks, so a hearth lit and abandoned goes cold instead of
 * lighting a room forever.
 *
 * <p>Two independent expiries rather than one, because they answer different questions: charges are
 * what stops a single pinch of powder becoming an unlimited network, and the timer is what stops the
 * world filling with green light nobody is using.
 *
 * <h2>Relationship to the hearth</h2>
 * <p>The hearth remains the network node — it holds the address, the facing and the registration.
 * These flames are only the volume, and they carry a copy of the hearth's facing so they can find it
 * again (see {@link FlooFlamePlacement}). Lighting also sets the hearth's own {@code LIT}, so
 * everything that already keyed on a lit hearth — {@code findNearbyLitFireplace}, the arrival glow,
 * the block sync payload — keeps working untouched.
 */
public class FlooFlamesBlock extends Block {

    public static final MapCodec<FlooFlamesBlock> CODEC = simpleCodec(FlooFlamesBlock::new);

    /** Hops left in this fire. */
    public static final int MAX_CHARGES = 3;
    public static final IntegerProperty CHARGES = IntegerProperty.create("charges", 1, MAX_CHARGES);

    /** Copied from the hearth so the flames can find their way back to it. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** How long one unused charge lasts when nothing has configured it. A minute per hop. */
    public static final int TICKS_PER_CHARGE = 1200;

    /**
     * The configured charge lifetime.
     *
     * <p>{@link #MAX_CHARGES} deliberately stays a constant beside it: it sizes the {@link #CHARGES}
     * blockstate property, which is built once at registry time, so a config value read later could
     * never have widened it and a knob for it would only be a lie.
     */
    public static int ticksPerCharge() {
        int configured = at.koopro.wizardsandbeasts.Config.flooTicksPerCharge;
        return configured > 0 ? configured : TICKS_PER_CHARGE;
    }

    /**
     * How long a player must be out of the flames before stepping in offers travel again.
     *
     * <p>{@code entityInside} runs every tick a player overlaps the block, so without this the
     * destination screen would be re-sent twenty times a second and the player could never close it.
     * The window is also what makes <em>arriving</em> near flames survivable: a traveller lands at
     * the destination hearth and must be able to walk out without being immediately offered another
     * hop.
     */
    private static final int REOFFER_COOLDOWN_TICKS = 60;

    /** Last game tick each player was offered the destination list. Cleared with the player. */
    private static final PlayerScopedState<Long> LAST_OFFER =
            PlayerScopedState.create("floo_flames_offer");

    public FlooFlamesBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CHARGES, MAX_CHARGES)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NonNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGES, FACING);
    }

    /**
     * No outline and no collision: the flames are a volume, not an obstacle.
     *
     * <p>The empty <em>outline</em> is the load-bearing half. Block picking raytraces
     * {@code getShape}, so any visible box here would sit between the player's crosshair and the
     * hearth behind it — and the hearth's right-click is how a Floo address is written with a name
     * tag and how the destination list is opened without stepping in. Green fire that ate those
     * clicks would break the two interactions the network is made of. With nothing to pick, a click
     * passes straight through to the hearth exactly as it did before flames existed.
     *
     * <p>Being unpickable also means these cannot be mined, which is why every way out of existence
     * is deliberate: a spent charge, the burn-down timer, the hearth going away, or
     * {@link #extinguish}.
     *
     * <p>{@code entityInside} is unaffected — vanilla drives it from
     * {@code getEntityInsideCollisionShape}, which stays the default full cube.
     */
    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return Shapes.empty();
    }

    // -- lighting and lifetime -------------------------------------------------------------------

    /**
     * Light or refresh the flames in front of {@code fireplacePos}.
     *
     * <p>Refuses when the target block is neither existing flames, ordinary fire, nor something
     * vanilla already considers replaceable (air, tall grass, snow), so lighting a hearth can never
     * destroy whatever a player built in front of it. Waterlogged replaceables are refused too — a
     * hearth flooded to the brim should not burn.
     *
     * @return true when flames are burning there afterwards
     */
    public static boolean light(ServerLevel level, BlockPos fireplacePos, Direction facing) {
        BlockPos flamePos = FlooFlamePlacement.flamePos(fireplacePos, facing);
        if (!canBurnAt(level, flamePos)) {
            return false;
        }
        level.setBlock(flamePos, ModBlocks.FLOO_FLAMES.get().defaultBlockState()
                .setValue(CHARGES, MAX_CHARGES)
                .setValue(FACING, facing), 3);
        level.scheduleTick(flamePos, ModBlocks.FLOO_FLAMES.get(), ticksPerCharge());
        return true;
    }

    /**
     * Bring an existing fire back to {@link #MAX_CHARGES}, or light one if none is burning.
     *
     * <p>This is what a second pinch of powder buys at a hearth that is already green. Lighting alone
     * could not express it: {@link #light} only ever sets a full-charge fire at a position it is
     * willing to overwrite, and the interesting case — a fire down to its last hop, in front of a
     * hearth that is already {@code LIT} — needs the charges reset and the burn-down clock pushed back
     * without the hearth's own state changing at all.
     *
     * @return true when a full fire is burning there afterwards
     */
    public static boolean restoke(ServerLevel level, BlockPos fireplacePos, Direction facing) {
        BlockPos flamePos = FlooFlamePlacement.flamePos(fireplacePos, facing);
        BlockState existing = level.getBlockState(flamePos);
        if (!existing.is(ModBlocks.FLOO_FLAMES.get())) {
            return light(level, fireplacePos, facing);
        }
        level.setBlock(flamePos, existing.setValue(CHARGES, MAX_CHARGES).setValue(FACING, facing), 3);
        level.scheduleTick(flamePos, ModBlocks.FLOO_FLAMES.get(), ticksPerCharge());
        return true;
    }

    /** Whether flames could stand at {@code flamePos} without destroying anything that matters. */
    public static boolean canBurnAt(ServerLevel level, BlockPos flamePos) {
        BlockState existing = level.getBlockState(flamePos);
        if (existing.is(ModBlocks.FLOO_FLAMES.get()) || existing.is(Blocks.FIRE)) {
            return true;
        }
        return existing.canBeReplaced() && existing.getFluidState().isEmpty();
    }

    /** True when flames are already burning in front of a hearth at {@code fireplacePos}. */
    public static boolean isLitAt(ServerLevel level, BlockPos fireplacePos, Direction facing) {
        return level.getBlockState(FlooFlamePlacement.flamePos(fireplacePos, facing))
                .is(ModBlocks.FLOO_FLAMES.get());
    }

    /**
     * Spend one hop's worth of fire in front of {@code fireplacePos}, putting it out when the last
     * charge goes.
     *
     * <p>Silent about a hearth with no flames: travel does not require them (a player can still use a
     * lit hearth directly), so "nothing to spend" is an ordinary outcome rather than an error.
     */
    public static void consumeChargeAt(ServerLevel level, BlockPos fireplacePos, Direction facing) {
        BlockPos flamePos = FlooFlamePlacement.flamePos(fireplacePos, facing);
        BlockState state = level.getBlockState(flamePos);
        if (!state.is(ModBlocks.FLOO_FLAMES.get())) {
            return;
        }
        int remaining = state.getValue(CHARGES) - 1;
        if (remaining < 1) {
            extinguish(level, flamePos);
            return;
        }
        level.setBlock(flamePos, state.setValue(CHARGES, remaining), 3);
        level.scheduleTick(flamePos, ModBlocks.FLOO_FLAMES.get(), ticksPerCharge());
    }

    /** Put out the flames belonging to the hearth at {@code fireplacePos}, if any. */
    public static void extinguishAt(ServerLevel level, BlockPos fireplacePos, Direction facing) {
        extinguish(level, FlooFlamePlacement.flamePos(fireplacePos, facing));
    }

    /**
     * Put the fire out, wherever the request came from, and take the hearth dark with it.
     *
     * <p>Darkening here rather than at each call site is what keeps the two blocks from disagreeing.
     * The fire in front is the clock — a hearth is lit because there are flames, so the moment they
     * go there is nothing left for {@code LIT} to be describing. Every route out of existence (a
     * spent charge, the burn-down timer, a broken hearth, a command) funnels through this one method,
     * so none of them can leave a hearth glowing over cold ash.
     */
    public static void extinguish(ServerLevel level, BlockPos flamePos) {
        BlockState flames = level.getBlockState(flamePos);
        if (!flames.is(ModBlocks.FLOO_FLAMES.get())) {
            return;
        }
        BlockPos hearthPos = FlooFlamePlacement.fireplacePos(flamePos, flames.getValue(FACING));
        // Anyone mid-departure from this hearth loses their journey, not their powder. Done here
        // rather than at each caller for the same reason darkenHearth is: every route out of
        // existence funnels through this method, and a traveller left standing in a windup over cold
        // ash would teleport out of a fire that no longer exists.
        FlooDeparture.cancelAllFrom(level, hearthPos);
        level.setBlock(flamePos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, flamePos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.6f);
        darkenHearth(level, hearthPos);
    }

    /**
     * Take a hearth out of {@code LIT}, if it is one and it is lit.
     *
     * <p>Also clears the block entity's fallback timer and tells nearby clients, so the client-side
     * lit countdown does not go on rendering a fire the server has already put out.
     */
    public static void darkenHearth(ServerLevel level, BlockPos hearthPos) {
        BlockState hearth = level.getBlockState(hearthPos);
        if (!hearth.is(ModBlocks.FLOO_FIREPLACE.get()) || !hearth.getValue(FlooFireplaceBlock.LIT)) {
            return;
        }
        level.setBlock(hearthPos, hearth.setValue(FlooFireplaceBlock.LIT, false), 3);
        if (level.getBlockEntity(hearthPos) instanceof FlooFireplaceBlockEntity be) {
            be.setLitTicksRemaining(0);
        }
        FlooBlockSyncS2CPayload.sendToNear(level, hearthPos, false, 0);
    }

    /**
     * Every route into the world gets a burn-down, not just {@link #light}.
     *
     * <p>The timer is a scheduled tick, and a scheduled tick only exists because something asked for
     * one. {@code light} does — but {@code /setblock}, a structure template and a creative-mode paste
     * do not, and flames placed that way would have burned for the rest of the world's life with no
     * clock on them at all. Asking for it here means the guarantee belongs to the block rather than to
     * whoever remembered to call the right helper.
     *
     * <p>Scheduling the same tick twice is harmless: a scheduled tick is identified by position, block
     * and trigger time, so {@code light}'s request and this one collapse into one.
     */
    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) {
            server.scheduleTick(pos, this, ticksPerCharge());
        }
    }

    /** A charge burning down on its own, for a hearth that was lit and then left. */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level,
                        @NonNull BlockPos pos, @NonNull RandomSource random) {
        int remaining = state.getValue(CHARGES) - 1;
        if (remaining < 1) {
            extinguish(level, pos);
            return;
        }
        level.setBlock(pos, state.setValue(CHARGES, remaining), 3);
        level.scheduleTick(pos, this, ticksPerCharge());
    }

    /**
     * Flames cannot outlive their hearth.
     *
     * <p>Checked on every neighbour change rather than only when the hearth is mined, because a
     * fireplace can also leave by piston, explosion or {@code /setblock}, and green fire burning in
     * front of nothing is the kind of orphan that survives forever.
     */
    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state,
                                              @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess tickAccess,
                                              @NonNull BlockPos pos,
                                              @NonNull Direction direction,
                                              @NonNull BlockPos neighbourPos,
                                              @NonNull BlockState neighbourState,
                                              @NonNull RandomSource random) {
        BlockPos hearth = FlooFlamePlacement.fireplacePos(pos, state.getValue(FACING));
        if (neighbourPos.equals(hearth) && !neighbourState.is(ModBlocks.FLOO_FIREPLACE.get())) {
            return Blocks.AIR.defaultBlockState();
        }
        // Water still puts a Floo fire out, even though vanilla's fire logic never sees this block.
        // Being outside #minecraft:fire is what makes the flames safe to stand in; it also means
        // nothing in the game was extinguishing them, so a bucket emptied into a lit hearth simply
        // failed to be waterlogged and left the fire burning inside the water. Checked on the
        // neighbour update rather than on a tick so the fire dies the moment the water arrives.
        if (!state.getFluidState().isEmpty() || neighbourState.getFluidState().is(FluidTags.WATER)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    // -- the interaction -------------------------------------------------------------------------

    /**
     * Stepping into the fire offers the network.
     *
     * <p>The same destination list the lit hearth's right-click builds, from the same manager, sent
     * on the same payload — this is a second door into one room, not a second room. Travel itself
     * still happens in {@code FlooTravelHandler} when the player picks a destination.
     *
     * <p>{@code actuallyInside} is vanilla's own distinction between an entity that overlaps this
     * block right now and one whose movement merely swept through it this tick. Only the first is a
     * player standing in a fireplace; honouring the second would open the screen on anyone who
     * sprinted past a hearth.
     *
     * <p>Nothing here touches {@code applier}, and nothing here burns anything — see the class note.
     * The one damage-adjacent thing it does is hand out {@link ModEffects#FLOO_PROTECTED}, which only
     * ever removes harm.
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier applier,
                                boolean actuallyInside) {
        if (!actuallyInside || level.isClientSide()) {
            return;
        }
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            return;
        }

        // Protection first, and outside every gate below it. It is refreshed on every tick anything
        // alive overlaps the fire — not only players, and not only players who are due another look
        // at the destination list — because the offer cooldown is about not spamming a screen and has
        // nothing to say about whether the fire is safe to stand in. Ordering it after that gate is
        // how a traveller who steps back into their own arrival hearth burns for the fifty-nine ticks
        // the screen is suppressed for.
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(ModEffects.FLOO_PROTECTED,
                    FlooProtectedEffect.REFRESH_TICKS, 0, true, false, false));
        }

        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!offerCooldownElapsed(player, level.getGameTime())) {
            return;
        }

        BlockPos hearthPos = FlooFlamePlacement.fireplacePos(pos, state.getValue(FACING));
        if (!(level.getBlockEntity(hearthPos) instanceof FlooFireplaceBlockEntity hearth)) {
            return;
        }
        if (!hearth.isRegistered()) {
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.unregistered"), true);
            markOffered(player, level.getGameTime());
            return;
        }
        if (!hearth.isEnabled()) {
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.sealed"), true);
            FlooCues.sealed(player);
            markOffered(player, level.getGameTime());
            return;
        }

        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) level);
        FlooAccess.openPicker(player, manager, hearth.getNetworkAddress(), false);
        markOffered(player, level.getGameTime());
    }

    private static boolean offerCooldownElapsed(ServerPlayer player, long now) {
        Long last = LAST_OFFER.get(player.getUUID());
        return last == null || now - last >= REOFFER_COOLDOWN_TICKS;
    }

    private static void markOffered(ServerPlayer player, long now) {
        LAST_OFFER.put(player.getUUID(), now);
    }

    /**
     * Suppress the offer for one cooldown window.
     *
     * <p>Called on arrival: a traveller materialises on the destination hearth with its fire at
     * their feet, and without this the screen would reopen in their face before they had seen where
     * they were.
     */
    public static void suppressOfferFor(UUID playerId, long now) {
        LAST_OFFER.put(playerId, now);
    }

    // -- looks -----------------------------------------------------------------------------------

    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level,
                            @NonNull BlockPos pos, @NonNull RandomSource random) {
        // Emerald column, matching the hearth's own idle flicker so the two read as one fire.
        // Density falls with the remaining charges: the model is one shape for every state (see
        // ModModelProvider), so the flicker is where a player sees a dying hearth before it goes
        // out under them.
        DustParticleOptions emerald = FlooCues.emerald(1.2f);
        int charges = state.getValue(CHARGES);
        int count = 1 + charges + random.nextInt(1 + charges);
        for (int i = 0; i < count; i++) {
            double cx = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.7;
            double cy = pos.getY() + 0.05 + random.nextDouble() * 0.9;
            double cz = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.7;
            level.addParticle(emerald, cx, cy, cz, 0, 0.05 + random.nextDouble() * 0.04, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                    pos.getY() + 0.1 + random.nextDouble() * 0.4,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                    0, 0.02, 0);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                    0.35f, 0.6f + random.nextFloat() * 0.2f, false);
        }
    }
}
