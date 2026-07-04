package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.ability.DiveBomb;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Aerial dive-bomb goal for {@link DiveBomb}. When the flyer is airborne and stationed above its target, it
 * folds into a stoop — accelerating down at the target — and on contact deals the configured bonus impact
 * damage plus knockback before peeling off on a cooldown. Self-gates on {@code Module.CREATURES}.
 */
public class DiveBombGoal extends Goal {

    private final GenericBeastEntity mob;
    private final DiveBomb dive;
    private int cooldown;
    private boolean diving;

    public DiveBombGoal(GenericBeastEntity mob, DiveBomb dive) {
        this.mob = mob;
        this.dive = dive;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || mob.onGround()) {
            return false;
        }
        // Only stoop from above and within range, and only now and then so it reads as a deliberate dive.
        boolean above = mob.getY() > target.getY() + 1.0;
        boolean inRange = mob.distanceToSqr(target) <= dive.range() * dive.range();
        return above && inRange && mob.getRandom().nextInt(reducedTickDelay(20)) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return diving && target != null && target.isAlive() && !mob.onGround();
    }

    @Override
    public void start() {
        this.diving = true;
    }

    @Override
    public void stop() {
        this.diving = false;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        Vec3 toTarget = new Vec3(target.getX() - mob.getX(),
                target.getY(0.5) - mob.getY(), target.getZ() - mob.getZ());
        if (toTarget.lengthSqr() > 1.0E-6) {
            // Drive the stoop directly: fast, mostly-downward acceleration toward the target.
            Vec3 thrust = toTarget.normalize().scale(0.7);
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.4).add(thrust));
        }
        double reach = mob.getBbWidth() + target.getBbWidth() + 1.0;
        if (mob.distanceToSqr(target) <= reach * reach && mob.level() instanceof ServerLevel level) {
            target.hurtServer(level, mob.damageSources().mobAttack(mob), dive.diveDamage());
            target.knockback(1.0, mob.getX() - target.getX(), mob.getZ() - target.getZ());
            cooldown = dive.cooldownTicks();
            diving = false;
        }
    }
}
