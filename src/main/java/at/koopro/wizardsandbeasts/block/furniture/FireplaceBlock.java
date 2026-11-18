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
 * A great hearth: a multi-block surround with fire burning in its opening (the fire is the model's own, from an
 * animated texture). Cells over the fire send up smoke and sparks in the fire's colour; nothing ticks.
 */
@NullMarked
public class FireplaceBlock extends MultiblockFurnitureBlock {

    /** What the fire is: vanilla's warm fire, soul fire, or the Ministry's green (tinted motes). */
    public enum Flame { WARM, SOUL, GREEN }

    private final Flame flame;
    /** The opening's columns (inclusive) and how many rows the fire fills. */
    private final int fireFrom, fireTo, fireRows;

    public FireplaceBlock(BlockBehaviour.Properties properties, Spec spec, Flame flame, int fireFrom, int fireTo, int fireRows) {
        super(properties, spec);
        this.flame = flame;
        this.fireFrom = fireFrom;
        this.fireTo = fireTo;
        this.fireRows = fireRows;
    }

    @Override
    protected MapCodec<? extends FireplaceBlock> codec() {
        return simpleCodec(properties -> new FireplaceBlock(properties, spec(), flame, fireFrom, fireTo, fireRows));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int col = state.getValue(COL), row = state.getValue(ROW);
        if (col < fireFrom || col > fireTo || row >= fireRows) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
        double y = pos.getY() + (row == 0 ? 0.3 : 0.5) + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.4, z, 0, 0.04, 0);
        }
        if (row == 0 && random.nextInt(4) == 0) {
            switch (flame) {
                case WARM -> level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.02, 0);
                case SOUL -> level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.02, 0);
                case GREEN -> level.addParticle(new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), 0xFF6EE87A),
                        x, y, z, 0, 0.05, 0);
            }
        }
    }
}
