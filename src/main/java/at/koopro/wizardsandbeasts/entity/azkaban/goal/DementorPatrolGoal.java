package at.koopro.wizardsandbeasts.entity.azkaban.goal;

import at.koopro.wizardsandbeasts.azkaban.structure.AzkabanStructures;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Drift when nothing is felt. Inside Azkaban it patrols the fortress, a third of the time up in the high-security
 * wing; anywhere else it drifts around where it first appeared ({@link DementorEntity#home()}).
 *
 * <p>Previously every Dementor in the world patrolled toward the Azkaban fortress centre once one was known, so one
 * summoned anywhere else set off across the sea.
 */
public final class DementorPatrolGoal extends Goal {

    private static final int PATROL_INTERVAL = 80;
    private static final double SPEED = 0.5;
    private static final double HOME_RANGE = 12.0;

    private final DementorEntity dementor;
    private @Nullable Vec3 patrolTarget;
    private int wanderCooldown;

    public DementorPatrolGoal(DementorEntity dementor) {
        this.dementor = dementor;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return dementor.sensed() == null && !dementor.isDissipating();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        wanderCooldown = 0;
    }

    @Override
    public void tick() {
        if (wanderCooldown-- <= 0) {
            patrolTarget = pickPatrolPoint();
            wanderCooldown = PATROL_INTERVAL + dementor.getRandom().nextInt(40);
        }
        if (patrolTarget != null) {
            dementor.getMoveControl().setWantedPosition(patrolTarget.x, patrolTarget.y, patrolTarget.z, SPEED);
        }
    }

    private Vec3 pickPatrolPoint() {
        var random = dementor.getRandom();
        BlockPos center = AzkabanStructures.cachedFortressCenter;
        if (center == null || !dementor.insideAzkaban()) {
            BlockPos home = dementor.home();
            return new Vec3(
                    home.getX() + 0.5 + (random.nextDouble() - 0.5) * 2 * HOME_RANGE,
                    home.getY() + 1 + random.nextDouble() * 4,
                    home.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2 * HOME_RANGE);
        }
        boolean highSec = random.nextInt(3) == 0;
        double dy = highSec ? 60 + random.nextDouble() * 20 : random.nextDouble() * 50;
        return new Vec3(
                center.getX() + (random.nextDouble() - 0.5) * 14,
                center.getY() + dy,
                center.getZ() + (random.nextDouble() - 0.5) * 14);
    }
}
