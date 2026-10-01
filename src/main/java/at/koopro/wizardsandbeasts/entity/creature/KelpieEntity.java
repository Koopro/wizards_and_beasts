package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.flight.RiddenFlight;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;

/**
 * The Kelpie: a water demon that stands on the shore looking like a horse.
 *
 * <p>The generic creature supplies the stats, loot, bestiary page, the disguise flag and the {@code lure_disguise}
 * ability ({@code KelpieLureGoal}: let someone climb on, reveal, grip, drag them under). This class adds:
 * <ul>
 *   <li><b>Two elements.</b> Vanilla's {@link AmphibiousPathNavigation} with {@link AmphibiousMoveControl}: it walks
 *       like a horse and swims like a fish (the generic aquatic body was water-bound and could not set foot on the
 *       shore it lures from). Home is the water it appeared in; it never strays far from it.</li>
 *   <li><b>The grip</b> — server-side, bounded: {@link #beginGrip}, released by {@link #GRIP_TICKS}, by the victim
 *       striking it hard enough ({@link #BREAK_FREE_DAMAGE}), by death, or by a bridle. While it holds, the rider
 *       cannot dismount ({@link GripHold}). After a release it will not lure again for {@link #LURE_COOLDOWN}.</li>
 *   <li><b>The bridle.</b> Canon: a Placement Charm puts a bridle on it and makes it docile. The mod has no such
 *       charm, so the bridle is vanilla's saddle slot, and it can be fitted only to a <em>subdued</em> Kelpie — one
 *       Stupefied, bound by Petrificus Totalus, or beaten down to {@link #SUBDUED_HEALTH}. Bridled, it never lures or
 *       grips again, and its rider steers it on land and under water.</li>
 *   <li><b>Tells.</b> A disguised Kelpie drips — water runs off a horse that has not been in the water — and it never
 *       leaves the waterside. Someone paying attention can see it before they climb on.</li>
 * </ul>
 */
public class KelpieEntity extends GenericAquaticBeastEntity implements at.koopro.wizardsandbeasts.creature.variant.VariantHolder {

    /** Its coat, synced. Black is the base texture; the others are {@code textures/entity/kelpie/<coat>.png}. */
    public enum Coat implements at.koopro.wizardsandbeasts.creature.variant.CreatureVariant {
        BLACK(null), BLUE_BLACK("kelpie/blue_black"), GREEN_BLACK("kelpie/green_black");

        private static final Coat[] VALUES = values();
        private final @Nullable String texture;

        Coat(@Nullable String texture) {
            this.texture = texture;
        }

        public @Nullable String texture() {
            return texture;
        }

        @Override
        public @Nullable String variantTexture() {
            return texture;
        }

        public static Coat byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : BLACK;
        }
    }

    public static final int HOME_RADIUS = 14;
    public static final int GRIP_TICKS = 200;
    public static final float BREAK_FREE_DAMAGE = 8.0f;
    public static final int LURE_COOLDOWN = 1200;
    public static final float SUBDUED_HEALTH = 0.35f;
    private static final double RUN_SPEED_SQR = 0.14 * 0.14;
    private static final double RIDDEN_SWIM_SPEED = 0.06;

    private static final byte FLAG_GRIPPING = 1;
    private static final byte FLAG_THREATEN = 2;
    private static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(KelpieEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_COAT =
            SynchedEntityData.defineId(KelpieEntity.class, EntityDataSerializers.BYTE);

    private static final String ACTION = "kelpie_action";
    private static final RawAnimation IDLE = loop("idle");
    private static final RawAnimation WALK = loop("walk");
    private static final RawAnimation RUN = loop("run");
    private static final RawAnimation SWIM = loop("swim");
    private static final RawAnimation FLOAT = loop("float");
    private static final RawAnimation DIVE = loop("dive");
    private static final RawAnimation SURFACE = loop("surface");
    private static final RawAnimation LURE = loop("lure");
    private static final RawAnimation THREATEN = loop("threaten");
    private static final RawAnimation DROWN = loop("drown");

    private int gripTicks;
    private float gripDamage;
    private int lureCooldown;
    private boolean wasInWater;
    private int transitionCooldown;

    public KelpieEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new AmphibiousMoveControl(this);
    }

    private static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation.kelpie." + name);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_COAT, (byte) 0);
    }

    @Override
    protected @NonNull PathNavigation createNavigation(@NonNull Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void addMovementGoals() {
        super.addMovementGoals();
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        setCoat(at.koopro.wizardsandbeasts.creature.variant.CreatureVariants.roll("kelpie", Coat.values(), getRandom()));
        setHomeTo(blockPosition(), HOME_RADIUS);
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    // ── variant (Creature Lab) ──

    @Override
    public at.koopro.wizardsandbeasts.creature.variant.CreatureVariant variant() {
        return coat();
    }

    @Override
    public boolean applyVariant(String variantId) {
        for (Coat candidate : Coat.values()) {
            if (candidate.variantId().equals(variantId)) {
                setCoat(candidate);
                return true;
            }
        }
        return false;
    }

    public Coat coat() {
        return Coat.byId(entityData.get(DATA_COAT));
    }

    public void setCoat(Coat coat) {
        entityData.set(DATA_COAT, (byte) coat.ordinal());
    }

    private boolean flag(byte f) {
        return (entityData.get(DATA_FLAGS) & f) != 0;
    }

    private void setFlag(byte f, boolean on) {
        byte flags = entityData.get(DATA_FLAGS);
        byte next = (byte) (on ? flags | f : flags & ~f);
        if (next != flags) entityData.set(DATA_FLAGS, next);
    }

    // ── the grip ─────────────────────────────────────────────────────────────

    public boolean isGripping() {
        return flag(FLAG_GRIPPING);
    }

    public int lureCooldown() {
        return lureCooldown;
    }

    /** It has them: no dismounting until it lets go. Server-side. */
    public void beginGrip(LivingEntity victim) {
        gripTicks = GRIP_TICKS;
        gripDamage = 0;
        setFlag(FLAG_GRIPPING, true);
        setDisguised(false);
        triggerAnim(ACTION, "grab");
        playSound(ModSounds.KELPIE_SNARL.get(), 1.2f, 0.8f);
    }

    /** Lets go: throws the rider off, and will not lure anyone for a while. */
    public void releaseGrip() {
        if (!isGripping()) return;
        setFlag(FLAG_GRIPPING, false);
        gripTicks = 0;
        lureCooldown = LURE_COOLDOWN;
        ejectPassengers();
        if (isInWater()) {
            triggerAnim(ACTION, "submerge");
        }
    }

    /** Called once a tick by the lure goal while it holds someone; returns whether the grip still holds. */
    public boolean tickGrip() {
        if (!isGripping()) return false;
        if (--gripTicks <= 0 || gripDamage >= BREAK_FREE_DAMAGE || isBridled() || getPassengers().isEmpty()) {
            releaseGrip();
            return false;
        }
        return true;
    }

    /**
     * Drowning, done by the water: while its victim's head is under, their breath runs out several times faster than
     * it would — vanilla then does the drowning. Nothing is dealt through blocks or on land, and a water-breather (a
     * Gillyweed eater) is not drowned at all.
     */
    public static void drown(LivingEntity victim) {
        // The block at the eyes, read now — not the fluid flag the entity tick last cached.
        boolean headUnder = victim.level().getFluidState(net.minecraft.core.BlockPos.containing(victim.getEyePosition()))
                .is(FluidTags.WATER);
        if (!headUnder || victim.canBreatheUnderwater()
                || victim.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING)) {
            return;
        }
        victim.setAirSupply(Math.max(-20, victim.getAirSupply() - 4));
    }

    // ── the bridle ───────────────────────────────────────────────────────────

    /** Bridled: docile and ridable. Vanilla's saddle slot, saved and dropped by vanilla. */
    public boolean isBridled() {
        return isSaddled();
    }

    /** Whether a bridle can be put on it now: Stupefied, bound, or beaten down. */
    public boolean isSubdued() {
        return hasEffect(ModEffects.STUPEFY) || hasEffect(ModEffects.PETRIFICUS_TOTALUS)
                || getHealth() <= getMaxHealth() * SUBDUED_HEALTH;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.SADDLE)) {
            if (level().isClientSide()) return InteractionResult.SUCCESS;
            if (isBridled() || isGripping()) return InteractionResult.FAIL;
            if (!isSubdued()) {
                playSound(ModSounds.KELPIE_SNARL.get(), 1.0f, 1.1f);
                PlayerFeedback.actionBar(player, Component.translatable("entity.wizards_and_beasts.kelpie.will_not_be_bridled"));
                return InteractionResult.FAIL;
            }
            setItemSlot(EquipmentSlot.SADDLE, held.consumeAndReturn(1, player));
            setDropChance(EquipmentSlot.SADDLE, 2.0f);
            setDisguised(false);
            setTarget(null);
            playSound(net.minecraft.sounds.SoundEvents.HORSE_SADDLE.value(), 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        if (isBridled() && held.isEmpty() && getPassengers().isEmpty() && !player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }
        // A revealed Kelpie on its cooldown lets nobody on; the disguise mounting lives in GenericBeastEntity.
        return super.mobInteract(player, hand);
    }

    // ── riding (bridled only) ────────────────────────────────────────────────

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return isBridled() && getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 travel) {
        return RiddenFlight.input(this, rider);
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        RiddenFlight.tick(this, rider);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        if (isInWater()) {
            return (float) RIDDEN_SWIM_SPEED;
        }
        float walk = (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
        return rider.isSprinting() ? walk * 1.8f : walk;
    }

    @Override
    public void travel(Vec3 input) {
        if (getControllingPassenger() instanceof Player && isInWater()) {
            // Under a rider in water it swims where they look, as a horse never could.
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
            return;
        }
        super.travel(input);
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            clientEffects();
            return;
        }
        if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        if (!hasHome()) {
            setHomeTo(blockPosition(), HOME_RADIUS);
        }
        if (lureCooldown > 0) lureCooldown--;
        if (transitionCooldown > 0) transitionCooldown--;
        // Crossing the waterline.
        boolean inWater = isInWater();
        if (inWater != wasInWater && transitionCooldown == 0 && tickCount > 20) {
            triggerAnim(ACTION, inWater ? "submerge" : "emerge");
            level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.8, getZ(), 20, 0.6, 0.3, 0.6, 0.1);
            level.playSound(null, blockPosition(), ModSounds.KELPIE_SPLASH.get(), SoundSource.HOSTILE, 0.8f, 0.9f);
            transitionCooldown = 40;
        }
        wasInWater = inWater;
        // Back in its guise once the cooldown is over and nobody is aboard.
        if (!isDisguised() && !isBridled() && !isGripping() && lureCooldown == 0 && getPassengers().isEmpty()
                && getTarget() == null && tickCount % 20 == 0) {
            setDisguised(true);
        }
        if (tickCount % 10 == 0) {
            LivingEntity target = getTarget();
            boolean threat = !isDisguised() && target != null && target.isAlive() && distanceTo(target) > 3
                    && distanceTo(target) < 12 && getSensing().hasLineOfSight(target);
            setFlag(FLAG_THREATEN, threat);
        }
    }

    /** The tells: a disguised Kelpie on land drips; a swimming one trails bubbles. Cosmetic, per client. */
    private void clientEffects() {
        RandomSource random = getRandom();
        if (isDisguised() && !isInWater() && random.nextInt(12) == 0) {
            level().addParticle(ParticleTypes.DRIPPING_WATER, getX() + (random.nextDouble() - 0.5) * 0.8,
                    getY() + 0.8 + random.nextDouble() * 0.5, getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        }
        if (isUnderWater() && getDeltaMovement().lengthSqr() > 0.002 && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.BUBBLE, getX(), getY() + 1.0, getZ(), 0, 0.05, 0);
        }
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isGripping() && source.getEntity() != null && getPassengers().contains(source.getEntity())) {
            gripDamage += amount;   // the victim fights back
        }
        return hurt;
    }

    // ── voice ────────────────────────────────────────────────────────────────

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return isDisguised() ? ModSounds.KELPIE_WHINNY.get() : ModSounds.KELPIE_SNARL.get();
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Coat", coat().ordinal());
        output.putInt("LureCooldown", lureCooldown);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        setCoat(Coat.byId(input.getIntOr("Coat", 0)));
        lureCooldown = input.getIntOr("LureCooldown", 0);
        // A grip is never saved: a reload always finds the victim free.
    }

    // ── animation ────────────────────────────────────────────────────────────

    @Override
    protected void addMovementController(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<KelpieEntity>("kelpie_movement", 5, this::movement));
    }

    private PlayState movement(AnimationTest<KelpieEntity> test) {
        double dx = getX() - xo;
        double dy = getY() - yo;
        double dz = getZ() - zo;
        double horizontal = dx * dx + dz * dz;
        if (isInWater()) {
            if (isGripping()) return test.setAndContinue(DROWN);
            if (dy < -0.04) return test.setAndContinue(DIVE);
            if (dy > 0.04) return test.setAndContinue(SURFACE);
            return test.setAndContinue(horizontal > 1.0e-4 ? SWIM : FLOAT);
        }
        if (horizontal > 1.0e-4) {
            return test.setAndContinue(horizontal > RUN_SPEED_SQR || isGripping() ? RUN : WALK);
        }
        if (flag(FLAG_THREATEN)) return test.setAndContinue(THREATEN);
        return test.setAndContinue(isDisguised() ? LURE : IDLE);
    }

    /** Movement first, the shared one-shot layer, then the Kelpie's own beats last. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<KelpieEntity>(ACTION, 0, state -> PlayState.STOP)
                .triggerableAnim("grab", RawAnimation.begin().thenPlay("animation.kelpie.grab"))
                .triggerableAnim("emerge", RawAnimation.begin().thenPlay("animation.kelpie.emerge"))
                .triggerableAnim("submerge", RawAnimation.begin().thenPlay("animation.kelpie.submerge")));
    }

    /** While a Kelpie grips its rider, the rider cannot simply get off. Bounded by {@link #GRIP_TICKS}. */
    @EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID)
    public static final class GripHold {

        private GripHold() {}

        @SubscribeEvent
        public static void onDismount(EntityMountEvent event) {
            if (event.isDismounting() && event.getEntityBeingMounted() instanceof KelpieEntity kelpie
                    && kelpie.isGripping() && kelpie.isAlive() && !kelpie.isRemoved()
                    && event.getEntityMounting() instanceof LivingEntity rider && rider.isAlive()) {
                event.setCanceled(true);
            }
        }
    }
}
