package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

/**
 * Three candles hanging in the air at different heights, as over the tables of a great hall. They need nothing under
 * them and nothing walks into them. A hall can hold hundreds, so there is no block entity: the flames flicker by an
 * animated texture and a thread of smoke rises now and then.
 */
@NullMarked
public class FloatingCandlesBlock extends Block {

    /** Flame tips in block units (x, y, z); the third burns just above the block, as {@code tools/furniture_models.py} draws them. */
    private static final double[][] FLAMES = {{4.5, 15.5, 5.5}, {11.5, 13.5, 6.5}, {7.5, 17.5, 11.5}};
    private static final VoxelShape OUTLINE = Block.box(3, 3, 4, 13, 15, 13);

    public FloatingCandlesBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<FloatingCandlesBlock> codec() {
        return simpleCodec(FloatingCandlesBlock::new);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            double[] f = FLAMES[random.nextInt(FLAMES.length)];
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + f[0] / 16, pos.getY() + f[1] / 16 + 0.08,
                    pos.getZ() + f[2] / 16, 0, 0.01, 0);
        }
    }
}
