package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NonNull;

/**
 * What a Dementor looks and sounds like to this client, beyond its model: frost falling from its hem, and the memory
 * voices of whoever it is chilling.
 *
 * <p>A Muggle gets all of it too: they cannot see the Dementor (see {@link DementorRenderer}) but they feel the cold
 * and hear what it makes them remember, as Dudley did.
 *
 * <p>Everything here is local and cosmetic — each client draws its own, so nothing is sent and nothing doubles. The
 * voices are the one thing that must be per <em>listener</em>, not per Dementor: under a swarm the old per-entity
 * cooldown whispered once for every Dementor in range. One shared clock now paces them.
 *
 * <p>Only ever called from the client branch of {@link DementorEntity#aiStep}, so this class never loads on a
 * dedicated server.
 */
public final class DementorClientEffects {

    /** Game time before which no memory voice plays for this client. */
    private static long nextVoice;

    private DementorClientEffects() {
    }

    public static void tick(@NonNull DementorEntity dementor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || dementor.isDissipating()) {
            return;
        }
        RandomSource random = dementor.getRandom();
        DementorEntity.State state = dementor.state();

        // Frost and shadow off the hem: sparse at rest, thicker while it feeds.
        int every = state == DementorEntity.State.DRAINING || state == DementorEntity.State.KISS_WINDUP
                || state == DementorEntity.State.KISS_RESOLVE ? 2 : 8;
        if (dementor.tickCount % every == 0) {
            double x = dementor.getX() + (random.nextDouble() - 0.5) * 0.9;
            double z = dementor.getZ() + (random.nextDouble() - 0.5) * 0.9;
            dementor.level().addParticle(ParticleTypes.SNOWFLAKE, x, dementor.getY() + random.nextDouble() * 1.2, z,
                    0.0, -0.02, 0.0);
            if (random.nextInt(3) == 0) {
                dementor.level().addParticle(ParticleTypes.SMOKE, x, dementor.getY() + 0.2, z, 0.0, -0.01, 0.0);
            }
        }

        // Memory voices: the chilled hear their worst memories, faintly, now and then.
        long now = dementor.level().getGameTime();
        if (nextVoice > now + 400) {
            nextVoice = 0;   // a new world's clock started lower than the last one's
        }
        var chill = mc.player.getEffect(ModEffects.DEMENTOR_CHILL);
        if (chill == null || chill.getAmplifier() < 1 || now < nextVoice) {
            return;
        }
        nextVoice = now + 100 + random.nextInt(140) - chill.getAmplifier() * 20L;
        dementor.level().playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                ModSounds.DEMENTOR_WHISPER.get(), SoundSource.HOSTILE, 0.25f + 0.1f * chill.getAmplifier(),
                0.8f + random.nextFloat() * 0.2f, false);
    }
}
