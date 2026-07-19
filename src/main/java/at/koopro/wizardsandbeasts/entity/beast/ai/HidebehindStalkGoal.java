package at.koopro.wizardsandbeasts.entity.beast.ai;

import at.koopro.wizardsandbeasts.creature.ability.AbilitySupport;
import at.koopro.wizardsandbeasts.entity.beast.HidebehindEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Continuously repositions the Hidebehind to a point just behind its target player, recalculated on an
 * interval rather than every tick. Pure positioning goal — the actual "invisible unless faced" mechanic
 * lives client-side in {@link HidebehindEntity#isInvisibleTo}, not here.
 */
public class HidebehindStalkGoal extends Goal {

    private static final double RANGE = 16.0;
    private static final double STAND_OFF = 2.0;
    private static final int RECALC_INTERVAL = 10;

    private final HidebehindEntity mob;
    private Player target;
    private int recalcTicks;

    public HidebehindStalkGoal(HidebehindEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        List<Player> nearby = AbilitySupport.nearbyPlayers(mob, RANGE);
        if (nearby.isEmpty()) {
            return false;
        }
        target = nearby.get(0);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && mob.distanceToSqr(target) <= RANGE * RANGE;
    }

    @Override
    public void stop() {
        target = null;
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        if (recalcTicks-- <= 0) {
            recalcTicks = RECALC_INTERVAL;
            Vec3 behind = target.position().subtract(target.getLookAngle().normalize().scale(STAND_OFF));
            mob.getNavigation().moveTo(behind.x, behind.y, behind.z, 1.1);
        }
        mob.getLookControl().setLookAt(target, 30f, 30f);
    }
}
