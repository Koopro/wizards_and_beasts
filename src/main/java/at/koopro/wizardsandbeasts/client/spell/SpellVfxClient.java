package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class SpellVfxClient {

    private SpellVfxClient() {}

    /** Low-pitched bass note — audible cast-denied feedback, no world position. */
    public static void playDeniedFeedback() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f));
    }

    public static void spawnTintBurst(Vec3 pos, SpellFamily family, int argb, int count, float spread) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RandomSource rand = mc.level.random;
        var opts = ModParticles.tinted(family, argb);
        for (int i = 0; i < count; i++) {
            double ox = (rand.nextDouble() - 0.5) * spread * 2.0;
            double oy = (rand.nextDouble() - 0.5) * spread * 2.0;
            double oz = (rand.nextDouble() - 0.5) * spread * 2.0;
            mc.level.addParticle(opts, pos.x + ox, pos.y + oy, pos.z + oz, 0.0, 0.02, 0.0);
        }
    }

    public static void spawnTintBeam(Vec3 from, Vec3 to, SpellFamily family, int argb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var opts = ModParticles.tinted(family, argb);
        double dist = from.distanceTo(to);
        if (dist < 0.02) {
            return;
        }
        int steps = Math.max(10, (int) (dist * 14.0));
        RandomSource rand = mc.level.random;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 p = from.lerp(to, t);
            double jitter = 0.025 + rand.nextDouble() * 0.03;
            double dx = (rand.nextDouble() - 0.5) * jitter;
            double dy = (rand.nextDouble() - 0.5) * jitter;
            double dz = (rand.nextDouble() - 0.5) * jitter;
            mc.level.addParticle(opts, p.x, p.y, p.z, dx, dy, dz);
        }
    }
}
