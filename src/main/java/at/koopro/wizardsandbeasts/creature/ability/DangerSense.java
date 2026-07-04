package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.NonNull;

/**
 * Common ability (Kneazle): a sixth sense for danger. Periodically outlines nearby hostile creatures with
 * Glowing, so the trusted companion betrays lurking threats. The Kneazle's canon ability to sense the
 * untrustworthy/dangerous, rendered as a threat highlight.
 */
public record DangerSense(double radius) implements CreatureAbility {

    public static final MapCodec<DangerSense> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 12.0).forGetter(DangerSense::radius)
    ).apply(instance, DangerSense::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.DANGER_SENSE;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (entity.level().isClientSide() || entity.tickCount % 30 != 0) {
            return;
        }
        for (LivingEntity nearby : AbilitySupport.nearbyLiving(entity, radius)) {
            if (nearby instanceof Enemy) {
                nearby.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
            }
        }
    }
}
