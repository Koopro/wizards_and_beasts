package at.koopro.wizardsandbeasts.block;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class WardingStoneBlock extends Block {

    /** Pale blue, matching the rune ring that pulses on the block's own texture. */
    private static final int RUNE_ARGB = 0xFF7ED6EC;

    public WardingStoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // A ward that looks exactly like cobblestone is a gameplay problem, not just a dull one:
        // players cannot tell a protected area from an ordinary wall. Motes seep out of the stone's
        // whole volume rather than rising off the top face, so a ward set into a floor still reads.
        if (random.nextInt(6) != 0) {
            return;
        }
        level.addParticle(new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), RUNE_ARGB),
                pos.getX() + random.nextDouble(),
                pos.getY() + random.nextDouble(),
                pos.getZ() + random.nextDouble(),
                (random.nextDouble() - 0.5) * 0.01,
                0.004 + random.nextDouble() * 0.008,
                (random.nextDouble() - 0.5) * 0.01);
    }
}
