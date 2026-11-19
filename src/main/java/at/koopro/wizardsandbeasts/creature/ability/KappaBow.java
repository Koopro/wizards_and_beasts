package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.creature.schedule.AiCadence;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

/**
 * Kappa: bow to it and it bows back, and the water spills from the hollow in its head, leaving it weak
 * (<i>Fantastic Beasts and Where to Find Them</i>). A player crouching while holding its gaze within
 * {@code range} is bowing; the Kappa answers, loses its strength for {@code weak_ticks} and stops hunting
 * that player. It cannot be made to bow again while still drained.
 */
public record KappaBow(double range, int weakTicks) implements CreatureAbility {

    public static final MapCodec<KappaBow> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 5.0).forGetter(KappaBow::range),
            Codec.INT.optionalFieldOf("weak_ticks", 400).forGetter(KappaBow::weakTicks)
    ).apply(instance, KappaBow::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.KAPPA_BOW;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !AiCadence.every(entity, 10)
                || entity.hasEffect(MobEffects.WEAKNESS)) {
            return;
        }
        for (Player player : AbilitySupport.nearbyPlayers(entity, range)) {
            if (bowing(player, entity)) {
                spill(level, entity, player);
                return;
            }
        }
    }

    /** Crouching, and holding the creature's gaze. */
    public static boolean bowing(Player player, GenericBeastEntity entity) {
        return player.isShiftKeyDown() && WildlifeRules.looksAt(player.getLookAngle(),
                entity.getEyePosition().subtract(player.getEyePosition()));
    }

    private void spill(ServerLevel level, GenericBeastEntity entity, Player player) {
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weakTicks, 2));
        entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, weakTicks, 1));
        if (entity.getTarget() == player) {
            entity.setTarget(null);
        }
        level.sendParticles(ParticleTypes.SPLASH, entity.getX(), entity.getEyeY() + 0.2, entity.getZ(),
                30, 0.25, 0.1, 0.25, 0.2);
        level.playSound(null, entity.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }
}
