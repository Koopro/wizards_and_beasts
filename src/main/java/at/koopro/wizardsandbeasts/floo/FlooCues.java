package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * What the Floo Network looks and sounds like, in one place.
 *
 * <h2>Why this exists</h2>
 * <p>The emerald {@code 0xFF21B342} was written out at seven call sites across five files, each with
 * its own particle count and its own idea of how wide the column should be. Refusals played whatever
 * vanilla sound the author reached for — {@code FIRE_EXTINGUISH} in one place, nothing at all in
 * three others — so the same event sounded different depending on which door you hit it through.
 *
 * <p>A system's identity is the thing most easily lost to that kind of drift, because no single site
 * looks wrong on its own. Collecting it here means the green is one green, a refusal always sounds
 * like a refusal, and changing either is one edit rather than seven.
 *
 * <p>Server-side. The client draws its own idle flicker and its own screen effects; this is the half
 * that has to reach everybody standing in the room.
 */
@NullMarked
public final class FlooCues {

    /** The Floo green. Every emerald particle in the system is this colour. */
    public static final int EMERALD = 0xFF21B342;

    private FlooCues() {
    }

    public static DustParticleOptions emerald(float scale) {
        return new DustParticleOptions(EMERALD, scale);
    }

    // -- refusals --------------------------------------------------------------------------------

    /**
     * A line that will not open.
     *
     * <p>Low and flat rather than harsh: a sealed connection is a door that has been shut on purpose,
     * and it should not sound like something breaking.
     *
     * <p>Audible to the room rather than to the refused player alone. A grate refusing somebody is a
     * physical event in a room — the fire thuds and does not open — and the people standing around it
     * are exactly who should learn that this hearth goes nowhere. It also keeps every Floo cue on one
     * mechanism: {@code level.playSound} at a position, with nothing needing a per-player packet.
     */
    public static void sealed(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), ModSounds.FLOO_SEALED.get(),
                    SoundSource.BLOCKS, 0.8f, 1.0f);
        }
    }

    /**
     * A hop that did not take: out of powder, blocked at the far end, a departure cut short.
     *
     * <p>Audible to the room, because a sputtering grate is a visible event — somebody stood in the
     * fire, the fire spat, and nothing happened.
     */
    public static void sputter(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.FLOO_FAIL_SPUTTER.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                10, 0.25, 0.3, 0.25, 0.02);
    }

    // -- the journey -----------------------------------------------------------------------------

    /**
     * One tick of the swirl a traveller is standing in.
     *
     * <p>Orbiting rather than rising, and that is the whole difference between this and the hearth's
     * idle column. A fire burning in a grate goes up; a wizard being taken by the Network is being
     * turned, and the particles have to say which of those is happening. The orbit tightens and
     * speeds up as the window closes, so the moment of departure is legible from outside the fire as
     * well as inside it.
     *
     * @param progress 0 at the start of the departure window, 1 at the moment of the hop
     */
    public static void swirl(ServerLevel level, double x, double y, double z, float progress) {
        int arms = 3;
        // Two turns over the window, plus a per-arm offset. The turn count is what makes it read as
        // rotation rather than as a ring that happens to be shimmering.
        float angle = progress * Mth.TWO_PI * 2.0f;
        double radius = 0.85 - 0.45 * progress;
        double lift = 0.2 + 1.4 * progress;
        DustParticleOptions dust = emerald(1.0f + progress * 0.9f);

        for (int arm = 0; arm < arms; arm++) {
            double a = angle + (Mth.TWO_PI / arms) * arm;
            double px = x + Math.cos(a) * radius;
            double pz = z + Math.sin(a) * radius;
            level.sendParticles(dust, px, y + lift * level.random.nextDouble(), pz,
                    1, 0.04, 0.12, 0.04, 0.0);
            // Ash rides the same orbit, a quarter turn behind, so the swirl has grit in it and is
            // not purely a light show. Floo travel is famously filthy.
            double ashAngle = a - 0.5;
            level.sendParticles(ParticleTypes.ASH,
                    x + Math.cos(ashAngle) * radius, y + lift * 0.6, z + Math.sin(ashAngle) * radius,
                    1, 0.05, 0.1, 0.05, 0.0);
        }
    }

    /** The rushing loop under the swirl. Called on an interval, not every tick. */
    public static void travelLoop(ServerLevel level, BlockPos pos, float progress) {
        level.playSound(null, pos, ModSounds.FLOO_TRAVEL_LOOP.get(), SoundSource.BLOCKS,
                0.4f, 0.8f + progress * 0.7f);
    }

    // -- the ends --------------------------------------------------------------------------------

    /**
     * A grate flaring as somebody comes out of it.
     *
     * <p>A vertical burst, deliberately unlike the swirl: the turning is over, and what the room sees
     * is fire going straight up out of a fireplace. Ash falls through it, which is what makes the
     * traveller who walks out of it look like they have been somewhere.
     */
    public static void arrivalBurst(ServerLevel level, double x, double y, double z) {
        level.sendParticles(emerald(1.5f), x, y + 0.5, z, 26, 0.22, 0.85, 0.22, 0.09);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.3, z, 14, 0.2, 0.5, 0.2, 0.07);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.4, z, 6, 0.2, 0.2, 0.2, 0.01);
        level.sendParticles(ParticleTypes.ASH, x, y + 1.0, z, 18, 0.35, 0.45, 0.35, 0.0);
    }

    /** The soot a traveller brings with them, thrown around wherever they land. */
    public static void sootFall(ServerLevel level, double x, double y, double z, boolean heavy) {
        ParticleOptions smoke = ParticleTypes.CAMPFIRE_COSY_SMOKE;
        level.sendParticles(smoke, x, y + 0.6, z, heavy ? 14 : 7, 0.3, 0.4, 0.3, 0.005);
        level.sendParticles(ParticleTypes.ASH, x, y + 0.4, z, heavy ? 28 : 14, 0.35, 0.45, 0.35, 0.01);
    }

    /** The column that goes up when a pinch of powder catches. */
    public static void ignite(ServerLevel level, BlockPos pos) {
        DustParticleOptions dust = emerald(1.6f);
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (int i = 0; i < 40; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 0.7;
            double oz = (level.random.nextDouble() - 0.5) * 0.7;
            double y = pos.getY() + 0.1 + level.random.nextDouble() * 1.6;
            level.sendParticles(dust, cx + ox, y, cz + oz, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, pos.getY() + 0.4, cz,
                18, 0.25, 0.5, 0.25, 0.08);
        level.sendParticles(ParticleTypes.LAVA, cx, pos.getY() + 0.3, cz, 4, 0.2, 0.2, 0.2, 0.0);
    }
}
