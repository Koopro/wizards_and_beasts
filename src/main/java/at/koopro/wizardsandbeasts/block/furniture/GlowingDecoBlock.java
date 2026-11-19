package at.koopro.wizardsandbeasts.block.furniture;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

/**
 * Furniture that gives off light and, now and then, something rising from it: a crystal's motes in its own colour, a
 * fireplace's smoke and sparks, a candelabra's thread of smoke. The glow itself is the block's light level and an
 * animated texture; nothing here ticks.
 */
@NullMarked
public class GlowingDecoBlock extends FurnitureBlock {

    /** What rises: tinted motes, or a hearth's smoke and flame (in its flame's particle). */
    public enum Emission { MOTES, HEARTH, SOUL_HEARTH, GREEN_HEARTH, SMOKE }

    private final Emission emission;
    private final int argb;
    /** Where it rises from, in block units, in the authoring frame (front to the north). */
    private final double x, y, z;

    public GlowingDecoBlock(BlockBehaviour.Properties properties, Spec spec, Emission emission, int argb,
                            double x, double y, double z) {
        super(properties, spec);
        this.emission = emission;
        this.argb = argb;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    protected MapCodec<? extends GlowingDecoBlock> codec() {
        return simpleCodec(properties -> new GlowingDecoBlock(properties, spec(), emission, argb, x, y, z));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // The authoring point turned by facing, as FurnitureBlock.rotated turns outlines.
        double lx = x, lz = z;
        switch (state.getValue(FACING)) {
            case EAST -> { lx = 16 - z; lz = x; }
            case SOUTH -> { lx = 16 - x; lz = 16 - z; }
            case WEST -> { lx = z; lz = 16 - x; }
            default -> { }
        }
        double px = pos.getX() + lx / 16 + (random.nextDouble() - 0.5) * 0.25;
        double py = pos.getY() + y / 16;
        double pz = pos.getZ() + lz / 16 + (random.nextDouble() - 0.5) * 0.25;
        switch (emission) {
            case MOTES -> {
                if (random.nextInt(5) == 0) {
                    level.addParticle(new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), argb), px, py, pz, 0, 0.02, 0);
                }
            }
            case HEARTH, SOUL_HEARTH -> {
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, px, py + 0.3, pz, 0, 0.03, 0);
                }
                if (random.nextInt(6) == 0) {
                    level.addParticle(emission == Emission.HEARTH ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                            px, py, pz, 0, 0.01, 0);
                }
            }
            case GREEN_HEARTH -> {
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, px, py + 0.3, pz, 0, 0.03, 0);
                }
                if (random.nextInt(4) == 0) {
                    level.addParticle(new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), argb), px, py, pz, 0, 0.04, 0);
                }
            }
            case SMOKE -> {
                if (random.nextInt(5) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0.01, 0);
                }
            }
        }
    }
}
