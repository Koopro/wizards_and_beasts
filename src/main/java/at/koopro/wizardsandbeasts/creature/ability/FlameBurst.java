package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.FlameBurstGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Fire Crab): the protective flame burst. When threatened, on a cooldown the Fire Crab
 * blasts fire from its jewelled shell — every living thing within {@code radius} is set alight and takes
 * {@code damage}, with a ring of flame particles. The follow-up "fire emission" behaviour the original
 * fire-affinity pass deferred. Server-authoritative AoE (no projectile entity); self-gates in its goal.
 */
public record FlameBurst(double radius, float damage, int burnSeconds, int cooldownTicks) implements CreatureAbility {

    public static final MapCodec<FlameBurst> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 4.0).forGetter(FlameBurst::radius),
            Codec.FLOAT.optionalFieldOf("damage", 3.0f).forGetter(FlameBurst::damage),
            Codec.INT.optionalFieldOf("burn_seconds", 4).forGetter(FlameBurst::burnSeconds),
            Codec.INT.optionalFieldOf("cooldown_ticks", 100).forGetter(FlameBurst::cooldownTicks)
    ).apply(instance, FlameBurst::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.FLAME_BURST;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        goalSelector.addGoal(2, new FlameBurstGoal(entity, this));
    }
}
