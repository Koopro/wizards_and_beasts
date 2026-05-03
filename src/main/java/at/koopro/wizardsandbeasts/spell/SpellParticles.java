package at.koopro.wizardsandbeasts.spell;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class SpellParticles {
    private SpellParticles() {}

    static void spawnBeam(ServerLevel level, Vec3 from, Vec3 to, int argbColor) {
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

    static void spawnTrail(ServerLevel level, Vec3 position, Vec3 motion, int argbColor, int segments) {
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

    static void spawnBurst(ServerLevel level, Vec3 pos, int argbColor, int count, double spread) {
        int rgb = argbColor & 0x00FFFFFF;
        level.sendParticles(
                new DustParticleOptions(rgb, 1.0f),
                pos.x, pos.y, pos.z, count, spread, spread, spread, 0.05);
    }
}
