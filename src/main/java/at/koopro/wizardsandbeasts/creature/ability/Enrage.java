package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: when the creature drops below {@code healthFraction} of max health, it flies into a rage —
 * gains Speed + Strength for {@code durationTicks} and puffs angry particles. Re-applied (refreshed) each
 * time it is hurt while still below the threshold.
 */
public record Enrage(float healthFraction, int speedAmplifier, int strengthAmplifier, int durationTicks)
        implements CreatureAbility {

    public static final MapCodec<Enrage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("health_fraction", 0.5f).forGetter(Enrage::healthFraction),
            Codec.INT.optionalFieldOf("speed_amplifier", 1).forGetter(Enrage::speedAmplifier),
            Codec.INT.optionalFieldOf("strength_amplifier", 1).forGetter(Enrage::strengthAmplifier),
            Codec.INT.optionalFieldOf("duration_ticks", 200).forGetter(Enrage::durationTicks)
    ).apply(instance, Enrage::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.ENRAGE;
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, @NonNull DamageSource source, float amount) {
        if (!entity.isAlive() || entity.getHealth() > entity.getMaxHealth() * healthFraction) {
            return;
        }
        entity.addEffect(new MobEffectInstance(MobEffects.SPEED, durationTicks, speedAmplifier));
        entity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, durationTicks, strengthAmplifier));
        if (entity.level() instanceof ServerLevel level) {
            AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.CRIT, 8);
        }
    }
}
