package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Lethifold): the smothering shroud. While a player is within close range in low light,
 * the Lethifold engulfs them — Darkness, Blindness and heavy Slowness, drains their air, and feeds on them
 * (heals itself). Canon: a black cloak that suffocates the sleeping. Strongest in the dark.
 */
public record LethifoldSmother(double range, float maxLightForHunt, float drainHeal) implements CreatureAbility {

    public static final MapCodec<LethifoldSmother> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 3.0).forGetter(LethifoldSmother::range),
            Codec.FLOAT.optionalFieldOf("max_light_for_hunt", 7.0f).forGetter(LethifoldSmother::maxLightForHunt),
            Codec.FLOAT.optionalFieldOf("drain_heal", 1.0f).forGetter(LethifoldSmother::drainHeal)
    ).apply(instance, LethifoldSmother::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.LETHIFOLD_SMOTHER;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 10 != 0) {
            return;
        }
        if (level.getMaxLocalRawBrightness(entity.blockPosition()) > maxLightForHunt) {
            return;
        }
        boolean fed = false;
        for (Player player : AbilitySupport.nearbyPlayers(entity, range)) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
            player.setAirSupply(Math.max(-20, player.getAirSupply() - 40));
            fed = true;
        }
        if (fed && entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(drainHeal);
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.SMOKE, 6);
    }
}
