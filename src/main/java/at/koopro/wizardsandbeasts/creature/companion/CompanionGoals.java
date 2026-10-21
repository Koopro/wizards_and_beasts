package at.koopro.wizardsandbeasts.creature.companion;

import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * The goals that carry out companion modes, added to every bondable creature by one call from its
 * {@code registerGoals} ({@link #register}). Each goal checks the creature's companion state live, so a
 * species gains them by data and a creature without a companion profile never runs them.
 *
 * <p>Priorities sit above wandering and grazing (a staying creature must not drift off to graze) and below
 * each species' own survival goals (a drowning creature still swims).
 */
@NullMarked
public final class CompanionGoals {

    /** Ticks a stay or rest must hold, with the owner away, to count as an order carried out. */
    static final int HOLD_TO_TRAIN = 1200;
    private static final double AWAY = 8.0;
    private static final double PATROL_RADIUS = 8.0;
    private static final double GUARD_RANGE = 12.0;

    private CompanionGoals() {}

    public static <T extends PathfinderMob & BondableBeast> void register(T mob, GoalSelector goals, GoalSelector targets) {
        goals.addGoal(1, new HoldGoal<>(mob));
        goals.addGoal(2, new CombatGoal<>(mob));
        goals.addGoal(4, new PatrolGoal<>(mob));
        targets.addGoal(1, new GuardTargetGoal<>(mob));
    }

    static boolean inMode(BondableBeast beast, CompanionCommand... modes) {
        if (Companions.profile(beast) == null || beast.bondOwner() == null) {
            return false;
        }
        CompanionCommand mode = Companions.memory(beast).modeOrFollow();
        for (CompanionCommand candidate : modes) {
            if (candidate == mode) {
                return true;
            }
        }
        return false;
    }

    /** Below its personality's flee line: it breaks off and goes to its person. */
    static boolean frightened(BondableBeast beast) {
        PathfinderMob mob = beast.bondMob();
        float flee = CompanionRules.fleeBelow(Companions.traits(Companions.memory(beast)));
        return flee > 0 && mob.getHealth() < mob.getMaxHealth() * flee;
    }

    /** Stay and rest: hold the spot it was told to. Resting heals. Holding while the owner is away trains it. */
    static final class HoldGoal<T extends PathfinderMob & BondableBeast> extends Goal {
        private final T mob;
        private int held;
        private boolean credited;

        HoldGoal(T mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return inMode(mob, CompanionCommand.STAY, CompanionCommand.REST);
        }

        @Override
        public void start() {
            held = 0;
            credited = false;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos anchor = Companions.memory(mob).anchor().orElse(null);
            if (anchor != null && mob.blockPosition().distSqr(anchor) > 6.25) {
                mob.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 1.0);
            } else {
                mob.getNavigation().stop();
            }
            held++;
            if (inMode(mob, CompanionCommand.REST) && held % 100 == 0 && mob.getHealth() < mob.getMaxHealth()) {
                mob.heal(1.0f);
            }
            Player owner = mob.resolveBondOwner();
            if (!credited && held >= HOLD_TO_TRAIN && owner instanceof ServerPlayer serverOwner
                    && mob.distanceToSqr(owner) > AWAY * AWAY) {
                credited = true;
                Companions.trained(mob, serverOwner);
            }
        }
    }

    /** Patrol: walks the ground around where it was posted. */
    static final class PatrolGoal<T extends PathfinderMob & BondableBeast> extends Goal {
        private final T mob;

        PatrolGoal(T mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return inMode(mob, CompanionCommand.PATROL) && mob.getTarget() == null
                    && mob.getNavigation().isDone() && mob.getRandom().nextInt(80) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return inMode(mob, CompanionCommand.PATROL) && mob.getTarget() == null && !mob.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos anchor = Companions.memory(mob).anchor().orElse(mob.blockPosition());
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double distance = mob.getRandom().nextDouble() * PATROL_RADIUS;
            mob.getNavigation().moveTo(anchor.getX() + 0.5 + Math.cos(angle) * distance, anchor.getY(),
                    anchor.getZ() + 0.5 + Math.sin(angle) * distance, 0.9);
        }
    }

    /** Melee for companions that may fight, only while they have a target and are not frightened. */
    static final class CombatGoal<T extends PathfinderMob & BondableBeast> extends MeleeAttackGoal {
        private final T beast;

        CombatGoal(T mob) {
            super(mob, 1.2, true);
            this.beast = mob;
        }

        @Override
        public boolean canUse() {
            return Companions.ableToFight(beast) && beast.bondOwner() != null && !frightened(beast) && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            if (frightened(beast)) {
                beast.setTarget(null);
                return false;
            }
            LivingEntity target = beast.getTarget();
            return target != null && Companions.mayTarget(beast, target) && super.canContinueToUse();
        }
    }

    /**
     * Who to turn on, untold: whatever hurts its owner (defend, patrol, or a guarding personality following
     * along), whatever its owner is fighting (defend), and hostile creatures near its post (patrol).
     */
    static final class GuardTargetGoal<T extends PathfinderMob & BondableBeast> extends TargetGoal {
        private final T beast;
        private @Nullable LivingEntity chosen;

        GuardTargetGoal(T mob) {
            super(mob, false);
            this.beast = mob;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!Companions.ableToFight(beast) || beast.bondOwner() == null || frightened(beast)) {
                return false;
            }
            boolean defend = inMode(beast, CompanionCommand.DEFEND);
            boolean patrol = inMode(beast, CompanionCommand.PATROL);
            boolean guards = inMode(beast, CompanionCommand.FOLLOW)
                    && Companions.traits(Companions.memory(beast)).stream().anyMatch(PersonalityTrait::guardsOwner)
                    && Companions.stage(beast).atLeast(CompanionStage.BONDED);
            if (!defend && !patrol && !guards) {
                return false;
            }
            Player owner = beast.resolveBondOwner();
            chosen = null;
            if (owner != null && beast.distanceToSqr(owner) < GUARD_RANGE * GUARD_RANGE * 4) {
                LivingEntity attacker = owner.getLastHurtByMob();
                if (attacker != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 100
                        && Companions.mayTarget(beast, attacker)) {
                    chosen = attacker;
                } else if (defend) {
                    LivingEntity owners = owner.getLastHurtMob();
                    if (owners != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 100
                            && owners.isAlive() && Companions.mayTarget(beast, owners)) {
                        chosen = owners;
                    }
                }
            }
            if (chosen == null && patrol) {
                Vec3 post = Vec3.atCenterOf(Companions.memory(beast).anchor().orElse(beast.blockPosition()));
                List<LivingEntity> near = beast.level().getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(post, post).inflate(PATROL_RADIUS + 2),
                        e -> e instanceof Enemy && e.isAlive());
                chosen = near.isEmpty() ? null : near.getFirst();
            }
            return chosen != null;
        }

        @Override
        public void start() {
            mob.setTarget(chosen);
            super.start();
        }
    }
}
