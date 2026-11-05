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
 *
 * <p>It lets go once the Hidebehind is in striking reach of a back that is turned to it, so the melee goal
 * below it can strike. It used to hold MOVE across its whole 16-block range, which is also the creature's
 * follow range, so the melee goal never ran and the Hidebehind never attacked (Beast Renaissance D3). Turn
 * round and it lets go of the attack and slips behind you again.
 */
public class HidebehindStalkGoal extends Goal {

    private static final double RANGE = 16.0;
    private static final double STAND_OFF = 2.0;
    private static final int RECALC_INTERVAL = 10;
    /** Within this reach of a turned back, stalking ends and the strike begins. */
    static final double STRIKE_REACH = 3.0;
    /** A player whose look points at the Hidebehind closer than this (cosine) is facing it. */
    static final double FACING_DOT = 0.5;

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
        Player nearest = nearby.get(0);
        if (inStrikePosition(mob.position(), nearest.position(), nearest.getLookAngle(), mob.distanceToSqr(nearest))) {
            return false;
        }
        target = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && mob.distanceToSqr(target) <= RANGE * RANGE
                && !inStrikePosition(mob.position(), target.position(), target.getLookAngle(), mob.distanceToSqr(target));
    }

    /** Close enough to strike, and the player is not looking its way. */
    static boolean inStrikePosition(Vec3 hidebehind, Vec3 player, Vec3 playerLook, double distanceSqr) {
        if (distanceSqr > STRIKE_REACH * STRIKE_REACH) {
            return false;
        }
        Vec3 toHidebehind = hidebehind.subtract(player);
        if (toHidebehind.lengthSqr() < 1.0e-6) {
            return true;
        }
        return playerLook.normalize().dot(toHidebehind.normalize()) < FACING_DOT;
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
