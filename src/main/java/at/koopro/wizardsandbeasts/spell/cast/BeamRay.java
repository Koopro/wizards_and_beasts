package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Result of {@link BeamRayResolver#resolve}. The visual beam should run from
 * {@link #start} (eye / wand tip) to {@link #end} (resolved hit point or max reach).
 *
 * @param start              eye-anchored ray origin used for picking
 * @param end                resolved end point (block, entity, or max reach)
 * @param hit                full {@link HitResult} so callers can branch on entity vs block vs miss
 * @param reachedDistance    distance from {@link #start} to {@link #end} in blocks
 */
public record BeamRay(Vec3 start, Vec3 end, HitResult hit, float reachedDistance) {

    public boolean hitsAnything() {
        return hit != null && hit.getType() != HitResult.Type.MISS;
    }

    public boolean hitsEntity() {
        return hit != null && hit.getType() == HitResult.Type.ENTITY;
    }

    public boolean hitsBlock() {
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }
}
