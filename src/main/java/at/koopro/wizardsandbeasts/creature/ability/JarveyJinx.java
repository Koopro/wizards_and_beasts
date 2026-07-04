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
 * Signature ability (Jarvey): the foul-mouthed ferret hurls a jinx along with its insults. While it has a
 * target, on a cooldown it barks a rude "word" (sound cue) and douses nearby players in Unluck + Weakness,
 * trailing witch particles. Pairs with the Jarvey's {@code THIEF} trait — bad luck while it robs you blind.
 */
public record JarveyJinx(double radius, int cooldownTicks, int durationTicks) implements CreatureAbility {

    private static final String COOLDOWN_KEY = "jarvey";

    public static final MapCodec<JarveyJinx> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(JarveyJinx::radius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 160).forGetter(JarveyJinx::cooldownTicks),
            Codec.INT.optionalFieldOf("duration_ticks", 200).forGetter(JarveyJinx::durationTicks)
    ).apply(instance, JarveyJinx::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.JARVEY_JINX;
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
            player.addEffect(new MobEffectInstance(MobEffects.UNLUCK, durationTicks, 0));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, 0));
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.WITCH, 12);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.FOX_SCREECH, SoundSource.HOSTILE, 1.0f, 1.3f);
    }
}
