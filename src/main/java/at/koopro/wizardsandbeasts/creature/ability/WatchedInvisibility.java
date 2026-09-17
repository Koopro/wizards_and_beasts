package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NonNull;

/**
 * Invisible while it is being looked at. The Demiguise "can make itself invisible" when threatened or watched, so the
 * experience is a glimpse at the edge of vision that is gone when you turn to it — which is what makes it something
 * to find rather than something to shoot.
 */
public record WatchedInvisibility(double range) implements CreatureAbility {

    private static final int CHECK_TICKS = 4;

    public static final MapCodec<WatchedInvisibility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 24.0).forGetter(WatchedInvisibility::range)
    ).apply(instance, WatchedInvisibility::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.WATCHED_INVISIBILITY;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % CHECK_TICKS != 0) {
            return;
        }
        if (watched(entity, level)) {
            entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, CHECK_TICKS * 3, 0, false, false, false));
        } else if (entity.hasEffect(MobEffects.INVISIBILITY)) {
            entity.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    /** Whether any player within range, in sight of it, is looking straight at it. */
    public boolean watched(GenericBeastEntity entity, ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(entity) > range * range) {
                continue;
            }
            if (player.hasLineOfSight(entity) && WildlifeRules.looksAt(player.getViewVector(1.0f),
                    entity.getEyePosition().subtract(player.getEyePosition()))) {
                return true;
            }
        }
        return false;
    }
}
