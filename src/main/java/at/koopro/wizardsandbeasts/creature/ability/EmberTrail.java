package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Ashwinder): born of magical fire and red-hot, it leaves a trail of glowing cinders.
 * While moving on the ground it has a small per-tick chance to set down a fire block in its wake (only into
 * air on top of a solid block) — the smouldering trail of an Ashwinder. The "fire emission" follow-up the
 * fire-affinity pass deferred; no new items (the canon igniting egg is left for a loot pass).
 */
public record EmberTrail(float chancePerTick, int emberParticles) implements CreatureAbility {

    public static final MapCodec<EmberTrail> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("chance_per_tick", 0.06f).forGetter(EmberTrail::chancePerTick),
            Codec.INT.optionalFieldOf("ember_particles", 3).forGetter(EmberTrail::emberParticles)
    ).apply(instance, EmberTrail::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.EMBER_TRAIL;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.onGround()) {
            return;
        }
        if (entity.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4 || entity.getRandom().nextFloat() >= chancePerTick) {
            if (emberParticles > 0) {
                AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.FLAME, 1);
            }
            return;
        }
        BlockPos at = entity.blockPosition();
        BlockState below = level.getBlockState(at.below());
        if (level.getBlockState(at).isAir() && below.isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
            level.setBlockAndUpdate(at, Blocks.FIRE.defaultBlockState());
            AbilitySupport.emitAt(level, AbilitySupport.Particle.ASH, entity.getX(), entity.getY() + 0.1, entity.getZ(), 6, 0.2, 0.02);
        }
    }
}
