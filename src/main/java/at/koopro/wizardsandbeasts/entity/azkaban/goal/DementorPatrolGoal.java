package at.koopro.wizardsandbeasts.entity.azkaban.goal;

import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.azkaban.structure.AzkabanStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

public final class DementorPatrolGoal extends Goal {

    private static final int PATROL_INTERVAL = 80;

    private final DementorEntity dementor;
    private @Nullable Vec3 patrolTarget;
    private int wanderCooldown = 0;

    public DementorPatrolGoal(DementorEntity dementor) {
        this.dementor = dementor;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return dementor.getFeltPlayers(24.0).isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return dementor.getFeltPlayers(24.0).isEmpty();
    }

    @Override
    public void tick() {
        if (wanderCooldown-- <= 0) {
            patrolTarget = pickPatrolPoint();
            wanderCooldown = PATROL_INTERVAL + dementor.getRandom().nextInt(40);
        }
        if (patrolTarget != null) {
            dementor.getMoveControl().setWantedPosition(
                    patrolTarget.x, patrolTarget.y, patrolTarget.z, 0.45);
        }
    }

    private Vec3 pickPatrolPoint() {
        BlockPos center = AzkabanStructures.cachedFortressCenter;
        if (center == null) {
            // Fallback: drift randomly around current position
            Vec3 pos = dementor.position();
            return pos.add(
                    (dementor.getRandom().nextDouble() - 0.5) * 20,
                    (dementor.getRandom().nextDouble() - 0.5) * 10,
                    (dementor.getRandom().nextDouble() - 0.5) * 20);
        }
        // Bias toward high-security wing (upper floors, Y+60 from base)
        boolean highSec = dementor.getRandom().nextInt(3) == 0;
        double dy = highSec ? 60 + dementor.getRandom().nextDouble() * 20
                            : dementor.getRandom().nextDouble() * 50;
        return new Vec3(
                center.getX() + (dementor.getRandom().nextDouble() - 0.5) * 14,
                center.getY() + dy,
                center.getZ() + (dementor.getRandom().nextDouble() - 0.5) * 14);
    }
}
