package at.koopro.wizardsandbeasts.entity.broom;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
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
    private static final EntityDataAccessor<Integer> CURRENT_DURABILITY =
            SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.broom.idle");
    private static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("animation.broom.fly");
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
    private long lastInputGameTick = Long.MIN_VALUE;
    private int lastInputSequence = -1;

    private ItemStack broomStack = ItemStack.EMPTY;
    private boolean droppedItem;
    private transient @Nullable BroomDefinition currentDef;
    private double lastMotionDelta;

    final Map<Integer, Integer> collisionCooldowns = new HashMap<>();

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public BroomEntity(EntityType<? extends BroomEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DEFINITION_ID, FALLBACK_DEFINITION.toString());
        builder.define(BOOST_TICKS_REMAINING, 0);
        builder.define(BOOST_COOLDOWN_TICKS, 0);
        builder.define(CURRENT_DURABILITY, 120);
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity rider = getControllingPassenger();
        if (rider != null) {
            clearStaleInputIfNeeded();
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

    @Override
    protected net.minecraft.world.phys.Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        return new net.minecraft.world.phys.Vec3(0, dimensions.height() * 0.55, 0);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        writeDurabilityBackToStack();
        super.removePassenger(passenger);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
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

    BroomDefinition resolveDefinition() {
        if (currentDef != null) {
            return currentDef;
        }
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

    private void applyCrashWearFromMotionDelta() {
        double delta = Math.abs(getDeltaMovement().length() - lastMotionDelta);
        if (delta > 0.4D) {
            int damage = 1 + random.nextInt(3);
            applyDurabilityDamage(damage);
        }
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
