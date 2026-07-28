package at.koopro.wizardsandbeasts.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;

/**
 * A plain block that quietly emits a particle, so magical scenery reads as magical when nobody is
 * interacting with it.
 *
 * <p>Almost every block in the mod was a silent cube: only the Floo fireplace and the torches spawned
 * anything ambient, which left a Gringotts vault and a wizarding cauldron as inert as cobblestone.
 * This exists so adding that back is a registration change rather than a bespoke class per block.
 *
 * <p>{@code chance} is a one-in-N roll per client tick, deliberately sparse. Ambient particles are
 * background: at one-in-four a single block is already noticeable, and a wall of them at that rate
 * reads as a bug rather than atmosphere.
 *
 * <p>The particle supplier is invoked per spawn rather than stored as a value, so a block whose
 * particle carries a tint can vary it without needing its own subclass.
 */
@NullMarked
public class AmbientParticleBlock extends Block {

    /** Where the particle appears, relative to the block. */
    public enum Emission {
        /** Drifting up out of the top face — smoke, steam, motes off an open vessel. */
        ABOVE,
        /** Seeping from the block's own volume — glow bleeding out of stone. */
        WITHIN,
        /** Falling from the underside — an enchanted ceiling shedding light. */
        BELOW
    }

    private final Supplier<ParticleOptions> particle;
    private final Emission emission;
    private final int chance;

    public AmbientParticleBlock(Properties properties, Supplier<ParticleOptions> particle,
                                Emission emission, int chance) {
        super(properties);
        this.particle = particle;
        this.emission = emission;
        this.chance = Math.max(1, chance);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(chance) != 0) {
            return;
        }
        double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
        double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
        double y = switch (emission) {
            case ABOVE -> pos.getY() + 1.0 + random.nextDouble() * 0.2;
            case WITHIN -> pos.getY() + random.nextDouble();
            case BELOW -> pos.getY() - 0.05;
        };
        // Velocity is small and mostly vertical: ambient particles that shoot sideways read as an
        // effect firing rather than as atmosphere.
        double dy = switch (emission) {
            case ABOVE -> 0.01 + random.nextDouble() * 0.02;
            case WITHIN -> 0.005 + random.nextDouble() * 0.01;
            case BELOW -> -0.01 - random.nextDouble() * 0.015;
        };
        level.addParticle(particle.get(), x, y, z,
                (random.nextDouble() - 0.5) * 0.006, dy, (random.nextDouble() - 0.5) * 0.006);
    }
}
