package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.GatedLeapGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: a pouncing leap at the current attack target ({@link GatedLeapGoal} — vanilla
 * leap-at-target behaviour that self-gates on {@code Module.CREATURES}). The
 * Wampus-Cat/Kneazle/Werewolf/Swooping-Evil/Manticore kit. Pure goal contribution — no tick/hit hooks.
 */
public record LeapAtTarget(float leapVelocity) implements CreatureAbility {

    public static final MapCodec<LeapAtTarget> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("leap_velocity", 0.4f).forGetter(LeapAtTarget::leapVelocity)
    ).apply(instance, LeapAtTarget::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.LEAP;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        goalSelector.addGoal(3, new GatedLeapGoal(entity, leapVelocity));
    }
}
