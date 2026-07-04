package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: natural armour. The creature continuously carries Resistance at {@code amplifier}, modelling
 * thick hide / chitin / bulk without an attribute modifier (Graphorn, Erumpent, Giant, Troll). Refreshed each
 * second with a hidden, ambient-flagged effect so it reads as innate toughness rather than a buff.
 */
public record DamageReduction(int amplifier) implements CreatureAbility {

    public static final MapCodec<DamageReduction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(DamageReduction::amplifier)
    ).apply(instance, DamageReduction::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.DAMAGE_REDUCTION;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (entity.level().isClientSide() || entity.tickCount % 20 != 0) {
            return;
        }
        entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, amplifier, true, false));
    }
}
