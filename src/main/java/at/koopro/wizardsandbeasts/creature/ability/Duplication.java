package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;

/**
 * Matagot-style ability: on taking damage, a chance to split into 1-2 lower-health clones of itself.
 * Clones carry a short re-entrancy guard (own {@code chance} roll suppressed while the guard is active)
 * so a chain of hits can't spawn an unbounded clone tree in one fight; after the guard expires they're
 * ordinary, permanent, independently-killable copies rather than a despawn-on-timer construct — a
 * deliberate simplification over the design doc's "recombine after 30s," which would need a persistent
 * clone-identity flag the shared cooldown-map primitive isn't shaped for.
 */
public record Duplication(float chance, int minClones, int maxClones, float cloneHealthFraction, int reentryGuardTicks)
        implements CreatureAbility {

    private static final String GUARD_KEY = "duplication_guard";

    public static final MapCodec<Duplication> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.5f).forGetter(Duplication::chance),
            Codec.INT.optionalFieldOf("min_clones", 1).forGetter(Duplication::minClones),
            Codec.INT.optionalFieldOf("max_clones", 2).forGetter(Duplication::maxClones),
            Codec.floatRange(0f, 1f).optionalFieldOf("clone_health_fraction", 0.5f).forGetter(Duplication::cloneHealthFraction),
            Codec.INT.optionalFieldOf("reentry_guard_ticks", 200).forGetter(Duplication::reentryGuardTicks)
    ).apply(instance, Duplication::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.DUPLICATION;
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide() || entity.getCooldown(GUARD_KEY) > 0) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level) || entity.getRandom().nextFloat() >= chance) {
            return;
        }
        entity.setCooldown(GUARD_KEY, reentryGuardTicks);
        int span = Math.max(0, maxClones - minClones);
        int count = minClones + (span > 0 ? entity.getRandom().nextInt(span + 1) : 0);
        for (int i = 0; i < count; i++) {
            spawnClone(entity, level);
        }
    }

    private void spawnClone(GenericBeastEntity entity, ServerLevel level) {
        EntityType<?> type = entity.getType();
        var spawned = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(spawned instanceof GenericBeastEntity clone)) {
            return;
        }
        double angle = entity.getRandom().nextDouble() * Math.PI * 2;
        double dx = Math.cos(angle) * 1.2;
        double dz = Math.sin(angle) * 1.2;
        clone.setPos(entity.getX() + dx, entity.getY(), entity.getZ() + dz);
        clone.setYRot(entity.getYRot());
        clone.setHealth(Math.max(1.0f, clone.getMaxHealth() * cloneHealthFraction));
        clone.setCooldown(GUARD_KEY, reentryGuardTicks);
        level.addFreshEntity(clone);
    }
}
