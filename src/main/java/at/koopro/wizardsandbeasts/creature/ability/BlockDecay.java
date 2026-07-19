package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Bundimun-style ability: periodically corrodes one nearby wooden block (canon: "rots wooden
 * foundations"). Deliberately conservative — long interval, low per-tick chance, restricted to
 * {@link BlockTags#PLANKS} only, skips anything with a block entity (chests etc.) — this is the one
 * ability in the library that can alter world blocks, so it stays narrow and slow rather than a general
 * "decays any block" mechanic.
 */
public record BlockDecay(int interval, int radius, float chance) implements CreatureAbility {

    public static final MapCodec<BlockDecay> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("interval", 100).forGetter(BlockDecay::interval),
            Codec.INT.optionalFieldOf("radius", 3).forGetter(BlockDecay::radius),
            Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.15f).forGetter(BlockDecay::chance)
    ).apply(instance, BlockDecay::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.BLOCK_DECAY;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        int period = Math.max(1, interval);
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % period != 0) {
            return;
        }
        if (entity.getRandom().nextFloat() >= chance) {
            return;
        }
        BlockPos origin = entity.blockPosition();
        int span = Math.max(1, radius * 2 + 1);
        for (int attempt = 0; attempt < 6; attempt++) {
            BlockPos candidate = origin.offset(
                    entity.getRandom().nextInt(span) - radius,
                    entity.getRandom().nextInt(3) - 1,
                    entity.getRandom().nextInt(span) - radius);
            BlockState state = level.getBlockState(candidate);
            if (!state.isAir() && state.is(BlockTags.PLANKS) && level.getBlockEntity(candidate) == null) {
                level.destroyBlock(candidate, false, entity);
                AbilitySupport.emitAt(level, AbilitySupport.Particle.SMOKE,
                        candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5, 6, 0.3, 0.02);
                return;
            }
        }
    }
}
