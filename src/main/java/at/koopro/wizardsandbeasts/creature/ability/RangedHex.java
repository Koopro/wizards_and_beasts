package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.RangedHexGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Common ability: a server-authoritative ranged attack on a cooldown. When a target is in range with line
 * of sight, the creature "spits"/"hexes" it — applies {@code damage} + an optional status effect and traces
 * a particle line to the target (mirrors {@code DragonBreathGoal}: hitscan, no projectile entity). The
 * Snallygaster/Centaur/Runespoor/Giant/Merperson/Obscurus kit. Self-gates on the module in its goal.
 */
public record RangedHex(
        double range,
        float damage,
        Optional<AbilitySupport.EffectSpec> effect,
        int cooldownTicks,
        AbilitySupport.Particle particle
) implements CreatureAbility {

    public static final MapCodec<RangedHex> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 12.0).forGetter(RangedHex::range),
            Codec.FLOAT.optionalFieldOf("damage", 3.0f).forGetter(RangedHex::damage),
            AbilitySupport.EffectSpec.CODEC.optionalFieldOf("effect").forGetter(RangedHex::effect),
            Codec.INT.optionalFieldOf("cooldown_ticks", 60).forGetter(RangedHex::cooldownTicks),
            AbilitySupport.Particle.CODEC.optionalFieldOf("particle", AbilitySupport.Particle.WITCH).forGetter(RangedHex::particle)
    ).apply(instance, RangedHex::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.RANGED_HEX;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        goalSelector.addGoal(2, new RangedHexGoal(entity, this));
    }
}
