package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: pack tactics. The more kin of the same kind hunt nearby, the bolder this creature fights —
 * it gains Strength scaling with the number of same-type allies in {@code radius}, capped at {@code maxAmplifier}.
 * The Werewolf/Quintaped/Acromantula swarm kit. Refreshed periodically (cheap, no per-tick scan); a lone
 * creature gets nothing.
 */
public record PackTactics(double radius, int maxAmplifier) implements CreatureAbility {

    public static final MapCodec<PackTactics> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(PackTactics::radius),
            Codec.INT.optionalFieldOf("max_amplifier", 2).forGetter(PackTactics::maxAmplifier)
    ).apply(instance, PackTactics::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.PACK_TACTICS;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (entity.level().isClientSide() || entity.tickCount % 20 != 0) {
            return;
        }
        int allies = 0;
        for (LivingEntity other : AbilitySupport.nearbyLiving(entity, radius)) {
            if (other.getType() == entity.getType()) {
                allies++;
            }
        }
        if (allies <= 0) {
            return;
        }
        int amplifier = Math.min(maxAmplifier, allies - 1);
        // Short, refreshed duration so the buff fades quickly once the pack scatters.
        entity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, amplifier, true, false));
    }
}
