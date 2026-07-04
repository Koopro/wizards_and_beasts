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
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Signature ability (Sphinx): it poses a riddle no intruder can answer. While guarding a target, on a cooldown
 * it binds nearby players in Blindness + Nausea — the disorientation of a mind caught in the puzzle — with a
 * low resonant cue and enchant particles. Pairs with the Sphinx's existing dread/heal kit as the guardian's
 * opening move.
 */
public record SphinxRiddle(double radius, int cooldownTicks, int durationTicks) implements CreatureAbility {

    private static final String COOLDOWN_KEY = "riddle";

    public static final MapCodec<SphinxRiddle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(SphinxRiddle::radius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 200).forGetter(SphinxRiddle::cooldownTicks),
            Codec.INT.optionalFieldOf("duration_ticks", 120).forGetter(SphinxRiddle::durationTicks)
    ).apply(instance, SphinxRiddle::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.SPHINX_RIDDLE;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.getCooldown(COOLDOWN_KEY) > 0) {
            return;
        }
        if (entity.getTarget() == null) {
            return;
        }
        List<Player> players = AbilitySupport.nearbyPlayers(entity, radius);
        if (players.isEmpty()) {
            return;
        }
        entity.setCooldown(COOLDOWN_KEY, cooldownTicks);
        for (Player player : players) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, durationTicks, 0));
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, durationTicks, 0));
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.ENCHANT, 16);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.HOSTILE, 1.0f, 0.7f);
    }
}
