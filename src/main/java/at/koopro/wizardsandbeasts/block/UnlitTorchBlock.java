package at.koopro.wizardsandbeasts.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Standing torch that emits no light and spawns no particles.
 * Used by the Deluminator as the "absorbed" version of a vanilla torch.
 */
public class UnlitTorchBlock extends TorchBlock {

    public static final MapCodec<UnlitTorchBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(PARTICLE_OPTIONS_FIELD.forGetter(b -> b.flameParticle), propertiesCodec())
                    .apply(instance, UnlitTorchBlock::new));

    public UnlitTorchBlock(SimpleParticleType particle, Properties properties) {
        super(particle, properties);
    }

    @Override
    public MapCodec<? extends TorchBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // No particles for unlit torch
    }
}
