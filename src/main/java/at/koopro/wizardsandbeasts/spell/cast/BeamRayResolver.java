package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Single source of truth for "where does a wand beam start, end, and hit?".
 * <p>
 * Used by both the client beam renderer ({@code client.beam}) and server channel logic
 * ({@code WandBeamChannelLogic}) so the visual end and the gameplay hit point always agree.
 * <p>
 * The ray is anchored at the player's eye on both sides; the renderer is free to draw the
 * tube from the wand tip to {@link BeamRay#end()} for visual style, but the resolved end
 * is what the server treats as the impact point.
 */
public final class BeamRayResolver {

    /** Default reach growth per tick during a held channel (blocks/tick). */
    public static final float DEFAULT_EXTENSION_BLOCKS_PER_TICK = 8.0f;

    private static float extensionBlocksPerTick = DEFAULT_EXTENSION_BLOCKS_PER_TICK;

    /** Mutable so {@link at.koopro.wizardsandbeasts.client.gui.BeamDebugScreen} can tune live. */
    public static float extensionBlocksPerTick() {
        return extensionBlocksPerTick;
    }

    public static void setExtensionBlocksPerTick(float blocksPerTick) {
        extensionBlocksPerTick = Mth.clamp(blocksPerTick, 1.0f, 40.0f);
    }

    /** Default living-entity targeting predicate (no caster, not spectator-equivalent). */
    public static final Predicate<Entity> LIVING_FILTER =
            e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator();

    /** Wider predicate that also picks up armor stands and items, mirroring {@code findLeviosaTargetAlongCrosshair}. */
    public static final Predicate<Entity> LEVIOSA_FILTER = e -> {
        if (e == null) return false;
        if (e instanceof ItemEntity || e instanceof FallingBlockEntity) return true;
        if (!e.isPickable() || e.isSpectator()) return false;
        return e instanceof LivingEntity || e instanceof ArmorStand;
    };

    private BeamRayResolver() {}

    /**
     * Resolves a beam ray for {@code caster} along their look vector, capped at {@code maxReach}.
     * Picks the closer of (block hit, entity hit). When neither hits, the end sits at {@code maxReach}.
     *
     * @param caster      who is casting; may be a client-side or server-side player
     * @param partialTick partial tick for smooth client interpolation; pass {@code 1f} on the server
     * @param maxReach    maximum reach in blocks (already ramped by tick count when called from a channel)
     * @param entityFilter which entities count as targets ({@link #LIVING_FILTER}, {@link #LEVIOSA_FILTER}, or custom)
     */
    public static BeamRay resolve(Player caster, float partialTick, float maxReach,
                                  Predicate<Entity> entityFilter) {
        Vec3 start = caster.getEyePosition(partialTick);
        Vec3 look = caster.getViewVector(partialTick);
        Vec3 unboundedEnd = start.add(look.scale(maxReach));

        BlockHitResult blockHit = caster.level().clip(new ClipContext(
                start,
                unboundedEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster));
        Vec3 blockEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : unboundedEnd;
        double blockDistSqr = start.distanceToSqr(blockEnd);

        EntityHitResult entityHit = pickEntityAlongRay(caster, start, blockEnd, maxReach, entityFilter);

        HitResult chosen;
        Vec3 end;
        if (entityHit != null) {
            double entityDistSqr = start.distanceToSqr(entityHit.getLocation());
            if (entityDistSqr <= blockDistSqr) {
                chosen = entityHit;
                end = entityHit.getLocation();
            } else {
                chosen = blockHit;
                end = blockEnd;
            }
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            chosen = blockHit;
            end = blockEnd;
        } else {
            chosen = blockHit; // MISS
            end = unboundedEnd;
        }

        float reached = (float) start.distanceTo(end);
        return new BeamRay(start, end, chosen, reached);
    }

    /** Convenience: living-entity filter, server-side (partialTick = 1). */
    public static BeamRay resolveLiving(Player caster, float maxReach) {
        return resolve(caster, 1.0f, maxReach, LIVING_FILTER);
    }

    /**
     * Computes the ramp-extended max reach for a given tick count, capped at {@code rangeBlocks}.
     * Mirrors {@code Math.min(range, beamTicks * extensionBlocksPerTick())}.
     */
    public static float rampedReach(float rangeBlocks, float elapsedTicks) {
        return Math.min(rangeBlocks, elapsedTicks * extensionBlocksPerTick);
    }

    /**
     * Manual entity ray pick. Mirrors the pattern in {@code findLeviosaTargetAlongCrosshair}
     * to avoid the cross-version churn of {@code ProjectileUtil.getEntityHitResult} signatures.
     */
    private static EntityHitResult pickEntityAlongRay(Player caster, Vec3 start, Vec3 end,
                                                      float maxReach, Predicate<Entity> filter) {
        AABB searchBox = caster.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        Entity closest = null;
        Vec3 closestHit = null;
        double closestDistSqr = Math.max(start.distanceToSqr(end), maxReach * maxReach);

        for (Entity candidate : caster.level().getEntities(caster, searchBox, filter)) {
            double inflate = candidate instanceof ItemEntity ? 0.6 : Math.max(0.1, candidate.getPickRadius());
            AABB hitBox = candidate.getBoundingBox().inflate(inflate);
            Optional<Vec3> intercept = hitBox.clip(start, end);
            if (intercept.isEmpty()) continue;
            double distSqr = start.distanceToSqr(intercept.get());
            if (distSqr < closestDistSqr) {
                closestDistSqr = distSqr;
                closest = candidate;
                closestHit = intercept.get();
            }
        }
        return closest == null ? null : new EntityHitResult(closest, closestHit);
    }
}
