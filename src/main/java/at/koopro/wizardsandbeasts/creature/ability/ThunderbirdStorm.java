package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Thunderbird): senses danger and answers with a storm. When the Thunderbird has a
 * hostile target, on a cooldown it whips up thunder/rain and calls a lightning bolt down on the target.
 * Canon: a Thunderbird can sense danger and create storms as it flies.
 */
public record ThunderbirdStorm(double range, int cooldownTicks, int stormDurationTicks) implements CreatureAbility {

    private static final String COOLDOWN_KEY = "thunderbird";

    public static final MapCodec<ThunderbirdStorm> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 20.0).forGetter(ThunderbirdStorm::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 160).forGetter(ThunderbirdStorm::cooldownTicks),
            Codec.INT.optionalFieldOf("storm_duration_ticks", 600).forGetter(ThunderbirdStorm::stormDurationTicks)
    ).apply(instance, ThunderbirdStorm::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.THUNDERBIRD_STORM;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.getCooldown(COOLDOWN_KEY) > 0) {
            return;
        }
        LivingEntity target = entity.getTarget();
        if (target == null || !target.isAlive() || entity.distanceToSqr(target) > range * range) {
            return;
        }
        entity.setCooldown(COOLDOWN_KEY, cooldownTicks);
        level.setWeatherParameters(0, stormDurationTicks, true, true);
        strikeLightning(level, target.position());
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.ELECTRIC, 16);
    }

    private static void strikeLightning(ServerLevel level, Vec3 pos) {
        Entity bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt instanceof LightningBolt lightning) {
            lightning.setPos(pos.x, pos.y, pos.z);
            level.addFreshEntity(lightning);
        }
    }
}
