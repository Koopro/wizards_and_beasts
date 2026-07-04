package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.SeekWaterGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: the aquatic counterpart of {@link FireAffinity}. While submerged the creature keeps its
 * air topped up ({@code breatheUnderwater}) and regenerates ({@code regenInWater}); out of water it can be
 * slowed ({@code slowOnLand}) and, for truly aquatic beasts, suffers dry-out damage past a grace window and
 * paths back toward water ({@code seekWaterWhenDry}). Per-entity dry timer lives on the entity
 * ({@code WaterDryTicks}).
 */
public record WaterAffinity(
        boolean breatheUnderwater,
        float regenInWater,
        boolean requiresWater,
        int dryGraceTicks,
        float dryDamage,
        boolean seekWaterWhenDry,
        boolean slowOnLand,
        boolean bubbleParticles
) implements CreatureAbility {

    private static final int SEEK_WATER_GOAL_PRIORITY = 6;

    public static final MapCodec<WaterAffinity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("breathe_underwater", true).forGetter(WaterAffinity::breatheUnderwater),
            Codec.FLOAT.optionalFieldOf("regen_in_water", 0.0f).forGetter(WaterAffinity::regenInWater),
            Codec.BOOL.optionalFieldOf("requires_water", false).forGetter(WaterAffinity::requiresWater),
            Codec.INT.optionalFieldOf("dry_grace_ticks", 200).forGetter(WaterAffinity::dryGraceTicks),
            Codec.FLOAT.optionalFieldOf("dry_damage", 1.0f).forGetter(WaterAffinity::dryDamage),
            Codec.BOOL.optionalFieldOf("seek_water_when_dry", false).forGetter(WaterAffinity::seekWaterWhenDry),
            Codec.BOOL.optionalFieldOf("slow_on_land", false).forGetter(WaterAffinity::slowOnLand),
            Codec.BOOL.optionalFieldOf("bubble_particles", false).forGetter(WaterAffinity::bubbleParticles)
    ).apply(instance, WaterAffinity::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.WATER_AFFINITY;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        boolean inWater = entity.isInWater();
        if (inWater) {
            entity.setWaterDryTicks(0);
            if (breatheUnderwater) {
                entity.setAirSupply(entity.getMaxAirSupply());
            }
            if (regenInWater > 0 && entity.tickCount % 20 == 0 && entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(regenInWater);
            }
            if (bubbleParticles && entity.tickCount % 8 == 0) {
                AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.BUBBLE, 2);
            }
        } else {
            if (slowOnLand && entity.tickCount % 40 == 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, true, false));
            }
            if (requiresWater) {
                int dry = entity.getWaterDryTicks() + 1;
                entity.setWaterDryTicks(dry);
                if (dry > dryGraceTicks && dry % 20 == 0) {
                    entity.hurtServer(level, level.damageSources().dryOut(), dryDamage);
                }
            }
        }
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        if (seekWaterWhenDry) {
            goalSelector.addGoal(SEEK_WATER_GOAL_PRIORITY, new SeekWaterGoal(entity, this, 1.0, 16));
        }
    }
}
