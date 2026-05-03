package at.koopro.wizardsandbeasts.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wall-mounted torch that emits no light and spawns no particles.
 * Used by the Deluminator as the "absorbed" version of a vanilla wall torch.
 */
public class UnlitWallTorchBlock extends WallTorchBlock {

    public static final MapCodec<UnlitWallTorchBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(PARTICLE_OPTIONS_FIELD.forGetter(b -> b.flameParticle), propertiesCodec())
                    .apply(instance, UnlitWallTorchBlock::new));

    public UnlitWallTorchBlock(SimpleParticleType particle, Properties properties) {
        super(particle, properties);
    }

    @SuppressWarnings("unchecked")
    @Override
    public MapCodec<WallTorchBlock> codec() {
        return (MapCodec<WallTorchBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // No particles for unlit wall torch
    }
}
