package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Common ability: applies a configurable list of status effects to a victim on melee contact. A richer,
 * data-driven generalisation of the {@code POISON_ATTACK}/{@code PETRIFY} traits (any effect, amplifier,
 * duration). Stacks alongside the trait-based on-hit effects.
 *
 * <p>{@code chance} (default {@code 1.0}, unchanged for every existing consumer) gates whether the
 * effects apply at all on a given hit — e.g. the Rougarou's canon "10% chance" curse bite.
 */
public record StatusOnHit(@NonNull List<AbilitySupport.EffectSpec> effects, float chance) implements CreatureAbility {

    public static final MapCodec<StatusOnHit> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            AbilitySupport.EffectSpec.LIST_CODEC.fieldOf("effects").forGetter(StatusOnHit::effects),
            Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1.0f).forGetter(StatusOnHit::chance)
    ).apply(instance, StatusOnHit::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.STATUS_ON_HIT;
    }

    @Override
    public void onMeleeContact(@NonNull GenericBeastEntity entity, @NonNull LivingEntity target) {
        if (chance < 1.0f && entity.getRandom().nextFloat() >= chance) {
            return;
        }
        AbilitySupport.applyAll(target, effects);
    }
}
