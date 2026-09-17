package at.koopro.wizardsandbeasts.entity.broom;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import at.koopro.wizardsandbeasts.broom.BroomGeometry;
import at.koopro.wizardsandbeasts.entity.broom.handling.HandlingProfileRegistry;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;

public class BroomEntity extends Entity implements GeoEntity {
    /**
     * How long the server holds the last input it was sent before treating the rider as idle.
     *
     * <p>Twenty — two keepalives, not one. {@code BroomClientInputHandler} re-sends on an interval of ten
     * ticks, so a timeout of ten meant a single late packet read as "the rider let go of everything".
     */
    private static final long INPUT_TIMEOUT_TICKS = 20L;
    private static final Identifier FALLBACK_DEFINITION =
            Identifier.fromNamespaceAndPath("wizards_and_beasts", "cleansweep_seven");
    private static final EntityDataAccessor<String> DEFINITION_ID =
            SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> BOOST_TICKS_REMAINING =
            SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOOST_COOLDOWN_TICKS =
            SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> POLISHED =
            SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CURRENT_DURABILITY =
            SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.broom.idle");
    private static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("animation.broom.fly_forward");
    private static final RawAnimation BOOST_ANIM = RawAnimation.begin().thenLoop("animation.broom.boost");

    boolean inputForward;
    boolean inputBackward;
    boolean inputUp;
    boolean inputDown;
    boolean inputBoosting;
    float inputYaw;
    float inputPitch;

    float currentSpeed;
    float prevSpeed;
    float prevSteerYaw;
    float pitchTilt, prevPitchTilt;
    float rollTilt, prevRollTilt;
    float forwardLean, prevForwardLean;
    float verticalVelocity;
    /** Whether the boost was firing last tick, so onBoostStart fires on the edge and not every tick. */
    boolean wasBoostFiring;
    private long lastInputGameTick = Long.MIN_VALUE;
    private int lastInputSequence = -1;

    private ItemStack broomStack = ItemStack.EMPTY;
    private boolean droppedItem;
    private transient @Nullable BroomDefinition currentDef;
    /**
     * Where this broom was last tick, for {@link BroomMovement#observeMovement}. Null until the first
     * observed tick, because there is no honest delta to report before there is a previous position — and
     * seeding it to the origin would make a broom's first observed tick look like a fall from the world
     * centre, which is a severe impact.
     */
    @Nullable Vec3 observedPreviousPosition;
    /**
     * Smoothing for a broom this client is not flying.
     *
     * <p>Needed the moment the non-authoritative side stopped simulating: without a handler,
     * {@code Entity#moveOrInterpolateTo} falls back to {@code setPos}, so another player's broom would
     * teleport once per position packet instead of flying. Three steps, the same as a boat.
     */
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    /** Keeps the touchdown sound to one per landing rather than one per grounded tick. */
    private boolean announcedLanding;

    final Map<Integer, Integer> collisionCooldowns = new HashMap<>();

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public BroomEntity(EntityType<? extends BroomEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DEFINITION_ID, FALLBACK_DEFINITION.toString());
        builder.define(POLISHED, false);
        builder.define(BOOST_TICKS_REMAINING, 0);
        builder.define(BOOST_COOLDOWN_TICKS, 0);
        builder.define(CURRENT_DURABILITY, 120);
    }

    @Override
    public void tick() {
        super.tick();

        if (!onGround() && !verticalCollision) {
            announcedLanding = false;
        }

        interpolation.interpolate();

        LivingEntity rider = getControllingPassenger();
        if (rider != null) {
            if (bailOutOfFluid(rider)) {
                return;
            }
            clearStaleInputIfNeeded();
            refreshPolishedFlag();
            if (!level().isClientSide()) {
                ensureBoostInitialized();
                BroomMovement.tickBoost(this);
            }
            // Vanilla's contract for a ridden vehicle, the one AbstractBoat#tick follows: simulate only
            // where you are the authority. Player.isClientAuthoritative() is true, so for a broom with a
            // rider that is the rider's own client and nobody else — the server's position and rotation are
            // overwritten from ServerboundMoveVehiclePacket every tick regardless of what it computed.
            // Running the flight sim on both sides did not make the server authoritative; it only gave it a
            // second, wrong opinion, built from input that arrives on change or every ten ticks, and that
            // opinion still fired collisions, durability wear and crash damage the rider never flew into.
            if (isLocalInstanceAuthoritative()) {
                BroomMovement.tickMovement(this);
            } else {
                BroomMovement.observeMovement(this);
            }
            // Durability is charged for impacts and nothing else; the impact the rider's client saw reaches
            // the server through BroomImpactC2SPayload. A catch-all used to sit here billing one to three
            // points for any tick whose motion jumped by more than 0.4 — but on the server that motion is
            // observed from the rider's position packets, so one late or doubled packet at cruising speed
            // was billed as a crash, twice, and simply flying wore the broom out.
            if (!level().isClientSide() && !isRemoved()) {
                BroomImpacts.scanEntityCollisions(this, rider);
            }
        } else if (isLocalInstanceAuthoritative()) {
            // Nobody riding: with no controlling passenger there is no client to be authoritative, so this
            // is the server, and the settle is its own to simulate. A broom let go of in mid-air drifts down
            // under its own weakGravity rather than dropping like a plank; on the ground it rests, its model
            // still hovering at seat height (BroomGeometry).
            BroomDefinition def = resolveDefinition();
            Vec3 drift = getDeltaMovement();
            verticalVelocity = BroomFlightRules.applyWeakGravity((float) drift.y,
                    HandlingProfileRegistry.of(def).modifyWeakGravity(def.weakGravity(), this, def));
            setDeltaMovement(drift.x * UNRIDDEN_DRAG, verticalVelocity, drift.z * UNRIDDEN_DRAG);
            move(MoverType.SELF, getDeltaMovement());
            currentSpeed = (float) getDeltaMovement().horizontalDistance();
        } else {
            BroomMovement.observeMovement(this);
        }
        BroomMovement.updateTilt(this);
        BroomImpacts.tickCollisionCooldowns(this);
    }

    /** Horizontal drag per tick on a broom nobody is riding, so one let go of at speed coasts to a stop. */
    private static final double UNRIDDEN_DRAG = 0.9;

    /** Which {@link BroomDefinitionRegistry#generation()} {@link #currentDef} was resolved from. */
    private int currentDefGeneration = -1;

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DEFINITION_ID.equals(key)) {
            currentDef = null;
        }
    }

    public void setInput(boolean forward, boolean backward, boolean up, boolean down,
                         boolean boosting, float yaw, float pitch) {
        this.inputForward = forward;
        this.inputBackward = backward;
        this.inputUp = up;
        this.inputDown = down;
        this.inputBoosting = boosting;
        this.inputYaw = yaw;
        this.inputPitch = pitch;
    }

    public void setInputFromNetwork(boolean forward, boolean backward, boolean up, boolean down,
                                    boolean boosting, float yaw, float pitch, int sequence, long gameTick) {
        if (sequence < lastInputSequence) {
            return;
        }
        lastInputSequence = sequence;
        lastInputGameTick = gameTick;
        setInput(forward, backward, up, down, boosting, yaw, pitch);
    }

    private void clearStaleInputIfNeeded() {
        if (level().isClientSide()) return;
        if (lastInputGameTick == Long.MIN_VALUE) return;
        if (level().getGameTime() - lastInputGameTick <= INPUT_TIMEOUT_TICKS) return;
        setInput(false, false, false, false, false, getYRot(), getXRot());
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity living ? living : null;
    }

    /**
     * Whether the boost is actually firing this tick — held, charged, and off cooldown.
     *
     * <p>Three conditions, and every one of them matters: the input alone is a player mashing a key
     * they have no charge for. Exposed because the FX layer, the FOV punch and the movement step all
     * have to agree on what "boosting" means, and they were each spelling the same three-way test
     * out by hand against a package-private field.
     */
    public boolean isBoostFiring() {
        return inputBoosting && getBoostCooldownTicks() <= 0 && getBoostTicksRemaining() > 0;
    }

    /**
     * Where the rider is put: feet on this broom's position, nudged along the shaft by the definition.
     *
     * <p>Height is exactly the passenger's own vehicle attachment, because {@code Entity.positionRider}
     * subtracts that again — for a player it is {@code Avatar.DEFAULT_VEHICLE_ATTACHMENT}, 0.6. Every seat
     * derivation before 2026-09-11 left it out, so every rider sat 0.6 blocks below where the arithmetic
     * said, with the handle through their stomach. The model is lifted to meet the rider instead
     * ({@link at.koopro.wizardsandbeasts.broom.BroomSeat#modelLift()}), which is also what makes the box
     * the rider's box, from their feet up.
     *
     * <p><b>Yaw only, not pitch.</b> The rendered broom's nose angle is {@code updateTilt}'s client-side
     * visual, and this method positions the rider on the server too, so the two sides could not agree on a
     * pitched seat. The cost is bounded by the largest authored {@code z}, five centimetres.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        Vec3 seat = resolveDefinition().seat().passengerOffset();
        double height = entity.getVehicleAttachmentPoint(this).y;
        return BroomGeometry.localToWorld(seat.x, height, seat.z, getYRot());
    }

    /**
     * Turns the rider's body with the broom, as {@code AbstractHorse} does for its rider.
     *
     * <p>Without it the body follows the head only once the two are 50 degrees apart, so legs posed astride
     * the shaft point across it through every turn, and the lean {@code BroomRiderRenderHandler} applies in
     * the body's frame tips the rider off the broom's axis.
     */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger instanceof LivingEntity living) {
            living.yBodyRot = getYRot();
        }
    }

    /** The rider's box, while there is a rider. See {@link #getDimensions}. */
    private static final EntityDimensions RIDDEN_DIMENSIONS = EntityDimensions.scalable(0.8f, 1.8f);

    /**
     * A ridden broom's box is its rider's.
     *
     * <p>The entity's position is the rider's feet, so this is the box that has to stop at a ceiling before
     * a head goes into it and fit through a doorway a player fits through. The registered size — a hovering
     * broom on its own — is what an unridden one keeps. The single box this replaced was 1.5 wide and 0.6
     * tall: too wide for a one-block gap, and too short to keep a rider out of the ceiling.
     */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isVehicle() ? RIDDEN_DIMENSIONS : super.getDimensions(pose);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        // Both sides: the box becomes the rider's the moment there is one, or the client would fly a
        // different box from the one the server checks the vehicle's moves against.
        refreshDimensions();
        if (level().isClientSide()) {
            return;
        }
        level().playSound(null, blockPosition(), ModSounds.BROOM_MOUNT.get(),
                SoundSource.PLAYERS, 0.7f, 0.95f + random.nextFloat() * 0.1f);
        // The box grows to the rider's this tick and may be nudged out of whatever it now overlaps.
        // Without this, stepping onto a parked broom could cost a heart to a collision that was never a
        // crash.
        passenger.invulnerableTime = Math.max(passenger.invulnerableTime, MOUNT_GRACE_TICKS);
        passenger.fallDistance = 0.0;
    }

    /** Ticks of damage immunity granted on mounting, so seating never reads as a landing. */
    private static final int MOUNT_GRACE_TICKS = 3;

    /**
     * Services this broom where it stands.
     *
     * <p>Works on the entity's own stack, then reads the durability back out of it, because the
     * entity holds durability as synced data and the stack holds it as damage — the two have to be
     * put back in step or the repair would vanish the next time either is written.
     */
    private InteractionResult polishInPlace(Player player, ItemStack tin) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack broom = broomStack.isEmpty()
                ? new ItemStack(BroomItemRegistry.BROOM_ITEM.get())
                : broomStack;
        writeDurabilityToStack(broom);
        if (!at.koopro.wizardsandbeasts.item.broom.BroomPolishItem.serviceable(broom, level())) {
            at.koopro.wizardsandbeasts.feedback.PlayerFeedback.actionBar(player,
                    Component.translatable("broom.wizards_and_beasts.polish.not_needed"));
            return InteractionResult.FAIL;
        }
        at.koopro.wizardsandbeasts.item.broom.BroomPolishItem.polish(broom, tin, player, level());
        broomStack = broom;
        setCurrentDurability(Math.max(1, broom.getMaxDamage() - broom.getDamageValue()));
        refreshPolishedFlag();
        return InteractionResult.SUCCESS;
    }

    /**
     * Keeps the synced polish flag in step with the stack's component.
     *
     * <p>Synced rather than read from {@code broomStack} at use time because the movement step runs
     * on both sides and the stack does not: a client that did not know the broom was polished would
     * compute a different heading wander from the server's, and the broom would snap back every few
     * ticks.
     */
    private void refreshPolishedFlag() {
        if (level().isClientSide()) {
            return;
        }
        // Recomputed rather than latched: the window is an absolute tick, so it lapses on its own
        // and nothing would otherwise tell a broom in flight that its polish just ran out.
        boolean polished = !broomStack.isEmpty()
                && at.koopro.wizardsandbeasts.item.broom.BroomPolish.isPolished(broomStack, level());
        if (polished != isPolished()) {
            entityData.set(POLISHED, polished);
        }
    }

    /** True while this broom's handle is freshly polished — halves its heading wander. */
    public boolean isPolished() {
        return entityData.get(POLISHED);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        writeDurabilityBackToStack();
        if (!level().isClientSide()) {
            level().playSound(null, blockPosition(), ModSounds.BROOM_DISMOUNT.get(),
                    SoundSource.PLAYERS, 0.6f, 1.0f);
        }
        super.removePassenger(passenger);
        refreshDimensions();
        // Exit velocity: a rider stepping off keeps whatever the broom was doing, so dismounting
        // at speed used to fling them forward and dismounting in a dive dropped them still
        // falling at flight speed. Their own momentum is theirs; the broom's is not.
        if (!level().isClientSide()) {
            passenger.setDeltaMovement(Vec3.ZERO);
            passenger.hurtMarked = true;
            passenger.fallDistance = 0.0;
        }
        // Reset the per-rider input-sequence baseline. Without this, the previous rider's last accepted
        // sequence -- or a crafted Integer.MAX_VALUE -- would reject every input from the next rider,
        // permanently jamming the broom. The monotonic guard only needs to hold within one rider's session.
        lastInputSequence = -1;
        lastInputGameTick = Long.MIN_VALUE;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // Servicing a broom standing in the world, before any of the mount handling: a player who has
        // just landed has the broom under them rather than in a slot, and making them pick it up to
        // oil it is friction with nothing on the other side of it.
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof at.koopro.wizardsandbeasts.item.broom.BroomPolishItem) {
            return polishInPlace(player, held);
        }

        if (player.isShiftKeyDown()) {
            if (!(player instanceof ServerPlayer serverPlayer) || !getPassengers().isEmpty()) {
                return InteractionResult.SUCCESS;
            }

            ItemStack pickupStack = broomStack.isEmpty()
                    ? new ItemStack(BroomItemRegistry.BROOM_ITEM.get())
                    : broomStack.copy();
            writeDurabilityToStack(pickupStack);
            if (!serverPlayer.getInventory().add(pickupStack)) {
                serverPlayer.drop(pickupStack, false);
            }
            discard();
            return InteractionResult.SUCCESS;
        }

        if (!level().isClientSide() && getControllingPassenger() == null && !player.isPassenger()) {
            // The other half of the broom licence gate; BroomItem.use covers the spawn-and-ride path.
            if (at.koopro.wizardsandbeasts.ministry.licence.BroomLicence.refuse(player, resolveDefinition())) {
                return InteractionResult.FAIL;
            }
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    /**
     * Not solid. A ridden broom's box is its rider's, and a solid one would be a pillar other players stand
     * on; a parked one would be a step. Brooms meeting each other are handled by
     * {@code BroomImpacts.scanEntityCollisions}, which never needed the solidity.
     */
    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    void dropBroomItem() {
        if (droppedItem) return;
        droppedItem = true;
        ItemStack toDrop = broomStack.isEmpty()
                ? new ItemStack(BroomItemRegistry.BROOM_ITEM.get())
                : broomStack.copy();
        writeDurabilityToStack(toDrop);
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            spawnAtLocation(serverLevel, toDrop);
        }
    }

    public void setBroomStack(ItemStack stack) {
        this.broomStack = stack;
        Identifier definition = stack.getOrDefault(ModDataComponents.BROOM_DEFINITION.get(), FALLBACK_DEFINITION);
        setDefinitionId(definition);
        if (stack.isDamageableItem()) {
            setCurrentDurability(Math.max(1, stack.getMaxDamage() - stack.getDamageValue()));
        } else {
            setCurrentDurability(resolveDefinition().durability());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        broomStack = input.read("BroomStack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if (!broomStack.isEmpty()) {
            setBroomStack(broomStack.copy());
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (!broomStack.isEmpty()) {
            output.store("BroomStack", ItemStack.CODEC, broomStack);
        }
    }

    public float getPitchTilt() {
        return pitchTilt;
    }

    public float getPrevPitchTilt() {
        return prevPitchTilt;
    }

    public float getRollTilt() {
        return rollTilt;
    }

    public float getPrevRollTilt() {
        return prevRollTilt;
    }

    public float getForwardLean() {
        return forwardLean;
    }

    public float getPrevForwardLean() {
        return prevForwardLean;
    }

    /**
     * Vertical velocity, exposed for {@code BroomHandlingProfile#onBoostStart}.
     *
     * <p>Public because the profiles live in a sub-package, and a sub-package is a different package
     * to Java — the movement fields they would otherwise reach for are package-private. This is the
     * only one a profile mutates: everything else it needs arrives as a method argument, which keeps
     * the widened surface to one value rather than the whole input block.
     */
    public float getVerticalVelocity() {
        return verticalVelocity;
    }

    public void setVerticalVelocity(float value) {
        this.verticalVelocity = value;
    }

    /**
     * True when the rider is actively turning, rather than holding a heading.
     *
     * <p>The threshold matters: mouse input is never exactly zero, so comparing against zero would
     * report "steering" every tick and a heading lock would never engage.
     */
    public boolean isSteering() {
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(inputYaw - getYRot())) > STEERING_DEADZONE_DEGREES;
    }

    /** Degrees of yaw difference below which the rider counts as holding a heading, not turning. */
    private static final float STEERING_DEADZONE_DEGREES = 2.0f;

    /**
     * World position of the bristle tips, where a slipstream is shed from.
     *
     * <p>The rig's {@code fx_tail} anchor at render scale, on the lifted model — see
     * {@link BroomGeometry#tailOffset}. Read from the geometry rather than sampled from the bone: GeckoLib
     * bone transforms exist only inside a client render pass, and crash debris wants this on the server.
     *
     * <p>It used to rotate {@code +z} straight through {@code Vec3.yRot(-yaw)}, which maps local {@code +z}
     * to <em>forward</em>, so the trail was seeded 2.5 blocks in front of the rider and flown through.
     */
    public Vec3 tailPosition() {
        return position().add(BroomGeometry.tailOffset(resolveDefinition().seat().modelLift(), getYRot()));
    }

    public float getCurrentSpeed() {
        return currentSpeed;
    }

    public boolean isBoosting() {
        return inputBoosting;
    }

    /**
     * One controller, three clips.
     *
     * <p>There were three controllers, one per clip, each stopping when its condition failed. All three
     * animate {@code bristles} and {@code shaft}, so through every hand-over two of them were fading in and
     * out on the same bones at once — and a held sprint key with no charge played the boost clip, because
     * the test read the key rather than {@link #isBoostFiring()}. A single controller blends from one clip to
     * the next, which is what a hand-over is.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<BroomEntity>("flight", 5, this::flightController));
    }

    private PlayState flightController(AnimationTest<BroomEntity> test) {
        if (Math.abs(currentSpeed) < 0.01f) {
            return test.setAndContinue(IDLE_ANIM);
        }
        return test.setAndContinue(isBoostFiring() ? BOOST_ANIM : FLY_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    public String getDefinitionId() {
        return entityData.get(DEFINITION_ID);
    }

    public void setDefinitionId(Identifier id) {
        entityData.set(DEFINITION_ID, id.toString());
        currentDef = null;
    }

    public int getBoostTicksRemaining() {
        return entityData.get(BOOST_TICKS_REMAINING);
    }

    public void setBoostTicksRemaining(int ticks) {
        entityData.set(BOOST_TICKS_REMAINING, Math.max(0, ticks));
    }

    public int getBoostCooldownTicks() {
        return entityData.get(BOOST_COOLDOWN_TICKS);
    }

    public void setBoostCooldownTicks(int ticks) {
        entityData.set(BOOST_COOLDOWN_TICKS, Math.max(0, ticks));
    }

    public int getCurrentDurability() {
        return entityData.get(CURRENT_DURABILITY);
    }

    public void setCurrentDurability(int durability) {
        entityData.set(CURRENT_DURABILITY, Math.max(0, durability));
    }

    /**
     * This broom's boosted top speed — the denominator any speed-scaled effect should use. Public
     * because the client camera and flight FX need it and have no business resolving definitions
     * themselves.
     */
    public float getTopSpeed() {
        BroomDefinition def = resolveDefinition();
        return def.maxSpeed() * def.boostMultiplier();
    }

    /** This broom's unboosted top speed. */
    public float getCruiseSpeed() {
        return resolveDefinition().maxSpeed();
    }

    public BroomDefinition resolveDefinition() {
        // The cache is only good for as long as the table it came from. A /reload swaps the table
        // without touching any entity's DEFINITION_ID, so the id-change hook never fires and a
        // broom in the world would fly its pre-reload values until it unloaded.
        int generation = BroomDefinitionRegistry.generation();
        if (currentDef != null && currentDefGeneration == generation) {
            return currentDef;
        }
        currentDef = null;
        currentDefGeneration = generation;
        Identifier id = Identifier.tryParse(getDefinitionId());
        if (id != null) {
            BroomDefinition resolved = BroomDefinitionRegistry.get(id);
            if (resolved != null) {
                currentDef = resolved;
                return resolved;
            }
        }
        currentDef = BroomDefinitionRegistry.getFallback();
        return currentDef;
    }

    void applyDurabilityDamage(int amount) {
        if (amount <= 0 || level().isClientSide() || isRemoved()) {
            return;
        }
        int newValue = Math.max(0, getCurrentDurability() - amount);
        setCurrentDurability(newValue);
        if (newValue <= 0) {
            breakBroom();
        }
    }

    /** Server game tick the last impact report from this broom's rider was acted on. */
    private long lastImpactReportTick = Long.MIN_VALUE;

    /**
     * Tells the server about a collision only this client could see. Client-side; see
     * {@link at.koopro.wizardsandbeasts.network.broom.BroomImpactC2SPayload}.
     *
     * <p>Silent below the minor threshold, and silent for a landing already announced, so ordinary flight
     * along the ground — a vertical collision every single tick — does not become a packet every tick.
     */
    void reportImpact(boolean gentle, float severity) {
        if (gentle ? announcedLanding : severity < BroomTuning.MINOR_IMPACT_THRESHOLD) {
            return;
        }
        at.koopro.wizardsandbeasts.util.ClientClassBridge.callStatic(
                "at.koopro.wizardsandbeasts.client.broom.BroomImpactClient", "report",
                new Class<?>[] {BroomEntity.class, boolean.class, float.class},
                new Object[] {this, gentle, severity});
    }

    /**
     * Applies an impact the rider's client reported. Server-side; validated by the payload before it gets
     * here, rate-limited here because the limit is per broom and the clock lives on the level.
     */
    public void acceptImpactReport(boolean gentle, float severity) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (now - lastImpactReportTick
                < at.koopro.wizardsandbeasts.network.broom.BroomImpactC2SPayload.IMPACT_REPORT_INTERVAL_TICKS) {
            return;
        }
        lastImpactReportTick = now;
        BroomImpacts.apply(this, gentle, net.minecraft.util.Mth.clamp(severity, 0f,
                at.koopro.wizardsandbeasts.network.broom.BroomImpactC2SPayload.MAX_SEVERITY));
    }

    private void ensureBoostInitialized() {
        if (getBoostTicksRemaining() <= 0 && getBoostCooldownTicks() <= 0) {
            setBoostTicksRemaining(resolveDefinition().boostDurationTicks());
        }
    }

    /**
     * A controlled touchdown: bleed off the remaining speed and say so quietly. No damage, no
     * durability cost, no knock — see {@link BroomFlightRules#isGentleLanding}.
     */
    void onGentleLanding() {
        currentSpeed *= 0.5f;
        verticalVelocity = 0f;
        setDeltaMovement(getDeltaMovement().multiply(0.6, 0.0, 0.6));
        // Latched on both sides. It used to be set only on the server, which was enough while the server
        // was the one noticing landings; now the client is, and it uses this same flag to keep a touchdown
        // to one report rather than one per grounded tick. The sound stays server-side.
        boolean firstTouch = !announcedLanding;
        announcedLanding = true;
        if (firstTouch && !level().isClientSide()) {
            level().playSound(null, blockPosition(), SoundEvents.WOOL_STEP,
                    SoundSource.PLAYERS, 0.5f, 1.1f);
        }
    }

    /**
     * Ends the flight when the broom meets water or lava, rather than letting a rider fly a
     * submerged broom around the bottom of a lake or hold station inside a lava lake taking ticks
     * of fire damage with no way to steer out.
     *
     * <p>The broom is <b>not</b> destroyed and the rider is <b>not</b> hurt by this: it puts them
     * down at the best nearby spot the dismount scan can find and hands the broom back as an item,
     * which is recoverable in a way that sinking into lava on a broom that keeps flying is not.
     * Whatever the fluid itself does to them still applies — this is not a rescue, only a refusal
     * to keep flying somewhere a broom cannot fly.
     *
     * @return true when the flight ended and the caller should stop ticking movement
     */
    private boolean bailOutOfFluid(LivingEntity rider) {
        if (level().isClientSide()) {
            return false;
        }
        boolean lava = isInLava();
        if (!lava && !isInWater()) {
            return false;
        }
        if (rider instanceof Player player) {
            PlayerFeedback.actionBar(player, Component.translatable(
                    lava ? "broom.wizards_and_beasts.bail.lava" : "broom.wizards_and_beasts.bail.water"));
        }
        rider.stopRiding();
        dropBroomItem();
        discard();
        return true;
    }

    /**
     * Where a dismounting rider is put down.
     *
     * <p>Vanilla's default hands back the vehicle's own position, which for a flying entity means
     * dismounting inside a wall leaves you inside the wall and dismounting at altitude leaves you
     * exactly where you were, falling. This walks {@link BroomFlightRules#dismountOffsets()} —
     * downward first, then outward, up only as a last resort — and takes the first position with
     * room for the rider.
     *
     * <p>If nothing qualifies it falls back to the broom's position, the old behaviour: a rider
     * boxed in on all sides has to end up somewhere, and refusing to dismount would be worse.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos origin = blockPosition();
        for (int[] offset : BroomFlightRules.dismountOffsets()) {
            BlockPos candidate = origin.offset(offset[0], offset[1], offset[2]);
            Vec3 spot = Vec3.atBottomCenterOf(candidate);
            if (level().noCollision(passenger, passenger.getDimensions(passenger.getPose())
                    .makeBoundingBox(spot))) {
                return spot;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    private void breakBroom() {
        if (level().isClientSide()) {
            return;
        }
        level().playSound(null, blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
        if (getControllingPassenger() != null) {
            getControllingPassenger().stopRiding();
        }
        ItemStack broken = broomStack.isEmpty() ? new ItemStack(BroomItemRegistry.BROOM_ITEM.get()) : broomStack.copy();
        if (broken.isDamageableItem()) {
            broken.setDamageValue(broken.getMaxDamage());
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            spawnAtLocation(serverLevel, broken);
        }
        discard();
    }

    private void writeDurabilityBackToStack() {
        if (!broomStack.isEmpty()) {
            writeDurabilityToStack(broomStack);
        }
    }

    private void writeDurabilityToStack(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return;
        }
        int durability = Math.max(0, getCurrentDurability());
        int max = stack.getMaxDamage();
        stack.setDamageValue(Math.max(0, Math.min(max, max - durability)));
    }
}
