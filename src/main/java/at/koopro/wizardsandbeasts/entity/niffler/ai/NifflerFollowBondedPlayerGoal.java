package at.koopro.wizardsandbeasts.entity.niffler.ai;

import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Follows the bonded owner once bond level ≥ 50.
 * Stops following at <= 2 blocks; resumes at > 6 blocks from owner.
 */
public class NifflerFollowBondedPlayerGoal extends Goal {

    private static final double STOP_DIST = 2.0;
    private static final double START_DIST = 6.0;

    private final NifflerEntity niffler;
    @Nullable private Player owner;

    public NifflerFollowBondedPlayerGoal(NifflerEntity niffler) {
        this.niffler = niffler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (niffler.isCarried()) return false;
        owner = resolveOwner();
        if (owner == null) return false;
        if (niffler.getBondLevel() < 50) return false;
        return niffler.distanceToSqr(owner) > START_DIST * START_DIST;
    }

    @Override
    public boolean canContinueToUse() {
        if (niffler.isCarried() || owner == null || !owner.isAlive()) return false;
        return niffler.distanceToSqr(owner) > STOP_DIST * STOP_DIST;
    }

    @Override
    public void tick() {
        if (owner == null) return;
        niffler.getLookControl().setLookAt(owner, 10f, niffler.getMaxHeadXRot());
        niffler.getNavigation().moveTo(owner, 1.0);
    }

    @Override
    public void stop() {
        owner = null;
        niffler.getNavigation().stop();
    }

    @Nullable
    private Player resolveOwner() {
        UUID uuid = niffler.getOwnerUUID();
        if (uuid == null) return null;
        return niffler.level().getPlayerByUUID(uuid);
    }
}
