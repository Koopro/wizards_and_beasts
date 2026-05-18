package at.koopro.wizardsandbeasts.spell.lib;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class SpellParticles {
    private SpellParticles() {}

    static void spawnBurst(ServerLevel level, Spell spell, Vec3 pos, int count, double spread) {
        SpellTintParticleOptions opts = ModParticles.tinted(SpellFamilies.of(spell), spell.getColor());
        level.sendParticles(opts, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.05);
    }

    static void spawnBurst(ServerLevel level, SpellFamily family, int argb, Vec3 pos, int count, double spread) {
        SpellTintParticleOptions opts = ModParticles.tinted(family, argb);
        level.sendParticles(opts, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.05);
    }

    /** Fallback when no spell/family context exists (commands, legacy callers). */
    static void spawnBurst(ServerLevel level, Vec3 pos, int argbColor, int count, double spread) {
        int rgb = argbColor & 0x00FFFFFF;
        level.sendParticles(
                new DustParticleOptions(rgb, 1.0f),
                pos.x, pos.y, pos.z, count, spread, spread, spread, 0.05);
    }

    static void spawnBeam(ServerLevel level, Spell spell, Vec3 from, Vec3 to) {
        SpellTintParticleOptions opts = ModParticles.tinted(SpellFamilies.of(spell), spell.getColor());
        beamTinted(level, from, to, opts);
    }

    static void spawnBeam(ServerLevel level, Vec3 from, Vec3 to, int argbColor) {
        int rgb = argbColor & 0x00FFFFFF;
        beamDust(level, from, to, rgb);
    }

    static void spawnTrail(ServerLevel level, Spell spell, Vec3 position, Vec3 motion, int segments) {
        SpellTintParticleOptions opts = ModParticles.tinted(SpellFamilies.of(spell), spell.getColor());
        trailTinted(level, position, motion, segments, opts);
    }

    static void spawnTrail(ServerLevel level, Vec3 position, Vec3 motion, int argbColor, int segments) {
        int rgb = argbColor & 0x00FFFFFF;
        trailDust(level, position, motion, segments, rgb);
    }

    private static void beamTinted(ServerLevel level, Vec3 from, Vec3 to, SpellTintParticleOptions opts) {
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
            level.sendParticles(opts, base.x, base.y, base.z, 1, dx, dy, dz, 0.0);
        }
    }

    private static void beamDust(ServerLevel level, Vec3 from, Vec3 to, int rgb) {
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

    private static void trailTinted(ServerLevel level, Vec3 position, Vec3 motion, int segments,
                                    SpellTintParticleOptions opts) {
        for (int i = 0; i < segments; i++) {
            double t = i / (double) segments;
            double px = position.x - motion.x * t * 0.4;
            double py = position.y - motion.y * t * 0.4;
            double pz = position.z - motion.z * t * 0.4;
            level.sendParticles(opts, px, py, pz, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static void trailDust(ServerLevel level, Vec3 position, Vec3 motion, int segments, int rgb) {
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
}
