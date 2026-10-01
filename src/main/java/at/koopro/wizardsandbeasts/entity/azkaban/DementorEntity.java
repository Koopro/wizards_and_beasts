package at.koopro.wizardsandbeasts.entity.azkaban;

import at.koopro.wizardsandbeasts.azkaban.AzkabanDamageTypes;
import at.koopro.wizardsandbeasts.azkaban.structure.AzkabanStructures;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.BeastNavigation;
import at.koopro.wizardsandbeasts.entity.azkaban.goal.DementorKissGoal;
import at.koopro.wizardsandbeasts.entity.azkaban.goal.DementorPatrolGoal;
import at.koopro.wizardsandbeasts.entity.azkaban.goal.DementorPursueGoal;
import at.koopro.wizardsandbeasts.entity.azkaban.goal.FleeFromPatronusGoal;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.particle.MagicSmoke;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.patronus.PatronusDetection;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Dementor — blind, immortal, emotion-sensing guard of Azkaban.
 *
 * <p>Canon (Prisoner of Azkaban chs. 5, 10, 12, 20; Order of the Phoenix ch. 1): it glides; it cannot see but feels
 * the emotions of everything near it; cold and despair come off it in a widening ring; given the chance it lowers its
 * hood and Kisses — takes the soul. It cannot be killed, and only a Patronus drives it off.
 *
 * <p>What lives where:
 * <ul>
 *   <li><b>State</b> — one synced {@link State}. Goals set it on the server; the client picks clips from it and never
 *       queries the world to guess.</li>
 *   <li><b>Senses</b> — every {@link #SENSE_INTERVAL} ticks it picks the creature it feels most ({@link #sensed()}):
 *       players first, then people, then animals, nearest within each; anything a Patronus wards is not felt.</li>
 *   <li><b>Aura</b> — {@link DementorAura} distance bands, applied once per second per victim by the nearest
 *       Dementor only, so a swarm deepens the chill by rule rather than stacking copies of it.</li>
 *   <li><b>Kiss</b> — {@link DementorKissGoal}: a server state machine with a per-Dementor cooldown, and a claim so
 *       two Dementors cannot Kiss one victim.</li>
 *   <li><b>Patronus</b> — {@link FleeFromPatronusGoal} flees directly away from any Patronus whose ward covers it
 *       ({@link PatronusDetection}).</li>
 * </ul>
 */
public class DementorEntity extends Monster implements GeoEntity {

    /** What the Dementor is doing, synced as one byte. */
    public enum State {
        IDLE, DRAINING, KISS_WINDUP, KISS_RESOLVE, REPELLED, DISSIPATING;

        private static final State[] VALUES = values();

        public static State byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
        }
    }

    public static final int SENSE_INTERVAL = 10;
    public static final int KISS_COOLDOWN = 600;
    /** Degrees a Dementor can turn its body per tick: it wheels slowly, it does not snap round. */
    public static final float TURN_RATE = 6.0f;

    private static final String MOVE_CONTROLLER = "movement";
    private static final String STATE_CONTROLLER = "state";
    private static final String REACTION_CONTROLLER = "reaction";

    private static final RawAnimation IDLE = anim("idle", true);
    private static final RawAnimation FLOAT = anim("float", true);
    private static final RawAnimation DRAIN = anim("drain", true);
    private static final RawAnimation KISS_WINDUP = hold("kiss_windup");
    private static final RawAnimation KISS_RESOLVE = hold("kiss_resolve");
    private static final RawAnimation REPELLED = hold("repelled");
    private static final RawAnimation DISSIPATE = hold("dissipate");
    private static final RawAnimation HURT = anim("hurt", false);

    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(DementorEntity.class, EntityDataSerializers.BYTE);

    private static final String KEY_KISS_COOLDOWN = "KissCooldown";
    private static final String KEY_HOME = "Home";

    // Despair-driven spawner inside Azkaban.
    private static final int SPAWN_INTERVAL_TICKS = 600;
    private static final int DEMENTOR_CAP_BASE = 12;
    private static final int DEMENTOR_CAP_PER_PLAYER = 2;
    private static final int DEMENTOR_CAP_MAX = 24;
    /** Blocks sampled per aura pulse for frost and wilting; a sample, not a sweep of every block in range. */
    private static final int FROST_SAMPLES = 12;
    private static final int FROST_RADIUS = 8;

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    private int kissCooldown;
    private int recoilCooldown;
    private int spawnCheckCooldown;
    private @Nullable LivingEntity sensed;
    private @Nullable UUID kissVictim;
    private @Nullable BlockPos home;

    public DementorEntity(EntityType<? extends DementorEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.moveControl = new GlideMoveControl(this);
    }

    private static RawAnimation anim(String name, boolean loop) {
        String id = "animation.dementor." + name;
        return loop ? RawAnimation.begin().thenLoop(id) : RawAnimation.begin().thenPlay(id);
    }

    private static RawAnimation hold(String name) {
        return RawAnimation.begin().thenPlayAndHold("animation.dementor." + name);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) State.IDLE.ordinal());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, DementorAura.SENSE_RADIUS)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected @NonNull PathNavigation createNavigation(@NonNull Level level) {
        return BeastNavigation.flying(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FleeFromPatronusGoal(this));
        this.goalSelector.addGoal(2, new DementorKissGoal(this));
        this.goalSelector.addGoal(3, new DementorPursueGoal(this));
        this.goalSelector.addGoal(4, new DementorPatrolGoal(this));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // ── state ────────────────────────────────────────────────────────────────

    public State state() {
        return State.byId(entityData.get(DATA_STATE));
    }

    public void setState(State state) {
        if (state() != State.DISSIPATING) {
            entityData.set(DATA_STATE, (byte) state.ordinal());
        }
    }

    /** Clears {@code state} back to idle if it is still the current one; a goal never clears another's state. */
    public void clearState(State state) {
        if (state() == state) {
            setState(State.IDLE);
        }
    }

    public boolean isDissipating() {
        return state() == State.DISSIPATING;
    }

    // ── immortality ──────────────────────────────────────────────────────────

    @Override
    public boolean isInvulnerableTo(@NonNull ServerLevel level, @NonNull DamageSource source) {
        // Nothing a player carries harms it; only the admin dissipation does.
        return !source.is(AzkabanDamageTypes.DEMENTOR_DISSIPATE);
    }

    /**
     * A blow does nothing, but it is felt: the Dementor recoils and hisses, then carries on. Only the admin dissipation
     * gets through to vanilla damage handling.
     */
    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        if (source.is(AzkabanDamageTypes.DEMENTOR_DISSIPATE)) {
            return super.hurtServer(level, source, amount);
        }
        if (recoilCooldown == 0 && (source.getEntity() != null || source.getDirectEntity() != null) && !isDissipating()) {
            recoilCooldown = 10;
            triggerAnim(REACTION_CONTROLLER, "hurt");
            playSound(ModSounds.DEMENTOR_RECOIL.get(), 0.8f, 0.9f + random.nextFloat() * 0.2f);
        }
        return false;
    }

    /** Dementors are not killed; dissipation unravels one over the vanilla death second. */
    @Override
    public void die(@NonNull DamageSource cause) {
        entityData.set(DATA_STATE, (byte) State.DISSIPATING.ordinal());
        kissVictim = null;
        if (level() instanceof ServerLevel sl) {
            MagicSmoke.dread(sl, getX(), getY() + 1.5, getZ(), 12, 0.4, 0.8);
            sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 1.5, getZ(), 16, 0.6, 0.8, 0.6, 0.02);
        }
        super.die(cause);
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || isDissipating();
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.DEMENTOR_BREATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(@NonNull DamageSource source) {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ModSounds.DEMENTOR_DISSIPATE.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.7f;
    }

    @Override
    public int getHeadRotSpeed() {
        return 4;
    }

    @Override
    public int getMaxHeadYRot() {
        return 40;
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            at.koopro.wizardsandbeasts.client.entity.DementorClientEffects.tick(this);
            return;
        }
        if (!(level() instanceof ServerLevel sl) || isDissipating()) {
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        if (recoilCooldown > 0) recoilCooldown--;
        if (!ModuleManager.isEnabled(Module.AZKABAN)) {
            sensed = null;
            return;
        }
        if (kissCooldown > 0) kissCooldown--;
        if (tickCount % SENSE_INTERVAL == 0) {
            sensed = sense(sl);
        }
        if (tickCount % 20 == 0) {
            pulseAura(sl);
            frostAndWilt(sl);
        }
        if (spawnCheckCooldown-- <= 0) {
            spawnCheckCooldown = SPAWN_INTERVAL_TICKS;
            trySpawnAdditionalDementor(sl);
        }
    }

    // ── senses ───────────────────────────────────────────────────────────────

    /** The creature this Dementor feels most right now, or {@code null}. Server-side. */
    public @Nullable LivingEntity sensed() {
        LivingEntity s = sensed;
        if (s != null && (!canFeel(s) || distanceTo(s) > DementorAura.SENSE_RADIUS)) {
            sensed = null;
            return null;
        }
        return s;
    }

    /** Whether it can feel this creature: something to feed on, in this world, and not warded by a Patronus. */
    public boolean canFeel(LivingEntity entity) {
        return entity.level() == level() && DementorAura.canFeedOn(entity)
                && !PatronusDetection.isWarded(level(), entity.position());
    }

    private @Nullable LivingEntity sense(ServerLevel sl) {
        List<LivingEntity> felt = sl.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(DementorAura.SENSE_RADIUS),
                e -> distanceTo(e) <= DementorAura.SENSE_RADIUS && canFeel(e));
        return felt.stream()
                .min(Comparator.<LivingEntity>comparingInt(DementorAura::preference)
                        .thenComparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    // ── aura ─────────────────────────────────────────────────────────────────

    /**
     * Chills every creature it can feed on within {@link DementorAura#COLD_RADIUS} — but only those for which it is
     * the nearest Dementor, so each victim gets one effect per second however many Dementors surround them.
     */
    private void pulseAura(ServerLevel sl) {
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(DementorAura.COLD_RADIUS), DementorAura::canFeedOn)) {
            double mine = distanceTo(victim);
            if (mine > DementorAura.COLD_RADIUS) continue;
            List<DementorEntity> near = sl.getEntitiesOfClass(DementorEntity.class,
                    victim.getBoundingBox().inflate(DementorAura.COLD_RADIUS),
                    d -> d.isAlive() && !d.isDissipating() && d.distanceTo(victim) <= DementorAura.COLD_RADIUS);
            DementorEntity closest = near.stream()
                    .min(Comparator.<DementorEntity>comparingDouble(d -> d.distanceToSqr(victim))
                            .thenComparingInt(DementorEntity::getId))
                    .orElse(this);
            if (closest != this || PatronusDetection.isWarded(sl, victim.position())) continue;
            chill(victim, DementorAura.amplifier(mine, near.size()));
        }
    }

    /** Sets the victim's chill to exactly {@code amplifier}: stepping back lowers it, it does not linger on top. */
    static void chill(LivingEntity victim, int amplifier) {
        if (amplifier == DementorAura.NONE) return;
        MobEffectInstance current = victim.getEffect(ModEffects.DEMENTOR_CHILL);
        if (current != null && current.getAmplifier() > amplifier) {
            victim.removeEffect(ModEffects.DEMENTOR_CHILL);
        }
        victim.addEffect(new MobEffectInstance(ModEffects.DEMENTOR_CHILL, DementorAura.CHILL_TICKS, amplifier,
                false, victim instanceof Player, true));
    }

    /** Frost on open water and wilting crops near it: a handful of sampled blocks a second, nothing destroyed. */
    private void frostAndWilt(ServerLevel sl) {
        BlockPos center = blockPosition();
        for (int i = 0; i < FROST_SAMPLES; i++) {
            BlockPos pos = center.offset(random.nextInt(FROST_RADIUS * 2 + 1) - FROST_RADIUS,
                    -3 + random.nextInt(4), random.nextInt(FROST_RADIUS * 2 + 1) - FROST_RADIUS);
            BlockState state = sl.getBlockState(pos);
            if (state.is(Blocks.WATER) && state.getFluidState().isSource() && sl.canSeeSky(pos.above())
                    && sl.getBlockState(pos.above()).isAir()) {
                // Frosted ice melts back on its own once the Dementor has gone.
                sl.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
            } else if (state.getBlock() instanceof CropBlock && state.getValue(CropBlock.AGE) > 0) {
                sl.setBlockAndUpdate(pos, state.setValue(CropBlock.AGE, state.getValue(CropBlock.AGE) - 1));
            }
        }
    }

    // ── kiss ─────────────────────────────────────────────────────────────────

    public int kissCooldown() {
        return kissCooldown;
    }

    public void startKissCooldown(int ticks) {
        kissCooldown = Math.max(kissCooldown, ticks);
    }

    public void claimKiss(@Nullable LivingEntity victim) {
        kissVictim = victim == null ? null : victim.getUUID();
    }

    /** Whether any other Dementor near {@code victim} has already claimed it for a Kiss. */
    public boolean kissClaimedByAnother(LivingEntity victim) {
        UUID id = victim.getUUID();
        return !level().getEntitiesOfClass(DementorEntity.class, victim.getBoundingBox().inflate(16),
                d -> d != this && id.equals(d.kissVictim)).isEmpty();
    }

    // ── home and the Azkaban spawner ─────────────────────────────────────────

    /** Where it drifts around when it feels nothing: where it first appeared. */
    public BlockPos home() {
        return home != null ? home : blockPosition();
    }

    public boolean insideAzkaban() {
        return level() instanceof ServerLevel sl && AzkabanStructures.isInsideAzkabanArea(sl, getX(), getZ(), 16);
    }

    private void trySpawnAdditionalDementor(ServerLevel sl) {
        if (!insideAzkaban()) return;
        List<Player> insidePlayers = sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(96),
                p -> AzkabanStructures.isInsideAzkabanArea(sl, p.getX(), p.getZ(), 32));
        if (insidePlayers.isEmpty()) return;

        int cap = Math.min(DEMENTOR_CAP_BASE + insidePlayers.size() * DEMENTOR_CAP_PER_PLAYER, DEMENTOR_CAP_MAX);
        AABB searchBox = getBoundingBox().inflate(200);
        int existing = sl.getEntitiesOfClass(DementorEntity.class, searchBox, LivingEntity::isAlive).size();
        if (existing >= cap) return;

        BlockPos spawnPos = pickSpawnPoint();
        if (spawnPos == null) return;
        DementorEntity newDementor = new DementorEntity(ModEntities.DEMENTOR.get(), sl);
        newDementor.setPos(spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5);
        sl.addFreshEntity(newDementor);
    }

    private @Nullable BlockPos pickSpawnPoint() {
        BlockPos center = AzkabanStructures.cachedFortressCenter;
        if (center == null) return null;
        // Prefer the upper floors: the high-security wing.
        int dy = random.nextInt(3) == 0 ? 60 + random.nextInt(20) : random.nextInt(50);
        return center.offset(random.nextInt(14) - 7, dy, random.nextInt(14) - 7);
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(KEY_KISS_COOLDOWN, Codec.INT, kissCooldown);
        if (home != null) {
            output.store(KEY_HOME, BlockPos.CODEC, home);
        }
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        kissCooldown = input.read(KEY_KISS_COOLDOWN, Codec.INT).orElse(0);
        home = input.read(KEY_HOME, BlockPos.CODEC).orElse(null);
    }

    @Override
    public void checkDespawn() {
        // Dementors are immortal and never despawn, not even on Peaceful.
    }

    // ── GeckoLib ─────────────────────────────────────────────────────────────

    /**
     * Movement first, state second, reactions last: GeckoLib applies controllers in registration order and the last
     * one to touch a bone wins, so a Kiss or a recoil shows over the drift instead of under it.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<DementorEntity>(MOVE_CONTROLLER, 6,
                test -> test.setAndContinue(test.isMoving() ? FLOAT : IDLE)));
        controllers.add(new AnimationController<DementorEntity>(STATE_CONTROLLER, 4, this::stateClip));
        controllers.add(new AnimationController<DementorEntity>(REACTION_CONTROLLER, 0, test -> PlayState.STOP)
                .triggerableAnim("hurt", HURT));
    }

    private PlayState stateClip(AnimationTest<DementorEntity> test) {
        return switch (state()) {
            case DRAINING -> test.setAndContinue(DRAIN);
            case KISS_WINDUP -> test.setAndContinue(KISS_WINDUP);
            case KISS_RESOLVE -> test.setAndContinue(KISS_RESOLVE);
            case REPELLED -> test.setAndContinue(REPELLED);
            case DISSIPATING -> test.setAndContinue(DISSIPATE);
            case IDLE -> PlayState.STOP;
        };
    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return animCache;
    }

    /**
     * Hovering flight that turns slowly: vanilla {@link FlyingMoveControl} snaps the body up to 90° a tick toward
     * where it is going; a Dementor wheels round at {@link #TURN_RATE}, and since it glides the way it faces, its path
     * curves as it turns.
     */
    static final class GlideMoveControl extends FlyingMoveControl {
        GlideMoveControl(Mob mob) {
            super(mob, 10, true);
        }

        @Override
        public void tick() {
            float before = mob.getYRot();
            super.tick();
            float turned = rotlerp(before, mob.getYRot(), TURN_RATE);
            mob.setYRot(turned);
            mob.yBodyRot = turned;
            if (Mth.abs(Mth.wrapDegrees(turned - before)) >= TURN_RATE - 0.01f) {
                // Still wheeling round: drift, do not push off at full speed sideways.
                mob.setSpeed(mob.getSpeed() * 0.5f);
            }
        }
    }
}
