package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Module-gated re-implementation of vanilla {@code LeapAtTargetGoal} for the {@code leap} ability. Identical
 * pounce behaviour, but {@link #canUse()} returns {@code false} whenever {@code Module.CREATURES} is
 * disabled — so a runtime module toggle stops the leap, matching every other ability goal (the vanilla goal
 * could not self-gate). Pounces at the current attack target from 2–4 blocks while grounded.
 */
public class GatedLeapGoal extends Goal {

    private final Mob mob;
    private final float yd;
    private LivingEntity target;

    public GatedLeapGoal(Mob mob, float yd) {
        this.mob = mob;
        this.yd = yd;
        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES) || mob.hasControllingPassenger()) {
            return false;
        }
        this.target = mob.getTarget();
        if (target == null) {
            return false;
        }
        double distSqr = mob.distanceToSqr(target);
        if (distSqr < 4.0 || distSqr > 16.0) {
            return false;
        }
        return mob.onGround() && mob.getRandom().nextInt(reducedTickDelay(5)) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.onGround();
    }

    @Override
    public void start() {
        Vec3 delta = mob.getDeltaMovement();
        Vec3 toTarget = new Vec3(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ());
        if (toTarget.lengthSqr() > 1.0E-7) {
            toTarget = toTarget.normalize().scale(0.4).add(delta.scale(0.2));
        }
        mob.setDeltaMovement(toTarget.x, this.yd, toTarget.z);
    }
}
