package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Ramora): the silver fish that anchors ships and holds them fast. Once per second every
 * nearby living creature that is also in water is pinned with heavy Slowness — it cannot be dragged away
 * while the Ramora holds. A guardian of sailors, so it grips rather than harms.
 */
public record Anchor(double radius, int slowAmplifier) implements CreatureAbility {

    public static final MapCodec<Anchor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 6.0).forGetter(Anchor::radius),
            Codec.INT.optionalFieldOf("slow_amplifier", 4).forGetter(Anchor::slowAmplifier)
    ).apply(instance, Anchor::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.ANCHOR;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 20 != 0 || !entity.isInWater()) {
            return;
        }
        for (LivingEntity target : AbilitySupport.nearbyLiving(entity, radius)) {
            if (target.isInWater()) {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, slowAmplifier));
            }
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.BUBBLE, 4);
    }
}
