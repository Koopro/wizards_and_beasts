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
 * Signature ability (Fwooper): a song that drives the listener to madness. Each second a nearby player is
 * in earshot, the maddening effects escalate — Nausea, then Weakness, then Mining Fatigue, then a touch of
 * magic damage — driven by how long they have lingered (tracked on the player's own effect duration as a
 * proxy). Canon: prolonged exposure to a Fwooper's song drives the listener insane.
 */
public record FwooperSong(double radius, int durationTicks) implements CreatureAbility {

    public static final MapCodec<FwooperSong> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(FwooperSong::radius),
            Codec.INT.optionalFieldOf("duration_ticks", 100).forGetter(FwooperSong::durationTicks)
    ).apply(instance, FwooperSong::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.FWOOPER_SONG;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 20 != 0) {
            return;
        }
        for (Player player : AbilitySupport.nearbyPlayers(entity, radius)) {
            // Escalate by how deep the existing nausea stack already is (proxy for "time listening").
            int stage = player.getEffect(MobEffects.NAUSEA) != null
                    ? Math.min(3, player.getEffect(MobEffects.NAUSEA).getDuration() / durationTicks)
                    : 0;
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, durationTicks * (stage + 2), 0));
            if (stage >= 1) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, 0));
            }
            if (stage >= 2) {
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, durationTicks, 1));
            }
            if (stage >= 3) {
                player.hurtServer(level, level.damageSources().magic(), 1.0f);
            }
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.ENCHANT, 5);
    }
}
