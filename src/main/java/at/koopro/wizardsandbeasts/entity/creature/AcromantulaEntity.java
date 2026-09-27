package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;

import java.util.Comparator;

/**
 * An Acromantula: the generic creature (stats, venom via {@code status_on_hit}, the web snare, the leap, pack
 * tactics, loot and bestiary all still come from {@code creatures/acromantula.json}) with what makes it a colony of
 * thinking spiders rather than a big vanilla one.
 *
 * <ul>
 *   <li><b>Colony.</b> Its colony is its home — vanilla's saved mob home. A newcomer within {@link #COLONY_JOIN}
 *       blocks of a spider that already has one adopts that home; otherwise it founds a colony where it stands. The
 *       territory ({@link #TERRITORY}) bounds everything: target goals drop anyone outside it, it walks back when it
 *       strays, and the {@code PACK} alert (vanilla's, follow range) only reaches kin nearby — a colony answers,
 *       the map does not.</li>
 *   <li><b>Nest.</b> Now and then, near home with nothing to hunt, it spins a strand into its hollow
 *       ({@link AcromantulaWebs#webTheNest}); the silk crumbles once no spider is near to keep it.</li>
 *   <li><b>Hunting.</b> Players (hostile temperament) and prey — any animal nobody has tamed.</li>
 *   <li><b>Climbing.</b> Vanilla's wall climber: {@link WallClimberNavigation} and a synced climbing flag.</li>
 *   <li><b>Threat display</b>, synced: reared, front legs up, pincers clicking, when its quarry is in sight and not
 *       yet in reach.</li>
 *   <li><b>Speech.</b> It talks — a line to the nearest player now and then, from {@link AcromantulaSpeech}.</li>
 * </ul>
 */
public class AcromantulaEntity extends GenericGroundBeastEntity {

    public static final int TERRITORY = 20;
    public static final double COLONY_JOIN = 32.0;
    /** Natural spawning adds no spider where this many already live within 48 blocks. */
    public static final int COLONY_CAP = 8;
    public static final int NEST_INTERVAL = 400;
    public static final int SPEECH_COOLDOWN = 600;
    private static final double THREAT_MIN = 3.5;
    private static final double THREAT_MAX = 12.0;
    private static final double RUN_SPEED_SQR = 0.12 * 0.12;

    private static final byte FLAG_CLIMBING = 1;
    private static final byte FLAG_THREATEN = 2;
    private static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(AcromantulaEntity.class, EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE = loop("idle");
    private static final RawAnimation WALK = loop("walk");
    private static final RawAnimation RUN = loop("run");
    private static final RawAnimation CLIMB = loop("climb");
    private static final RawAnimation THREATEN = loop("threaten");

    private int speechCooldown;
    private int nestCooldown;
    private boolean wasThreatening;

    public AcromantulaEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    private static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation.acromantula." + name);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS, (byte) 0);
    }

    @Override
    protected @NonNull PathNavigation createNavigation(@NonNull Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    protected void addMovementGoals() {
        super.addMovementGoals();
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
        // Prey: any animal nobody has tamed. Checked on vanilla's own random interval, not every tick.
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 20, true, false,
                (target, level) -> !(target instanceof TamableAnimal tame && tame.isTame())));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        joinOrFoundColony();
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    // ── colony ───────────────────────────────────────────────────────────────

    /** Adopts the home of the nearest spider that has one within {@link #COLONY_JOIN}, or founds one here. */
    void joinOrFoundColony() {
        BlockPos colony = level().getEntitiesOfClass(AcromantulaEntity.class,
                        getBoundingBox().inflate(COLONY_JOIN), s -> s != this && s.isAlive() && s.hasHome())
                .stream().min(Comparator.comparingDouble(this::distanceToSqr))
                .map(AcromantulaEntity::getHomePosition)
                .orElse(blockPosition());
        setHomeTo(colony.immutable(), TERRITORY);
    }

    /** Whether {@code other} belongs to the same colony: an Acromantula with the same home. */
    public boolean sameColony(LivingEntity other) {
        return other instanceof AcromantulaEntity spider && spider.hasHome() && hasHome()
                && spider.getHomePosition().equals(getHomePosition());
    }

    @Override
    public boolean canAttack(@NonNull LivingEntity target) {
        // Never its own kind — not even a spider that struck it by accident in a scrum.
        return !(target instanceof AcromantulaEntity) && super.canAttack(target);
    }

    // ── climbing ─────────────────────────────────────────────────────────────

    public boolean isClimbing() {
        return (entityData.get(DATA_FLAGS) & FLAG_CLIMBING) != 0;
    }

    private void setFlag(byte flag, boolean on) {
        byte flags = entityData.get(DATA_FLAGS);
        entityData.set(DATA_FLAGS, (byte) (on ? flags | flag : flags & ~flag));
    }

    @Override
    public boolean onClimbable() {
        return isClimbing();
    }

    @Override
    public void makeStuckInBlock(@NonNull BlockState state, @NonNull Vec3 motion) {
        if (!state.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(state, motion);
        }
    }

    // ── threat display ───────────────────────────────────────────────────────

    public boolean isThreatening() {
        return (entityData.get(DATA_FLAGS) & FLAG_THREATEN) != 0;
    }

    /** Reared when its quarry is in sight but not yet in reach. */
    static boolean threatens(boolean hasTarget, double distance, boolean seen) {
        return hasTarget && seen && distance > THREAT_MIN && distance <= THREAT_MAX;
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        setFlag(FLAG_CLIMBING, horizontalCollision);
        if (!hasHome()) {
            joinOrFoundColony();   // spawn eggs and saves from before colonies arrive without one
        }
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        if (speechCooldown > 0) speechCooldown--;
        if (nestCooldown > 0) nestCooldown--;
        if (tickCount % 10 != 0) {
            return;
        }
        LivingEntity target = getTarget();
        boolean hasTarget = target != null && target.isAlive();
        boolean threat = threatens(hasTarget, hasTarget ? distanceTo(target) : 0, hasTarget && getSensing().hasLineOfSight(target));
        setFlag(FLAG_THREATEN, threat);
        if (threat && !wasThreatening) {
            triggerDeclared("hiss");
            playSound(SoundEvents.SPIDER_AMBIENT, 1.2f, 0.5f);
        }
        wasThreatening = threat;

        if (!hasTarget && nestCooldown == 0 && hasHome()
                && getHomePosition().distSqr(blockPosition()) <= (double) AcromantulaWebs.NEST_RADIUS * AcromantulaWebs.NEST_RADIUS * 4) {
            nestCooldown = NEST_INTERVAL + random.nextInt(NEST_INTERVAL);
            if (AcromantulaWebs.webTheNest(level, getHomePosition(), random)) {
                triggerDeclared("web");
            }
        }
        if (speechCooldown == 0 && tickCount % 40 == 0) {
            speak(level, hasTarget ? target : null);
        }
    }

    // ── speech ───────────────────────────────────────────────────────────────

    private void speak(ServerLevel level, @Nullable LivingEntity target) {
        Player listener = target instanceof Player player ? player
                : level.getNearestPlayer(this, AcromantulaSpeech.RANGE);
        if (listener == null || listener.isSpectator() || !getSensing().hasLineOfSight(listener)) {
            return;
        }
        AcromantulaSpeech.Situation situation = AcromantulaSpeech.situation(target == listener,
                getHealth() < getMaxHealth() * 0.5f, hasHome() && isWithinHome(listener.blockPosition()));
        Component line = AcromantulaSpeech.line(situation, random);
        PlayerFeedback.actionBar(listener, Component.translatable("entity.wizards_and_beasts.acromantula.says",
                getDisplayName(), line));
        playSound(SoundEvents.SPIDER_AMBIENT, 0.8f, 0.6f);
        speechCooldown = SPEECH_COOLDOWN + random.nextInt(SPEECH_COOLDOWN);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && source.getEntity() instanceof Player attacker && speechCooldown < SPEECH_COOLDOWN / 2) {
            speechCooldown = 0;
            speak(level, attacker);
        }
        return hurt;
    }

    // ── animation ────────────────────────────────────────────────────────────

    @Override
    protected void addMovementController(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AcromantulaEntity>("acromantula_movement", 4, this::movement));
    }

    private PlayState movement(AnimationTest<AcromantulaEntity> test) {
        if (isClimbing()) {
            return test.setAndContinue(CLIMB);
        }
        if (test.isMoving()) {
            double dx = getX() - xo;
            double dz = getZ() - zo;
            return test.setAndContinue(dx * dx + dz * dz > RUN_SPEED_SQR ? RUN : WALK);
        }
        return test.setAndContinue(isThreatening() ? THREATEN : IDLE);
    }
}
