package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Common ability: applies a configurable list of status effects to a victim on melee contact. A richer,
 * data-driven generalisation of the {@code POISON_ATTACK}/{@code PETRIFY} traits (any effect, amplifier,
 * duration). Stacks alongside the trait-based on-hit effects.
 */
public record StatusOnHit(@NonNull List<AbilitySupport.EffectSpec> effects) implements CreatureAbility {

    public static final MapCodec<StatusOnHit> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            AbilitySupport.EffectSpec.LIST_CODEC.fieldOf("effects").forGetter(StatusOnHit::effects)
    ).apply(instance, StatusOnHit::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.STATUS_ON_HIT;
    }

    @Override
    public void onMeleeContact(@NonNull GenericBeastEntity entity, @NonNull LivingEntity target) {
        AbilitySupport.applyAll(target, effects);
    }
}
