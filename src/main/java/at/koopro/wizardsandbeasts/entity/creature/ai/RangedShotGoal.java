package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.ability.RangedShot;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Looses a {@link RangedShot} at the creature's target: when the target is inside the shot's band and in sight, the
 * creature shows the shot (its attack clip), waits out the windup, and releases. It claims only the creature's
 * gaze, so it runs beside whatever is moving the body: a centaur shoots while it circles.
 */
public final class RangedShotGoal extends Goal {

    private final GenericBeastEntity mob;
    private final RangedShot shot;
    private int cooldown;
    /** Ticks left until release; negative while no shot is being shown. */
    private int windup = -1;

    public RangedShotGoal(GenericBeastEntity mob, RangedShot shot) {
        this.mob = mob;
        this.shot = shot;
        // The first shot is not instant: a creature that has just noticed someone takes a moment to draw.
        this.cooldown = 20;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && shot.inBand(mob.distanceTo(target))
                && mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        // A shot already shown is finished even if the target steps out of the band: it was aimed, it flies.
        LivingEntity target = mob.getTarget();
        return windup >= 0 ? target != null && target.isAlive() : canUse();
    }

    @Override
    public void stop() {
        windup = -1;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (windup < 0) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            windup = shot.windupTicks();
            mob.swingAtNothing();   // the attack clip is the draw or the throw
        }
        if (windup-- > 0) {
            return;
        }
        windup = -1;
        cooldown = shot.cooldownTicks();
        if (mob.level() instanceof ServerLevel level) {
            shot.release(level, mob, target);
        }
    }
}
