package at.koopro.wizardsandbeasts.spell.lib;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.def.SpellVfx;
import at.koopro.wizardsandbeasts.network.spell.SpellVfxShapeS2CPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class SpellParticles {
    private SpellParticles() {}

    /**
     * A spell's impact burst, in the particle the spell authored.
     *
     * <p>Count is clamped to {@code SpellVfx.MAX_IMPACT_COUNT}. These go through
     * {@code sendParticles}, so the server pays for every one of them and sends it to everyone in
     * range — a caller asking for a thousand is a server problem, not just a framerate one.
     */
    static void spawnBurst(ServerLevel level, Spell spell, Vec3 pos, int count, double spread) {
        var vfx = spell.vfx();
        SpellTintParticleOptions opts = ModParticles.tinted(vfx.impact().particleFamily(), spell.getColor());
        int capped = Math.min(SpellVfx.MAX_IMPACT_COUNT, Math.max(0, count));
        level.sendParticles(opts, pos.x, pos.y, pos.z, capped, spread, spread, spread, 0.05);
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
        SpellTintParticleOptions opts =
                ModParticles.tinted(spell.vfx().trail().particleFamily(), spell.getColor());
        beamTinted(level, from, to, opts);
    }

    static void spawnBeam(ServerLevel level, Vec3 from, Vec3 to, int argbColor) {
        int rgb = argbColor & 0x00FFFFFF;
        beamDust(level, from, to, rgb);
    }

    /**
     * A particle line: one packet ({@code SpellVfxShapeS2CPayload}) for the whole line, laid out by each client under
     * its own budget. It was one {@code sendParticles} per point — 65 to 300 packets per observer per cast (audit
     * 2026-11-15). Same spacing (ten a block, at least nine) and the same few-hundredths jitter.
     */
    private static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions opts) {
        double distance = from.distanceTo(to);
        if (distance <= 0.05) return;
        int points = Math.max(8, (int) (distance * 10)) + 1;
        SpellVfxShapeS2CPayload.line(level, opts, from, to, points, 0.035f);
    }

    private static void beamTinted(ServerLevel level, Vec3 from, Vec3 to, SpellTintParticleOptions opts) {
        line(level, from, to, opts);
    }

    private static void beamDust(ServerLevel level, Vec3 from, Vec3 to, int rgb) {
        line(level, from, to, new DustParticleOptions(rgb, 1.0f));
    }
}
