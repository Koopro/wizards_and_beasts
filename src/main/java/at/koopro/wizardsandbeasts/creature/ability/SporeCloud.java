package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Common ability: a passive spore/miasma cloud. On a fixed interval the creature puffs out particles and dusts
 * any player within {@code radius} with its {@code effects} — a lighter, always-on cousin of
 * {@code nundu_pestilence} for fungal/rotting beasts (Horklump, Flobberworm, Glumbumble). No cooldown gate;
 * the interval is the throttle.
 */
public record SporeCloud(int interval, double radius, List<AbilitySupport.EffectSpec> effects,
                         AbilitySupport.Particle particle) implements CreatureAbility {

    public static final MapCodec<SporeCloud> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("interval", 60).forGetter(SporeCloud::interval),
            Codec.DOUBLE.optionalFieldOf("radius", 4.0).forGetter(SporeCloud::radius),
            AbilitySupport.EffectSpec.LIST_CODEC.optionalFieldOf("effects", List.of()).forGetter(SporeCloud::effects),
            AbilitySupport.Particle.CODEC.optionalFieldOf("particle", AbilitySupport.Particle.SPORE).forGetter(SporeCloud::particle)
    ).apply(instance, SporeCloud::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.SPORE_CLOUD;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        int period = Math.max(1, interval);
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % period != 0) {
            return;
        }
        AbilitySupport.emitAt(level, particle,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                12, radius * 0.5, 0.02);
        if (effects.isEmpty()) {
            return;
        }
        List<Player> players = AbilitySupport.nearbyPlayers(entity, radius);
        for (Player player : players) {
            AbilitySupport.applyAll(player, effects);
        }
    }
}
