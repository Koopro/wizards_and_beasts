package at.koopro.wizardsandbeasts.particle;

import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.MagicColours;
import net.minecraft.server.level.ServerLevel;

/**
 * Pixel smoke bursts in the mod's VFX language, in place of vanilla {@code LARGE_SMOKE}.
 *
 * <p>The colour is always one of {@code MagicColours} — the smoke says what made it (dark magic,
 * fire), it does not introduce a colour of its own. Counts stay low on purpose: each puff is a
 * readable shape, and a dozen of them is a burst where thirty vanilla smoke sprites were a fog.
 */
public final class MagicSmoke {

    private MagicSmoke() {}

    /** A burst of {@code count} puffs spread over a box of half-size {@code spreadXZ}/{@code spreadY}. */
    public static void burst(ServerLevel level, double x, double y, double z, int argb, int count,
                             double spreadXZ, double spreadY) {
        level.sendParticles(new SpellTintParticleOptions(ModParticles.SMOKE_PUFF.get(), argb),
                x, y, z, count, spreadXZ, spreadY, spreadXZ, 0.02);
    }

    /** Dark-magic smoke with the curling dread wisps through it — the Dementor's unravelling. */
    public static void dread(ServerLevel level, double x, double y, double z, int count,
                             double spreadXZ, double spreadY) {
        burst(level, x, y, z, MagicColours.DARK_MAGIC, count,
                spreadXZ, spreadY);
        level.sendParticles(new SpellTintParticleOptions(ModParticles.DARK_WISP.get(),
                        MagicColours.DARK_MAGIC),
                x, y, z, Math.max(2, count / 2), spreadXZ, spreadY, spreadXZ, 0.02);
    }
}
