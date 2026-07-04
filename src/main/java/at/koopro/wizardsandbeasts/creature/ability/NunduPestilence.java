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
 * Signature ability (Nundu): a breath of pestilence. On a cooldown, while a player is within range, the
 * Nundu exhales a disease cloud — Poison + Wither + Hunger on everyone in radius, with a lingering soul-ash
 * haze. Canon: a Nundu's breath could wipe out a village. Self-gated on the module via the shared tick.
 */
public record NunduPestilence(double radius, int cooldownTicks, int poisonAmplifier, int durationTicks)
        implements CreatureAbility {

    private static final String COOLDOWN_KEY = "nundu";

    public static final MapCodec<NunduPestilence> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 7.0).forGetter(NunduPestilence::radius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 120).forGetter(NunduPestilence::cooldownTicks),
            Codec.INT.optionalFieldOf("poison_amplifier", 1).forGetter(NunduPestilence::poisonAmplifier),
            Codec.INT.optionalFieldOf("duration_ticks", 140).forGetter(NunduPestilence::durationTicks)
    ).apply(instance, NunduPestilence::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.NUNDU_PESTILENCE;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.getCooldown(COOLDOWN_KEY) > 0) {
            return;
        }
        var players = AbilitySupport.nearbyPlayers(entity, radius);
        if (players.isEmpty()) {
            return;
        }
        entity.setCooldown(COOLDOWN_KEY, cooldownTicks);
        for (Player player : players) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, durationTicks, poisonAmplifier));
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, durationTicks, 0));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, durationTicks, 1));
        }
        // Lingering haze around the Nundu's maw.
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.6;
        double z = entity.getZ();
        AbilitySupport.emitAt(level, AbilitySupport.Particle.SOUL, x, y, z, 30, radius * 0.4, 0.02);
        AbilitySupport.emitAt(level, AbilitySupport.Particle.WITCH, x, y, z, 20, radius * 0.4, 0.02);
    }
}
