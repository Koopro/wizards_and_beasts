package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.DiveBombGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: an aerial dive-bomb. A flyer that has climbed above its target folds its wings and stoops,
 * slamming the target for {@code diveDamage} bonus impact and knocking it back, then peels off on a cooldown
 * (Griffin, Hippogriff, Zouwu, Thunderbird). Behaviour lives in {@link DiveBombGoal}, which self-gates on the
 * module. Harmless on grounded creatures (the goal only fires while airborne and above the target).
 */
public record DiveBomb(double range, float diveDamage, int cooldownTicks) implements CreatureAbility {

    public static final MapCodec<DiveBomb> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 16.0).forGetter(DiveBomb::range),
            Codec.FLOAT.optionalFieldOf("dive_damage", 4.0f).forGetter(DiveBomb::diveDamage),
            Codec.INT.optionalFieldOf("cooldown_ticks", 120).forGetter(DiveBomb::cooldownTicks)
    ).apply(instance, DiveBomb::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.DIVE_BOMB;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        // Above the aerial pursuit (3), which brings a large flier over its target: the stoop takes over from it.
        goalSelector.addGoal(2, new DiveBombGoal(entity, this));
    }
}
