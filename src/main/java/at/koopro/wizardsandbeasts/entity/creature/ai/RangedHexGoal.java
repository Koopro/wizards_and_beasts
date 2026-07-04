package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.ability.RangedHex;
import at.koopro.wizardsandbeasts.entity.creature.BeastHexProjectile;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Ranged "hex" goal for {@link RangedHex}. On a cooldown, when the creature has a living target within range
 * and line of sight, it launches a real {@link BeastHexProjectile} — a travelling, dodgeable bolt carrying
 * the configured damage + optional effect + particle trail. Self-gates on {@code Module.CREATURES}.
 */
public class RangedHexGoal extends Goal {

    private final GenericBeastEntity mob;
    private final RangedHex hex;
    private int cooldown;

    public RangedHexGoal(GenericBeastEntity mob, RangedHex hex) {
        this.mob = mob;
        this.hex = hex;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive()
                && mob.distanceToSqr(target) <= hex.range() * hex.range()
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
        cooldown = hex.cooldownTicks();
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        BeastHexProjectile bolt = new BeastHexProjectile(level, mob, hex.damage(),
                hex.effect().orElse(null), hex.particle());
        double dx = target.getX() - mob.getX();
        double dy = target.getY(0.5) - bolt.getY();
        double dz = target.getZ() - mob.getZ();
        bolt.shoot(dx, dy, dz, 1.4f, 4.0f);
        level.addFreshEntity(bolt);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                net.minecraft.sounds.SoundEvents.WITCH_THROW, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.2f);
    }
}
