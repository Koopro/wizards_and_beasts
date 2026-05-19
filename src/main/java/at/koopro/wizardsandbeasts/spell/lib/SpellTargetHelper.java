package at.koopro.wizardsandbeasts.spell.lib;

import at.koopro.wizardsandbeasts.spell.core.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Entity-finding helpers for spell targeting. */
public final class SpellTargetHelper {

    private SpellTargetHelper() {}

    /**
     * Finds all living entities (and optionally items) in a cone in front of the caster.
     */
    public static List<Entity> findEntitiesInCone(ServerLevel level, ServerPlayer caster,
                                                   float range, boolean includeItems) {
        Vec3 look = caster.getLookAngle();
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(look.scale(range));
        AABB area = new AABB(start, end).inflate(1.5);
        final double minDot = 0.75; // ~41 deg half-angle cone

        return level.getEntities(caster, area, e -> {
            if (e == caster) return false;
            if (!(e instanceof LivingEntity) && !(includeItems && e instanceof ItemEntity)) {
                return false;
            }

            Vec3 targetCenter = e.getBoundingBox().getCenter();
            Vec3 toTarget = targetCenter.subtract(start);
            double distance = toTarget.length();
            if (distance <= 1.0e-4 || distance > range) {
                return false;
            }

            Vec3 dirToTarget = toTarget.scale(1.0 / distance);
            if (look.dot(dirToTarget) < minDot) {
                return false;
            }

            // Ignore entities behind walls so cone spells feel deterministic.
            BlockHitResult blockHit = level.clip(new ClipContext(
                    start,
                    targetCenter,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    caster));
            return blockHit.getType() == HitResult.Type.MISS;
        });
    }

    /** Extra Accio targets that are physical but not living/items. */
    public static List<Entity> findAccioExtraTargets(ServerLevel level, ServerPlayer caster, float range) {
        Vec3 look = caster.getLookAngle();
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(look.scale(range));
        AABB area = new AABB(start, end).inflate(1.6);
        final double minDot = 0.72;
        List<Entity> out = new ArrayList<>();
        for (Entity e : level.getEntities(caster, area, SpellTargetHelper::isAccioExtraEntity)) {
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 toTarget = center.subtract(start);
            double distance = toTarget.length();
            if (distance <= 1.0e-4 || distance > range) continue;
            Vec3 dirToTarget = toTarget.scale(1.0 / distance);
            if (look.dot(dirToTarget) < minDot) continue;
            BlockHitResult blockHit = level.clip(new ClipContext(
                    start, center,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    caster));
            if (blockHit.getType() == HitResult.Type.MISS) {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean isAccioExtraEntity(Entity entity) {
        return entity instanceof Projectile || entity instanceof VehicleEntity;
    }

    /**
     * Finds the closest living entity the caster is looking at within range.
     * Uses a dot-product cone check (0.85 threshold ≈ 30° half-angle).
     */
    @Nullable
    public static LivingEntity findTargetedEntity(ServerLevel level, ServerPlayer caster, float range) {
        return findTargetedEntity(level, caster, range, e -> true);
    }

    /**
     * Finds the closest living entity the caster is looking at, with an additional filter.
     */
    @Nullable
    public static LivingEntity findTargetedEntity(ServerLevel level, ServerPlayer caster,
                                                   float range, Predicate<LivingEntity> filter) {
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        AABB searchArea = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);

        LivingEntity target = null;
        double closestDist = range * range;

        for (Entity e : level.getEntities(caster, searchArea, e -> e instanceof LivingEntity && e != caster)) {
            LivingEntity living = (LivingEntity) e;
            if (!filter.test(living)) continue;

            Vec3 toEntity = e.position().add(0, e.getBbHeight() / 2, 0).subtract(start).normalize();
            if (look.dot(toEntity) > 0.85) {
                Vec3 targetCenter = e.getBoundingBox().getCenter();
                BlockHitResult blockHit = level.clip(new ClipContext(
                        start,
                        targetCenter,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        caster));
                if (blockHit.getType() != HitResult.Type.MISS) {
                    continue;
                }
                double dist = start.distanceToSqr(e.position());
                if (dist < closestDist) {
                    closestDist = dist;
                    target = living;
                }
            }
        }
        return target;
    }

    /**
     * First living entity along the caster's view ray, up to {@code maxRange} blocks.
     * Uses {@link ProjectileUtil#getHitResultOnViewVector} so range is not capped by
     * vanilla {@link net.minecraft.world.entity.Entity#pick} entity interaction distance (~3 blocks).
     */
    @Nullable
    public static LivingEntity findLivingAlongCrosshair(ServerPlayer caster, float maxRange) {
        if (maxRange <= 0) return null;
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(
                caster,
                e -> e instanceof LivingEntity && e != caster && e.isPickable(),
                maxRange);
        if (hit.getType() != HitResult.Type.ENTITY) return null;
        Entity e = ((EntityHitResult) hit).getEntity();
        return e instanceof LivingEntity living ? living : null;
    }
}
