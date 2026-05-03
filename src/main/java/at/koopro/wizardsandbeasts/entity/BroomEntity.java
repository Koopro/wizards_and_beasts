package at.koopro.wizardsandbeasts.entity;

import at.koopro.wizardsandbeasts.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.SynchedEntityData;
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

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class BroomEntity extends Entity implements GeoEntity {
    private static final long INPUT_TIMEOUT_TICKS = 10L;

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

    final Map<Integer, Integer> collisionCooldowns = new HashMap<>();

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public BroomEntity(EntityType<? extends BroomEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity rider = getControllingPassenger();
        if (rider != null) {
            clearStaleInputIfNeeded();
            BroomMovement.tickMovement(this);
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
        super.removePassenger(passenger);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!(player instanceof ServerPlayer serverPlayer) || !getPassengers().isEmpty()) {
                return InteractionResult.SUCCESS;
            }

            ItemStack pickupStack = broomStack.isEmpty()
                    ? new ItemStack(ModItems.BROOM_ITEM.get())
                    : broomStack.copy();
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
                ? new ItemStack(ModItems.BROOM_ITEM.get())
                : broomStack.copy();
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            spawnAtLocation(serverLevel, toDrop);
        }
    }

    public void setBroomStack(ItemStack stack) {
        this.broomStack = stack;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        broomStack = input.read("BroomStack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
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
}
