package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: a cosmetic glow. Emits a chosen particle on an interval and, optionally, keeps the
 * Glowing effect on itself (visible outline). The Fairy/Leprechaun/Clabbert/Pygmy-Puff look. No dynamic
 * block-light (per scope).
 */
public record Bioluminescence(AbilitySupport.Particle particle, int interval, boolean glowSelf)
        implements CreatureAbility {

    public static final MapCodec<Bioluminescence> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            AbilitySupport.Particle.CODEC.optionalFieldOf("particle", AbilitySupport.Particle.GLOW).forGetter(Bioluminescence::particle),
            Codec.INT.optionalFieldOf("interval", 10).forGetter(Bioluminescence::interval),
            Codec.BOOL.optionalFieldOf("glow_self", false).forGetter(Bioluminescence::glowSelf)
    ).apply(instance, Bioluminescence::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.BIOLUMINESCENCE;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        int step = Math.max(1, interval);
        if (entity.tickCount % step == 0) {
            AbilitySupport.emitAtBody(level, entity, particle, 2);
        }
        if (glowSelf && entity.tickCount % 40 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false));
        }
    }
}
