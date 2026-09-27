package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.chamber.ChamberBasilisk;
import at.koopro.wizardsandbeasts.effect.BasiliskVenomEffect;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;

/**
 * Slytherin's monster: the generic creature (its stats, traits, loot, bestiary page and the Constrict coil all still
 * come from {@code creatures/basilisk.json}), with what only a basilisk does.
 *
 * <ul>
 *   <li><b>Territory.</b> It keeps to {@link #TERRITORY} blocks of where it woke — vanilla's own saved mob home
 *       ({@code setHomeTo}): target goals drop anyone outside it and {@code MoveTowardsRestrictionGoal} brings it
 *       back, so it defends its lair and does not chase a fleeing player across the map.</li>
 *   <li><b>Mood</b>, synced as one byte: {@code COILED} when it has lain still, {@code THREATEN} (reared, jaws open)
 *       when it has a target in sight but out of reach, {@code CALM} otherwise. The transitions play {@code coil},
 *       {@code uncoil} and {@code rear}; the moods themselves are loops on the movement layer.</li>
 *   <li><b>The bite.</b> Its melee hit carries {@link BasiliskVenomEffect venom} (not vanilla poison — that trait is
 *       gone from its definition), and reaches from the head, which sits well ahead of the square hitbox.</li>
 *   <li><b>The eyes.</b> {@link #headPosition()} is where {@code DeathGazeGoal} looks from.</li>
 *   <li><b>Death.</b> Recorded against the Chamber it died in, so that Chamber never wakes another
 *       ({@link ChamberBasilisk}).</li>
 * </ul>
 */
public class BasiliskEntity extends GenericGroundBeastEntity {

    public enum Mood {
        CALM, COILED, THREATEN;

        private static final Mood[] VALUES = values();

        static Mood byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : CALM;
        }
    }

    public static final int TERRITORY = 24;
    /** Ticks lying still before it coils. */
    public static final int COIL_AFTER = 200;
    /** Where the head is, at scale 1, relative to the entity's feet: ahead along the body and up. From the rig. */
    public static final double HEAD_FORWARD = 2.1;
    public static final double HEAD_HEIGHT = 1.5;
    /** How far past its box the head reaches when it strikes, at scale 1. */
    public static final double HEAD_REACH = 1.5;
    /** Nearer than this it strikes rather than threatens. */
    private static final double THREAT_MIN = 5.0;
    private static final int BITE_DELAY = 9;

    private static final String MOOD_CONTROLLER = "basilisk_mood";
    private static final RawAnimation IDLE = loop("idle");
    private static final RawAnimation WALK = loop("walk");
    private static final RawAnimation COILED = loop("coiled");
    private static final RawAnimation THREATEN = loop("threaten");

    private static final EntityDataAccessor<Byte> DATA_MOOD =
            SynchedEntityData.defineId(BasiliskEntity.class, EntityDataSerializers.BYTE);
    private int stillTicks;
    private int pendingBite;

    public BasiliskEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // A serpent flows over a block's edge rather than hopping it.
        var step = getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) {
            step.setBaseValue(1.0);
        }
    }

    private static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation.basilisk." + name);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOOD, (byte) Mood.CALM.ordinal());
    }

    @Override
    protected void addMovementGoals() {
        super.addMovementGoals();
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        setHome(blockPosition());
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    // ── territory ────────────────────────────────────────────────────────────

    public BlockPos home() {
        return hasHome() ? getHomePosition() : blockPosition();
    }

    private void setHome(BlockPos pos) {
        setHomeTo(pos.immutable(), TERRITORY);
    }

    // ── mood ─────────────────────────────────────────────────────────────────

    public Mood mood() {
        return Mood.byId(entityData.get(DATA_MOOD));
    }

    private void setMood(Mood mood) {
        Mood was = mood();
        if (was == mood) {
            return;
        }
        entityData.set(DATA_MOOD, (byte) mood.ordinal());
        if (was == Mood.COILED) {
            triggerAnim(MOOD_CONTROLLER, "uncoil");
        } else if (mood == Mood.COILED) {
            triggerAnim(MOOD_CONTROLLER, "coil");
        }
        if (mood == Mood.THREATEN) {
            triggerAnim(MOOD_CONTROLLER, "rear");
            playSound(SoundEvents.CAT_HISS, 1.6f, 0.4f);
        }
    }

    /** What its mood should be now, from what it sees and how long it has lain still. Server-side. */
    static Mood moodFor(Mood current, boolean hasTarget, double targetDistance, boolean targetSeen, int stillTicks) {
        if (hasTarget) {
            return targetSeen && targetDistance > THREAT_MIN ? Mood.THREATEN : Mood.CALM;
        }
        if (current == Mood.COILED) {
            return stillTicks > 0 ? Mood.COILED : Mood.CALM;
        }
        return stillTicks >= COIL_AFTER ? Mood.COILED : Mood.CALM;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel) || !isAlive()) {
            return;
        }
        if (!hasHome()) {
            setHome(blockPosition());   // spawn eggs, the breeding ritual and pre-redesign saves arrive without one
        }
        if (pendingBite > 0 && --pendingBite == 0) {
            triggerDeclared("bite");
        }
        if (tickCount % 10 == 0 && ModuleManager.isEnabled(Module.CREATURES)) {
            boolean still = getDeltaMovement().horizontalDistanceSqr() < 1.0e-4 && getNavigation().isDone();
            stillTicks = still ? stillTicks + 10 : 0;
            LivingEntity target = getTarget();
            boolean hasTarget = target != null && target.isAlive();
            setMood(moodFor(mood(), hasTarget, hasTarget ? distanceTo(target) : 0,
                    hasTarget && hasLineOfSight(target), stillTicks));
        }
    }

    // ── the bite ─────────────────────────────────────────────────────────────

    @Override
    protected @NonNull AABB getAttackBoundingBox(double reach) {
        return super.getAttackBoundingBox(reach + HEAD_REACH * getScale());
    }

    @Override
    public boolean doHurtTarget(@NonNull ServerLevel level, @NonNull Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity victim && victim.isAlive()) {
            BasiliskVenomEffect.inject(victim, this);
            // The strike clip is the lunge; the jaws close and worry once it has landed.
            pendingBite = BITE_DELAY;
        }
        return hit;
    }

    // ── the eyes ─────────────────────────────────────────────────────────────

    /** Where its eyes are in the world: ahead along its body and up, scaled with it. */
    public Vec3 headPosition() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double forward = HEAD_FORWARD * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * forward, getY() + HEAD_HEIGHT * getScale(),
                getZ() + Mth.cos(yaw) * forward);
    }

    @Override
    public int getMaxHeadYRot() {
        return 50;
    }

    // ── death ────────────────────────────────────────────────────────────────

    @Override
    public void die(@NonNull DamageSource cause) {
        if (level() instanceof ServerLevel level && !isRemoved()) {
            ChamberBasilisk.slainAt(level, blockPosition());
        }
        super.die(cause);
    }

    // ── animation ────────────────────────────────────────────────────────────

    /** Idle and slither, or the mood's own loop when coiled or threatening. */
    @Override
    protected void addMovementController(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<BasiliskEntity>("basilisk_movement", 6, this::movement));
    }

    private PlayState movement(AnimationTest<BasiliskEntity> test) {
        return switch (mood()) {
            case COILED -> test.setAndContinue(COILED);
            case THREATEN -> test.setAndContinue(test.isMoving() ? WALK : THREATEN);
            case CALM -> test.setAndContinue(test.isMoving() ? WALK : IDLE);
        };
    }

    /**
     * Movement first, then the shared one-shot layer (strike, bite, gaze, hiss, taste_air, hit, death), then the mood
     * transitions last so a rear or an uncoil shows over everything.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<BasiliskEntity>(MOOD_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim("coil", RawAnimation.begin().thenPlay("animation.basilisk.coil"))
                .triggerableAnim("uncoil", RawAnimation.begin().thenPlay("animation.basilisk.uncoil"))
                .triggerableAnim("rear", RawAnimation.begin().thenPlay("animation.basilisk.rear")));
    }
}
