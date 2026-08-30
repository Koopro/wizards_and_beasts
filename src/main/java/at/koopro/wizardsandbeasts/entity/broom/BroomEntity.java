package at.koopro.wizardsandbeasts.entity.broom;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
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
    private static final long INPUT_TIMEOUT_TICKS = 10L;
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
    private double lastMotionDelta;
    /** Set by {@link #onGentleLanding()} and cleared each tick, so wear paths can agree on it. */
    private boolean landedGentlyThisTick;
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

        landedGentlyThisTick = false;
        if (!onGround() && !verticalCollision) {
            announcedLanding = false;
        }

        LivingEntity rider = getControllingPassenger();
        if (rider != null) {
            if (bailOutOfFluid(rider)) {
                return;
            }
            clearStaleInputIfNeeded();
            refreshPolishedFlag();
            ensureBoostInitialized();
            BroomMovement.tickMovement(this);
            applyCrashWearFromMotionDelta();
            if (!level().isClientSide() && !isRemoved()) {
                BroomImpacts.scanEntityCollisions(this, rider);
            }
        } else {
            verticalVelocity = (float) getDeltaMovement().y;
            setDeltaMovement(getDeltaMovement().scale(0.95).add(0, -0.04, 0));
            move(MoverType.SELF, getDeltaMovement());
        }
        BroomMovement.updateTilt(this);
        BroomImpacts.tickCollisionCooldowns(this);
        lastMotionDelta = getDeltaMovement().length();
    }

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

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity living ? living : null;
    }

    /**
     * Where the rider straddles the shaft, read from the definition.
     *
     * <p>The derivation — shaft centre 0.25 blocks up, humanoid hip pivot 0.75 blocks up, so the
     * seat is -0.50 — lives on {@link at.koopro.wizardsandbeasts.broom.BroomSeat}, which is also
     * where the default comes from. What used to be here was {@code dimensions.height() * 0.55}: a
     * hitbox height, which says nothing about where a model draws its shaft, and which put the
     * rider's hip 0.83 blocks above the handle.
     *
     * <p>The horizontal components are rotated into the broom's own frame, so a definition writing
     * {@code [0, -0.4375, 0.05]} means five centimetres toward the bristles rather than five
     * centimetres toward world south. Vanilla's default attachment does the same thing through
     * {@code EntityAttachments.getClamped}; overriding the method opts out of that, so the rotation
     * has to be done here or a non-zero offset would swing around the broom as it turned.
     *
     * <p><b>Yaw only, deliberately — not pitch.</b> The rendered broom does not pitch with
     * {@code getXRot()}: its nose angle comes from {@code BroomMovement.updateTilt}, which is roughly
     * {@code -0.4} times the entity pitch and clamped to 35 degrees. Rotating the seat by the full
     * entity pitch would swing the rider further than the mesh they are sitting on. Using the visual
     * tilt instead is worse still — those are client-side render values, and this method positions
     * the rider on the server too, so the two sides would disagree about where a passenger is. Yaw is
     * applied to the mesh one-for-one, which is why it is safe. The cost of leaving pitch out is
     * bounded by the largest authored {@code z}, five centimetres, which at any real flight angle
     * moves the seat by less than a pixel.
     *
     * <p>Sampling a {@code rider_attach} bone instead was considered and rejected for the same
     * reason: GeckoLib bone transforms exist only inside a client render pass, and
     * {@code positionRider} needs an answer on the server. The JSON offset is the only form of this
     * value both sides can agree on — and {@code BroomSeatParityTest} ties it back to the geometry,
     * which is what a bone would have given.
     */
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

    @Override
    protected net.minecraft.world.phys.Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        Vec3 offset = resolveDefinition().seat().passengerOffset();
        if (offset.x == 0.0 && offset.z == 0.0) {
            return offset;
        }
        return offset.yRot(-getYRot() * net.minecraft.util.Mth.DEG_TO_RAD);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (level().isClientSide()) {
            return;
        }
        level().playSound(null, blockPosition(), ModSounds.BROOM_MOUNT.get(),
                SoundSource.PLAYERS, 0.7f, 0.95f + random.nextFloat() * 0.1f);
        // A rider is seated below the broom's origin, so the tick they mount they are briefly
        // overlapping whatever the broom was resting on. Without this, stepping onto a parked broom
        // could cost a heart to a collision that was never a crash.
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

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return true;
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
     * Vertical velocity, exposed for {@code BroomHandlingProfile#afterVelocityComputed}.
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
     * <p>Taken from the rig's {@code fx_tail} anchor — {@code [0, 4, 40]} in model units, so 0.25
     * blocks up and 2.5 blocks back — and rotated into the broom's frame the same way the seat is.
     * Read from the geometry rather than sampled from the bone: GeckoLib bone transforms exist only
     * inside a client render pass, and this is wanted by anything that wants to put an effect at the
     * back of a broom.
     */
    public Vec3 tailPosition() {
        return position().add(TAIL_OFFSET.yRot(-getYRot() * net.minecraft.util.Mth.DEG_TO_RAD));
    }

    /** {@code fx_tail} in blocks: 4/16 up, 40/16 back along the broom's own axis. */
    private static final Vec3 TAIL_OFFSET = new Vec3(0.0, 4.0 / 16.0, 40.0 / 16.0);

    public float getCurrentSpeed() {
        return currentSpeed;
    }

    public boolean isBoosting() {
        return inputBoosting;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<BroomEntity>("idle", 5, this::idleController));
        controllers.add(new AnimationController<BroomEntity>("fly", 5, this::flyController));
        controllers.add(new AnimationController<BroomEntity>("boost", 3, this::boostController));
    }

    private PlayState idleController(AnimationTest<BroomEntity> test) {
        if (Math.abs(currentSpeed) < 0.01f) {
            return test.setAndContinue(IDLE_ANIM);
        }
        return PlayState.STOP;
    }

    private PlayState flyController(AnimationTest<BroomEntity> test) {
        if (Math.abs(currentSpeed) >= 0.01f && !inputBoosting) {
            return test.setAndContinue(FLY_ANIM);
        }
        return PlayState.STOP;
    }

    private PlayState boostController(AnimationTest<BroomEntity> test) {
        if (Math.abs(currentSpeed) >= 0.01f && inputBoosting) {
            return test.setAndContinue(BOOST_ANIM);
        }
        return PlayState.STOP;
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

    private void ensureBoostInitialized() {
        if (getBoostTicksRemaining() <= 0 && getBoostCooldownTicks() <= 0) {
            setBoostTicksRemaining(resolveDefinition().boostDurationTicks());
        }
    }

    /**
     * Wear from a sudden change in motion — the catch-all for impacts the collision path did not
     * classify.
     *
     * <p>Skipped for a tick that was judged a gentle landing. Coming to a stop on the ground is a
     * large motion delta by definition, so without this check every touchdown was charged one to
     * three durability here regardless of what the landing rule decided — the exemption would have
     * been exempting nothing.
     */
    private void applyCrashWearFromMotionDelta() {
        if (landedGentlyThisTick) {
            return;
        }
        double delta = Math.abs(getDeltaMovement().length() - lastMotionDelta);
        if (delta > 0.4D) {
            int damage = 1 + random.nextInt(3);
            applyDurabilityDamage(damage);
        }
    }

    /**
     * A controlled touchdown: bleed off the remaining speed and say so quietly. No damage, no
     * durability cost, no knock — see {@link BroomFlightRules#isGentleLanding}.
     */
    void onGentleLanding() {
        landedGentlyThisTick = true;
        currentSpeed *= 0.5f;
        verticalVelocity = 0f;
        setDeltaMovement(getDeltaMovement().multiply(0.6, 0.0, 0.6));
        if (!level().isClientSide() && !announcedLanding) {
            announcedLanding = true;
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
