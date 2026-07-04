package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.ability.AbilitySupport;
import at.koopro.wizardsandbeasts.creature.ability.WebSnare;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ranged snare goal for {@link WebSnare}: on a cooldown, roots the creature's target in place with heavy
 * Slowness + Mining Fatigue and traces cobweb particles. Hitscan, server-authoritative, module-gated.
 */
public class WebSnareGoal extends Goal {

    private final GenericBeastEntity mob;
    private final WebSnare snare;
    private int cooldown;

    public WebSnareGoal(GenericBeastEntity mob, WebSnare snare) {
        this.mob = mob;
        this.snare = snare;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive()
                && mob.distanceToSqr(target) <= snare.range() * snare.range()
                && mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = snare.cooldownTicks();
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, snare.durationTicks(), snare.slowAmplifier()));
        target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, snare.durationTicks(), 1));
        if (mob.level() instanceof ServerLevel level) {
            Vec3 from = mob.getEyePosition();
            Vec3 to = target.getEyePosition();
            int steps = (int) Math.max(4, from.distanceTo(to));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                AbilitySupport.emitAt(level, AbilitySupport.Particle.CRIT,
                        from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.z + (to.z - from.z) * t,
                        1, 0.05, 0.0);
            }
        }
    }
}
