package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.ability.AbilitySupport;
import at.koopro.wizardsandbeasts.creature.ability.FlameBurst;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Fire Crab's flame-burst goal for {@link FlameBurst}: on a cooldown, when a target is within burst range,
 * sets every nearby living creature (except itself) alight + small damage and rings the crab with flame
 * particles. Server-authoritative AoE, module-gated.
 */
public class FlameBurstGoal extends Goal {

    private final GenericBeastEntity mob;
    private final FlameBurst burst;
    private int cooldown;

    public FlameBurstGoal(GenericBeastEntity mob, FlameBurst burst) {
        this.mob = mob;
        this.burst = burst;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.distanceToSqr(target) <= burst.radius() * burst.radius();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = burst.cooldownTicks();
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        for (LivingEntity victim : AbilitySupport.nearbyLiving(mob, burst.radius())) {
            victim.igniteForSeconds(burst.burnSeconds());
            victim.hurtServer(level, level.damageSources().onFire(), burst.damage());
        }
        AbilitySupport.emitAt(level, AbilitySupport.Particle.FLAME,
                mob.getX(), mob.getY() + mob.getBbHeight() * 0.4, mob.getZ(), 40, burst.radius() * 0.5, 0.05);
    }
}
