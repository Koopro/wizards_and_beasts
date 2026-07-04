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
 * Signature ability (Boggart): becomes your fear. The Boggart stays Invisible until a player is close and
 * looking at it; then it reveals and unleashes a fear bundle — Darkness, Slowness, Weakness and Nausea on
 * everyone nearby. Canon: a shape-shifter that has no true form, only the form of what frightens you most.
 */
public record BoggartDread(double range, int fearDurationTicks) implements CreatureAbility {

    public static final MapCodec<BoggartDread> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 6.0).forGetter(BoggartDread::range),
            Codec.INT.optionalFieldOf("fear_duration_ticks", 120).forGetter(BoggartDread::fearDurationTicks)
    ).apply(instance, BoggartDread::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.BOGGART_DREAD;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 10 != 0) {
            return;
        }
        boolean watched = false;
        for (Player player : AbilitySupport.nearbyPlayers(entity, range)) {
            // "Looking at" approximated by the player facing the boggart within a narrow cone.
            if (isLookingAt(player, entity)) {
                watched = true;
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, fearDurationTicks, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, fearDurationTicks, 1));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, fearDurationTicks, 1));
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, fearDurationTicks, 0));
            }
        }
        if (watched) {
            entity.removeEffect(MobEffects.INVISIBILITY);
            AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.SMOKE, 10);
        } else {
            entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, true, false));
        }
    }

    private static boolean isLookingAt(Player player, GenericBeastEntity target) {
        var look = player.getViewVector(1.0f).normalize();
        var toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0)
                .subtract(player.getEyePosition()).normalize();
        return look.dot(toTarget) > 0.6;
    }
}
