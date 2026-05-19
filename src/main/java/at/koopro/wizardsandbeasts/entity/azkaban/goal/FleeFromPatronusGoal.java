package at.koopro.wizardsandbeasts.entity.azkaban.goal;

import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.spell.patronus.PatronusDetection;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class FleeFromPatronusGoal extends Goal {

    private static final double REPEL_RADIUS = 24.0;
    private static final int LOCK_TICKS = 60;

    private final DementorEntity dementor;
    private int lockoutTimer = 0;
    private Vec3 fleeTarget = null;

    public FleeFromPatronusGoal(DementorEntity dementor) {
        this.dementor = dementor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (lockoutTimer > 0) {
            lockoutTimer--;
            return fleeTarget != null;
        }
        return PatronusDetection.isPatronusNearby(dementor.level(), dementor.position(), REPEL_RADIUS);
    }

    @Override
    public boolean canContinueToUse() {
        return lockoutTimer > 0;
    }

    @Override
    public void start() {
        dementor.setRepelled(true);
        lockoutTimer = LOCK_TICKS;
        // Flee away from the Dementor's current position
        Vec3 pos = dementor.position();
        fleeTarget = pos.add(
                (dementor.getRandom().nextDouble() - 0.5) * 24,
                4 + dementor.getRandom().nextDouble() * 4,
                (dementor.getRandom().nextDouble() - 0.5) * 24);
    }

    @Override
    public void stop() {
        dementor.setRepelled(false);
        lockoutTimer = 0;
        fleeTarget = null;
    }

    @Override
    public void tick() {
        if (fleeTarget != null) {
            dementor.getMoveControl().setWantedPosition(
                    fleeTarget.x, fleeTarget.y, fleeTarget.z, 0.85);
        }
        lockoutTimer--;
    }
}
