package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: heals the creature by a flat amount each time it lands a melee hit (drains blood/magic).
 * The Chizpurfle/Kappa/Swooping-Evil kit.
 */
public record LifeLeech(float healOnHit) implements CreatureAbility {

    public static final MapCodec<LifeLeech> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("heal_on_hit", 2.0f).forGetter(LifeLeech::healOnHit)
    ).apply(instance, LifeLeech::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.LIFE_LEECH;
    }

    @Override
    public void onMeleeContact(@NonNull GenericBeastEntity entity, @NonNull LivingEntity target) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(healOnHit);
        }
        if (entity.level() instanceof ServerLevel level) {
            AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.SOUL, 4);
        }
    }
}
