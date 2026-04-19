package at.koopro.neo.spell;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * Static helpers for spell implementations.
 * Use these when overriding {@link Spell#execute} for custom behavior.
 */
public final class SpellHelper {

    private SpellHelper() {}

    // ── Entity queries ──────────────────────────────────────────────────

    /**
     * Finds all living entities (and optionally items) in a cone in front of the caster.
     */
    public static List<Entity> findEntitiesInCone(ServerLevel level, ServerPlayer caster,
                                                   float range, boolean includeItems) {
        Vec3 look = caster.getLookAngle();
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(look.scale(range));
        AABB area = new AABB(start, end).inflate(1.5);

        return level.getEntities(caster, area, e -> {
            if (e == caster) return false;
            if (e instanceof LivingEntity) return true;
            return includeItems && e instanceof ItemEntity;
        });
    }

    /**
     * Finds the closest living entity the caster is looking at within range.
     * Uses a dot-product cone check (0.85 threshold ≈ 30° half-angle).
     */
    @Nullable
    public static LivingEntity findTargetedEntity(ServerLevel level, ServerPlayer caster,
                                                   float range) {
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
                double dist = start.distanceToSqr(e.position());
                if (dist < closestDist) {
                    closestDist = dist;
                    target = living;
                }
            }
        }
        return target;
    }

    // ── Combat effects ──────────────────────────────────────────────────

    /**
     * Applies knockback to an entity in the given direction.
     */
    public static void applyKnockback(Entity entity, Vec3 direction, float strength) {
        Vec3 kb = direction.normalize().scale(strength);
        entity.push(kb.x, 0.3, kb.z);
        entity.hurtMarked = true;
    }

    /**
     * Creates a spell explosion at the given position.
     */
    public static void createExplosion(Level level, Entity source, Vec3 pos,
                                        float power, boolean breaksBlocks) {
        Level.ExplosionInteraction interaction = breaksBlocks
                ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.MOB;
        level.explode(source, pos.x, pos.y, pos.z, power, interaction);
    }

    /**
     * Ignites an entity for the given duration in seconds.
     */
    public static void ignite(Entity entity, int seconds) {
        entity.igniteForSeconds(seconds);
    }

    // ── Particles ───────────────────────────────────────────────────────

    /**
     * Spawns a colored particle beam between two points.
     */
    public static void spawnBeam(ServerLevel level, Vec3 from, Vec3 to, int argbColor) {
        int rgb = argbColor & 0x00FFFFFF;
        double distance = from.distanceTo(to);
        if (distance <= 0.05) return;

        int steps = Math.max(8, (int) (distance * 10));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 base = from.lerp(to, t);

            double jitterScale = 0.02 + level.random.nextDouble() * 0.03;
            double dx = (level.random.nextDouble() - 0.5) * jitterScale;
            double dy = (level.random.nextDouble() - 0.5) * jitterScale;
            double dz = (level.random.nextDouble() - 0.5) * jitterScale;

            level.sendParticles(
                    new DustParticleOptions(rgb, 1.0f),
                    base.x, base.y, base.z, 1, dx, dy, dz, 0.0);
        }
    }

    /**
     * Spawns a short colored particle trail behind a moving entity/projectile.
     */
    public static void spawnTrail(ServerLevel level, Vec3 position, Vec3 motion,
                                   int argbColor, int segments) {
        int rgb = argbColor & 0x00FFFFFF;
        for (int i = 0; i < segments; i++) {
            double t = i / (double) segments;
            double px = position.x - motion.x * t * 0.4;
            double py = position.y - motion.y * t * 0.4;
            double pz = position.z - motion.z * t * 0.4;
            level.sendParticles(
                    new DustParticleOptions(rgb, 1.0f),
                    px, py, pz, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /**
     * Spawns a burst of colored particles at a position.
     */
    public static void spawnBurst(ServerLevel level, Vec3 pos, int argbColor,
                                   int count, double spread) {
        int rgb = argbColor & 0x00FFFFFF;
        level.sendParticles(
                new DustParticleOptions(rgb, 1.0f),
                pos.x, pos.y, pos.z, count, spread, spread, spread, 0.05);
    }
}
