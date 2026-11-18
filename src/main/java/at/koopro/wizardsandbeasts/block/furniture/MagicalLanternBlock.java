package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

/**
 * A lantern whose flame burns without a wick: vanilla's lantern behaviour (standing or hanging, waterloggable) with
 * its own model, an animated flame texture for the flicker and, now and then, a mote rising from the cap. No block
 * entity: the flicker is the texture's.
 */
@NullMarked
public class MagicalLanternBlock extends LanternBlock {

    /** The colour of the motes off the lantern's flame (pale blue for the first lantern). */
    private final int moteArgb;

    public MagicalLanternBlock(Properties properties) {
        this(properties, 0xFF9FD8FF);
    }

    public MagicalLanternBlock(Properties properties, int moteArgb) {
        super(properties);
        this.moteArgb = moteArgb;
    }

    @Override
    public MapCodec<MagicalLanternBlock> codec() {
        return simpleCodec(properties -> new MagicalLanternBlock(properties, moteArgb));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            double top = state.getValue(HANGING) ? 0.70 : 0.62;
            level.addParticle(new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), moteArgb),
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2, pos.getY() + top, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2, 0, 0.04, 0);
        }
    }
}
