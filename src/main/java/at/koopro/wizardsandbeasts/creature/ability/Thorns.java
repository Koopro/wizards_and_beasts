package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Common ability: a melee attacker takes a fraction of the damage it dealt back (spines/quills/mucus) and,
 * optionally, a status effect. Only reacts to a living direct attacker (not fall/fire/etc.).
 */
public record Thorns(float reflectFraction, Optional<AbilitySupport.EffectSpec> attackerEffect)
        implements CreatureAbility {

    public static final MapCodec<Thorns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("reflect_fraction", 0.5f).forGetter(Thorns::reflectFraction),
            AbilitySupport.EffectSpec.CODEC.optionalFieldOf("attacker_effect").forGetter(Thorns::attackerEffect)
    ).apply(instance, Thorns::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.THORNS;
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, @NonNull DamageSource source, float amount) {
        LivingEntity attacker = AbilitySupport.meleeAttacker(source.getDirectEntity());
        if (attacker == null || attacker == entity || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (reflectFraction > 0 && amount > 0) {
            attacker.hurtServer(level, level.damageSources().thorns(entity), amount * reflectFraction);
        }
        attackerEffect.ifPresent(spec -> spec.applyTo(attacker));
    }
}
