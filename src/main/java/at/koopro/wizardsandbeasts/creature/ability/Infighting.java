package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Crowded together, these creatures kill each other. Hagrid's Skrewts did — by the end of the year there were only ten
 * left, the rest killed by their own kind (Goblet of Fire). When {@link SignatureRules#SKREWT_CROWD} or more of the
 * same species are within {@code radius}, an idle one picks the nearest and goes for it.
 *
 * <p>A pair tolerates each other. Nothing is fought while the creature already has a target.
 */
public record Infighting(double radius, int checkIntervalTicks) implements CreatureAbility {

    public static final MapCodec<Infighting> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 4.0).forGetter(Infighting::radius),
            Codec.INT.optionalFieldOf("check_interval_ticks", 40).forGetter(Infighting::checkIntervalTicks)
    ).apply(instance, Infighting::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.INFIGHTING;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (entity.tickCount % Math.max(1, checkIntervalTicks) != 0 || entity.getTarget() != null) {
            return;
        }
        GenericBeastEntity rival = rival(entity);
        if (rival != null) {
            entity.setTarget(rival);
        }
    }

    /** The kin it turns on, when crowded: the nearest of its species within {@code radius}, or {@code null}. */
    public @Nullable GenericBeastEntity rival(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return null;
        }
        List<GenericBeastEntity> kin = level.getEntitiesOfClass(GenericBeastEntity.class,
                entity.getBoundingBox().inflate(radius),
                other -> other != entity && other.isAlive() && other.creatureId().equals(entity.creatureId()));
        if (!SignatureRules.skrewtsFight(kin.size() + 1)) {
            return null;
        }
        return kin.stream().min(Comparator.comparingDouble(entity::distanceToSqr)).orElse(null);
    }
}
