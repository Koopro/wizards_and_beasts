package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: an evasive blink. When hurt below {@code triggerHealthFraction} (and off cooldown), the
 * creature teleports a short random distance and leaves a portal puff — the Diricawl/Demiguise/Zouwu escape.
 * Cooldown is tracked on the entity's shared ability-cooldown counter.
 */
public record BlinkAway(float triggerHealthFraction, double range, int cooldownTicks) implements CreatureAbility {

    /** Private cooldown key so blink never shares a timer with another ability on the same beast. */
    private static final String COOLDOWN_KEY = "blink";

    public static final MapCodec<BlinkAway> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("trigger_health_fraction", 1.0f).forGetter(BlinkAway::triggerHealthFraction),
            Codec.DOUBLE.optionalFieldOf("range", 8.0).forGetter(BlinkAway::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 100).forGetter(BlinkAway::cooldownTicks)
    ).apply(instance, BlinkAway::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.BLINK_AWAY;
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, @NonNull DamageSource source, float amount) {
        if (!entity.isAlive() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (entity.getHealth() > entity.getMaxHealth() * triggerHealthFraction) {
            return;
        }
        if (entity.getCooldown(COOLDOWN_KEY) > 0) {
            return;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            double dx = (entity.getRandom().nextDouble() - 0.5) * 2.0 * range;
            double dz = (entity.getRandom().nextDouble() - 0.5) * 2.0 * range;
            double tx = entity.getX() + dx;
            double tz = entity.getZ() + dz;
            double ty = entity.getY();
            if (entity.randomTeleport(tx, ty, tz, true)) {
                AbilitySupport.emitAt(level, AbilitySupport.Particle.PORTAL,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 24, 0.4, 0.2);
                entity.setCooldown(COOLDOWN_KEY, cooldownTicks);
                return;
            }
        }
    }
}
