package at.koopro.wizardsandbeasts.spell.patronus;

import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PatronusDetection {

    private PatronusDetection() {}

    /**
     * Returns true if any active PatronusEntity is within {@code radius} of {@code pos}.
     *
     * Consumers: Dementor aura masking, detection masking, FleeFromPatronusGoal, DementorKissGoal.
     *
     * // TODO: replace with deeper Patronus API if one is added (e.g. patronus power threshold check)
     */
    public static boolean isPatronusNearby(Level level, Vec3 pos, double radius) {
        if (!(level instanceof ServerLevel sl)) return false;
        AABB box = new AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        return !sl.getEntitiesOfClass(PatronusEntity.class, box).isEmpty();
    }
}
