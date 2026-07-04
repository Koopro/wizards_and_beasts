package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Jobberknoll): silent its whole life, then a single long scream on death that replays
 * every sound it ever heard. Modelled as a death burst — a loud scream + nearby living creatures briefly
 * outlined (Glowing), as the cry lays bare what the bird witnessed.
 */
public record DeathCry(double radius) implements CreatureAbility {

    public static final MapCodec<DeathCry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 10.0).forGetter(DeathCry::radius)
    ).apply(instance, DeathCry::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.DEATH_CRY;
    }

    @Override
    public void onDeath(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GOAT_SCREAMING_DEATH, SoundSource.HOSTILE, 1.5f, 0.8f);
        for (LivingEntity nearby : AbilitySupport.nearbyLiving(entity, radius)) {
            nearby.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.ENCHANT, 24);
    }
}
