package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.creature.bond.BondState;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.bond.FollowBondedOwnerGoal;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.entity.beast.ai.ThestralGrazeGoal;
import at.koopro.wizardsandbeasts.entity.flight.FlightBeats;
import at.koopro.wizardsandbeasts.entity.flight.RiddenFlight;
import at.koopro.wizardsandbeasts.entity.flight.WingedWalker;
import at.koopro.wizardsandbeasts.entity.flight.WingedWalkerFlightGoal;
import at.koopro.wizardsandbeasts.entity.flight.WingedWalkerMoveControl;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;

/**
 * The Thestral — Order of the Phoenix: a black, skeletal winged horse with blank white eyes, gentle and clever,
 * drawn to the smell of blood, seen only by those who have witnessed death.
 *
 * <p><b>It is really there.</b> Everyone shares one entity with one hitbox, one position and one set of riders; what
 * differs is only whether a given player's client draws it ({@code ThestralRenderer}, from the synced
 * {@link #WITNESSED_DEATH_FLAG}). Canon is explicit that it is physical: Ron, Hermione and Ginny rode thestrals they
 * could not see, helped on by those who could. So anyone may touch, feed, ride or strike one; the witness state
 * decides nothing but sight.
 *
 * <p><b>It is calm.</b> It wanders, grazes ({@link ThestralGrazeGoal}), now and then opens its wings, and takes to the
 * air in short flights ({@link WingedWalkerFlightGoal}). Struck, it rears and kicks back at what struck it — a horse's
 * defence — and nothing else: no fear aura, no curse, nothing undead.
 *
 * <p><b>It is ridden</b> once it trusts you ({@link #RIDE_BOND}), and it is won over the canon way, with raw meat
 * ({@code creature_bonds/thestral.json}). Walking, running, flight and gliding use the shared winged-walker riding
 * ({@link RiddenFlight}) the Hippogriff also uses.
 */
public class ThestralEntity extends GeoEntityBase implements WingedWalker, BondableBeast {

    /**
     * The player flag for having witnessed a death: set by {@code ThestralWitnessHandler}, kept in the player's ability
     * data (saved, copied on death, synced at login, respawn and dimension change), read by the renderer and by the
     * thestral-hair wand core.
     */
    public static final String WITNESSED_DEATH_FLAG = "witnessed_death";
    public static final String ACTION_CONTROLLER = "thestral_action";
    /** Bond at which it lets a rider on. One or two offerings of meat. */
    public static final int RIDE_BOND = 15;

    private static final EntityDataAccessor<Boolean> DATA_GRAZING =
            SynchedEntityData.defineId(ThestralEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BOND_LEVEL =
            SynchedEntityData.defineId(ThestralEntity.class, EntityDataSerializers.INT);

    private final BondState bond = new BondState();
    private final FlightBeats beats = new FlightBeats();
    private boolean flying;
    private boolean wasAngry;

    public ThestralEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new WingedWalkerMoveControl<>(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GRAZING, false);
        builder.define(DATA_BOND_LEVEL, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(3, new FollowBondedOwnerGoal<>(this));
        goalSelector.addGoal(4, new WingedWalkerFlightGoal<>(this, 1200));
        goalSelector.addGoal(5, new ThestralGrazeGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // It kicks back at what struck it and at nothing else.
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ── state ─────────────────────────────────────────────────────────────────

    public boolean isGrazing() {
        return entityData.get(DATA_GRAZING);
    }

    public void setGrazing(boolean grazing) {
        entityData.set(DATA_GRAZING, grazing);
    }

    @Override
    public boolean isFlying() {
        return flying;
    }

    @Override
    public void setFlying(boolean flying) {
        this.flying = flying;
        if (!flying) {
            setNoGravity(false);
        }
    }

    @Override
    public boolean canTakeFlight() {
        return !isGrazing();
    }

    @Override
    public void flyRidden(Vec3 input, float speed) {
        travelFlying(input, speed);
    }

    // ── bond ──────────────────────────────────────────────────────────────────

    @Override
    public @NonNull BondState bondState() {
        return bond;
    }

    @Override
    public void setSyncedBondLevel(int level) {
        entityData.set(DATA_BOND_LEVEL, level);
    }

    @Override
    public int getSyncedBondLevel() {
        return entityData.get(DATA_BOND_LEVEL);
    }

    // ── tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel) || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        tickBond();
        if (isVehicle()) {
            flying = false;
        }
        beats.update(this, clip -> triggerAnim(ACTION_CONTROLLER, clip));
        boolean angry = getTarget() != null;
        if (angry && !wasAngry) {
            triggerAnim(ACTION_CONTROLLER, "rear");   // the warning before the kick
        }
        wasAngry = angry;
        if (onGround() && !isVehicle() && !angry && !isGrazing() && getRandom().nextInt(2400) == 0) {
            triggerAnim(ACTION_CONTROLLER, "spread");
        }
    }

    // ── combat ────────────────────────────────────────────────────────────────

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) {
            onBondedHurt(source);
            setGrazing(false);
            ejectPassengers();
            triggerAnim(ACTION_CONTROLLER, "hurt");
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            triggerAnim(ACTION_CONTROLLER, "attack");
        }
        return hit;
    }

    @Override
    public void die(DamageSource cause) {
        if (level() instanceof ServerLevel) {
            triggerAnim(ACTION_CONTROLLER, "death");
        }
        super.die(cause);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    // ── interaction and riding ────────────────────────────────────────────────

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult fed = offerBondFood(player, hand);
        if (fed != InteractionResult.PASS) {
            return fed;
        }
        if (!player.getItemInHand(hand).isEmpty() || player.isSecondaryUseActive() || isVehicle()
                || getTarget() != null) {
            return super.mobInteract(player, hand);
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (bondLevel() < RIDE_BOND) {
            PlayerFeedback.actionBar(player, Component.translatable("creature.wizards_and_beasts.thestral.not_yet"));
            return InteractionResult.SUCCESS;
        }
        setFlying(false);
        setGrazing(false);
        getNavigation().stop();
        player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }

    /** No thinking or wandering under a rider — the rider steers. Vanilla still runs ridden travel. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || getControllingPassenger() instanceof Player;
    }

    /** The rider sits just behind the withers, between the wings. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, 1.3 * scale, -0.05 * scale).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        RiddenFlight.tick(this, rider);
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 travel) {
        return RiddenFlight.input(this, rider);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return RiddenFlight.speed(this, rider);
    }

    @Override
    public void travel(Vec3 input) {
        if (!RiddenFlight.travel(this, input)) {
            super.travel(input);
        }
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        saveBond(output);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        loadBond(input);
    }

    // ── animation ─────────────────────────────────────────────────────────────

    private static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation.thestral." + name);
    }

    private static final RawAnimation IDLE = loop("idle");
    private static final RawAnimation WALK = loop("walk");
    private static final RawAnimation RUN = loop("run");
    private static final RawAnimation FLY = loop("fly");
    private static final RawAnimation GLIDE = loop("glide");
    private static final RawAnimation GRAZE = loop("graze");

    /** Movement first, one-shots second: GeckoLib gives the shared bones to the later controller. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ThestralEntity>("thestral_movement", 5, this::movement));
        AnimationController<ThestralEntity> action =
                new AnimationController<>(ACTION_CONTROLLER, 0, state -> PlayState.STOP);
        for (String clip : new String[]{"takeoff", "land", "flap", "spread", "rear", "hurt", "attack"}) {
            action.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.thestral." + clip));
        }
        action.triggerableAnim("death", RawAnimation.begin().thenPlayAndHold("animation.thestral.death"));
        controllers.add(action);
    }

    private PlayState movement(AnimationTest<ThestralEntity> test) {
        double dx = getX() - xo;
        double dz = getZ() - zo;
        double dy = getY() - yo;
        double horizontal = dx * dx + dz * dz;
        if (onGround() || isInWater()) {
            if (isGrazing()) {
                return test.setAndContinue(GRAZE);
            }
            if (horizontal < 1.0e-4) {
                return test.setAndContinue(IDLE);
            }
            return test.setAndContinue(horizontal > 0.03 ? RUN : WALK);
        }
        return test.setAndContinue(dy < -0.05 && horizontal > 0.01 ? GLIDE : FLY);
    }
}
