package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: a restorative aura. Once per second heals the creature itself and, optionally, nearby
 * living allies; can also cleanse its own negative effects. The Unicorn/Qilin/Murtlap kit.
 */
public record HealAura(float healPerSecond, double radius, boolean healAllies, boolean cleanseSelf)
        implements CreatureAbility {

    public static final MapCodec<HealAura> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("heal_per_second", 1.0f).forGetter(HealAura::healPerSecond),
            Codec.DOUBLE.optionalFieldOf("radius", 6.0).forGetter(HealAura::radius),
            Codec.BOOL.optionalFieldOf("heal_allies", false).forGetter(HealAura::healAllies),
            Codec.BOOL.optionalFieldOf("cleanse_self", false).forGetter(HealAura::cleanseSelf)
    ).apply(instance, HealAura::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.HEAL_AURA;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 20 != 0) {
            return;
        }
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(healPerSecond);
        }
        if (cleanseSelf && !entity.getActiveEffects().isEmpty()) {
            entity.getActiveEffects().stream()
                    .filter(e -> !e.getEffect().value().isBeneficial())
                    .map(e -> e.getEffect())
                    .toList()
                    .forEach(entity::removeEffect);
        }
        if (healAllies) {
            for (LivingEntity ally : AbilitySupport.nearbyLiving(entity, radius)) {
                if (ally.getType() == entity.getType() && ally.getHealth() < ally.getMaxHealth()) {
                    ally.heal(healPerSecond);
                }
            }
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.HAPPY, 3);
    }
}
