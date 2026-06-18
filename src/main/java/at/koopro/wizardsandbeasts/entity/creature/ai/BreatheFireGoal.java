package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Ranged fire-breath for creatures carrying {@code Trait.FIRE_BREATH}. On a cooldown, exhales a cone
 * of fire at the current attack target — igniting and burning every living entity within range and a
 * forward arc. Projectile-free (no entity spawned), so it is mapping-stable. Data-driven, no
 * per-creature class.
 */
public final class BreatheFireGoal extends Goal {

    private static final double RANGE = 9.0;
    private static final double CONE_DOT = 0.5; // ~60-degree half-cone
    private static final int COOLDOWN_TICKS = 60;
    private static final float DAMAGE = 4.0f;
    private static final int BURN_SECONDS = 5;

    private final GenericBeastEntity mob;
    private int cooldown;

    public BreatheFireGoal(GenericBeastEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.distanceToSqr(target) <= RANGE * RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        cooldown = COOLDOWN_TICKS / 2;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (--cooldown > 0) {
            return;
        }
        cooldown = COOLDOWN_TICKS;
        if (!mob.hasLineOfSight(target)) {
            return;
        }
        breathe();
    }

    private void breathe() {
        Vec3 look = mob.getViewVector(1.0f).normalize();
        Vec3 origin = mob.getEyePosition();
        AABB area = mob.getBoundingBox().inflate(RANGE);
        List<LivingEntity> victims = mob.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != mob && e.isAlive());
        for (LivingEntity victim : victims) {
            Vec3 toVictim = victim.position().subtract(origin);
            if (toVictim.lengthSqr() > RANGE * RANGE) {
                continue;
            }
            if (look.dot(toVictim.normalize()) < CONE_DOT) {
                continue;
            }
            if (!mob.hasLineOfSight(victim)) {
                continue;
            }
            victim.igniteForSeconds(BURN_SECONDS);
            victim.hurt(mob.damageSources().onFire(), DAMAGE);
        }
    }
}
