package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.WebSnareGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: a ranged snare. On a cooldown the creature flings webbing at its target, rooting it with
 * heavy Slowness for {@code durationTicks} and tracing cobweb-coloured particles. The Acromantula kit.
 * Hitscan (no projectile entity); self-gates on the module in its goal.
 */
public record WebSnare(double range, int slowAmplifier, int durationTicks, int cooldownTicks)
        implements CreatureAbility {

    public static final MapCodec<WebSnare> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 10.0).forGetter(WebSnare::range),
            Codec.INT.optionalFieldOf("slow_amplifier", 3).forGetter(WebSnare::slowAmplifier),
            Codec.INT.optionalFieldOf("duration_ticks", 100).forGetter(WebSnare::durationTicks),
            Codec.INT.optionalFieldOf("cooldown_ticks", 140).forGetter(WebSnare::cooldownTicks)
    ).apply(instance, WebSnare::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.WEB_SNARE;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        goalSelector.addGoal(2, new WebSnareGoal(entity, this));
    }
}
