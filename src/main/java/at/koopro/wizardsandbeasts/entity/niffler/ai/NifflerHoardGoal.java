package at.koopro.wizardsandbeasts.entity.niffler.ai;

import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Home with the treasure. A wild Niffler with enough in its pouch goes back to its burrow — its home, vanilla's saved
 * mob home, set where it first appeared — and sits over its hoard for a while, turning things over and looking pleased
 * with itself. Nothing leaves the pouch: the hoard is the pouch, and no item entities are spawned to show it.
 *
 * <p>A bonded Niffler's home is its person; it follows them instead ({@code FollowBondedOwnerGoal} outranks this).
 */
public class NifflerHoardGoal extends Goal {

    public static final int HOARD_AT = 3;
    public static final int SIT_TICKS = 200;
    public static final int COOLDOWN = 1200;
    private static final double AT_HOME_SQR = 9.0;

    private final NifflerEntity niffler;
    private int sitTicks;
    private int cooldown;

    public NifflerHoardGoal(NifflerEntity niffler) {
        this.niffler = niffler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return !niffler.isCarried() && niffler.getOwnerUUID() == null && niffler.hasHome()
                && niffler.treasureCount() >= HOARD_AT;
    }

    @Override
    public boolean canContinueToUse() {
        return sitTicks < SIT_TICKS && !niffler.isCarried() && niffler.hasHome() && niffler.getLastHurtByMob() == null;
    }

    @Override
    public void start() {
        sitTicks = 0;
    }

    @Override
    public void stop() {
        niffler.setHoarding(false);
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        BlockPos home = niffler.getHomePosition();
        if (niffler.blockPosition().distSqr(home) > AT_HOME_SQR) {
            if (niffler.getNavigation().isDone()) {
                niffler.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
            }
            return;
        }
        niffler.getNavigation().stop();
        niffler.setHoarding(true);
        if (++sitTicks % 60 == 0) {
            niffler.admireHoard();
        }
    }
}
