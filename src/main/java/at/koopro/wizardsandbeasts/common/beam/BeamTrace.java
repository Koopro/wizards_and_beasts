package at.koopro.wizardsandbeasts.common.beam;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where does a beam hit? Blocks then entities, closer wins. Lives in {@code common} (not
 * {@code client}) on purpose: the server runs the identical trace for damage, so both sides land
 * on the same point from the same block data with no network sync of the hit point.
 */
public final class BeamTrace {

    private BeamTrace() {}

    /**
     * @param start     beam origin (world)
     * @param direction aim direction (need not be normalised)
     * @param maxRange  how far to look, in blocks
     * @param filter    extra predicate on top of pickable/alive/non-spectator (e.g. exclude caster)
     * @return the closer of the block hit and entity hit; a MISS {@link BlockHitResult} at
     *         {@code maxRange} if nothing is struck
     */
    public static HitResult trace(LivingEntity caster, Vec3 start, Vec3 direction,
                                  double maxRange, Predicate<Entity> filter) {
        Vec3 end = start.add(direction.normalize().scale(maxRange));

        // Fluid.NONE so the beam passes through water instead of stopping at the surface.
        BlockHitResult blockHit = caster.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 blockEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;

        // Search entities only up to the block hit, never the full range: nothing behind a wall can
        // be struck, so there is no reason to sweep past it.
        EntityHitResult entityHit = pickEntity(caster, start, blockEnd, filter);
        if (entityHit != null && start.distanceToSqr(entityHit.getLocation()) <= start.distanceToSqr(blockEnd)) {
            return entityHit;
        }
        return blockHit;
    }

    private static @Nullable EntityHitResult pickEntity(LivingEntity caster, Vec3 start, Vec3 end,
                                                        Predicate<Entity> filter) {
        AABB search = caster.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        // Spectators are excluded here (Palladium forgets to — a spectator can block its laser).
        Predicate<Entity> full = e -> e.isPickable() && !e.isSpectator() && e.isAlive() && filter.test(e);

        Entity closest = null;
        Vec3 closestHit = null;
        double closestSqr = start.distanceToSqr(end);

        for (Entity candidate : caster.level().getEntities(caster, search, full)) {
            AABB box = candidate.getBoundingBox().inflate(Math.max(0.1, candidate.getPickRadius()));
            Optional<Vec3> hit = box.clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distSqr = start.distanceToSqr(hit.get());
            if (distSqr < closestSqr) {
                closestSqr = distSqr;
                closest = candidate;
                closestHit = hit.get();
            }
        }
        return closest == null ? null : new EntityHitResult(closest, closestHit);
    }
}
