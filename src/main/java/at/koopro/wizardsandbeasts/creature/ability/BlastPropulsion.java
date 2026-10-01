package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Blast-Ended Skrewt): the blast. Every so often its tail end goes off with a small phut and a spray
 * of sparks, and the blast shoves it several feet forward (Goblet of Fire). Whatever is behind it when it goes off is
 * burned — which is how a Skrewt hurts you, and why you do not walk behind one.
 *
 * <p>It blasts when it is chasing something, and now and then as it wanders. Server-side; the shove is ordinary
 * velocity, so the client sees the jump through normal movement sync.
 */
public record BlastPropulsion(int cooldownTicks, double lungeSpeed, double burnReach, int burnSeconds, float wanderChance)
        implements CreatureAbility {

    static final String COOLDOWN = "blast";

    public static final MapCodec<BlastPropulsion> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("cooldown_ticks", 60).forGetter(BlastPropulsion::cooldownTicks),
            Codec.DOUBLE.optionalFieldOf("lunge_speed", 0.7).forGetter(BlastPropulsion::lungeSpeed),
            Codec.DOUBLE.optionalFieldOf("burn_reach", 2.0).forGetter(BlastPropulsion::burnReach),
            Codec.INT.optionalFieldOf("burn_seconds", 3).forGetter(BlastPropulsion::burnSeconds),
            Codec.FLOAT.optionalFieldOf("wander_chance", 0.01f).forGetter(BlastPropulsion::wanderChance)
    ).apply(instance, BlastPropulsion::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.BLAST_PROPULSION;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel) || entity.getCooldown(COOLDOWN) > 0 || !entity.onGround()) {
            return;
        }
        boolean chasing = entity.getTarget() != null && entity.getTarget().isAlive();
        boolean wandering = entity.getDeltaMovement().horizontalDistanceSqr() > 1.0e-4
                && entity.getRandom().nextFloat() < wanderChance;
        if (chasing || wandering) {
            blast(entity);
        }
    }

    /**
     * Fires the blast: sparks and a phut from the tail, a shove forward, and fire on everything behind it.
     *
     * @return how many things behind it were set alight
     */
    public int blast(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return 0;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0f, entity.getYRot());
        Vec3 tail = entity.position().add(0, entity.getBbHeight() * 0.4, 0)
                .subtract(forward.scale(entity.getBbWidth() * 0.5 + 0.2));
        entity.setDeltaMovement(entity.getDeltaMovement().add(forward.scale(lungeSpeed)).add(0, 0.2, 0));
        entity.hurtMarked = true;
        level.playSound(null, tail.x, tail.y, tail.z, ModSounds.SKREWT_BLAST.get(), SoundSource.HOSTILE, 0.8f, 1.0f);
        AbilitySupport.emitAt(level, AbilitySupport.Particle.FLAME, tail.x, tail.y, tail.z, 10, 0.15, 0.04);
        AbilitySupport.emitAt(level, AbilitySupport.Particle.SMOKE, tail.x, tail.y, tail.z, 6, 0.15, 0.02);

        int burned = 0;
        AABB behind = new AABB(tail, tail).inflate(burnReach);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, behind,
                e -> e != entity && e.isAlive() && !e.fireImmune() && !e.isSpectator())) {
            Vec3 toVictim = victim.position().subtract(entity.position());
            if (toVictim.horizontalDistanceSqr() < 1.0e-6 || toVictim.normalize().dot(forward) < 0.0) {
                victim.igniteForSeconds(burnSeconds);
                burned++;
            }
        }
        entity.setCooldown(COOLDOWN, cooldownTicks + entity.getRandom().nextInt(Math.max(1, cooldownTicks / 2)));
        BestiaryDiscoveryHandler.signatureSeenByNearby(entity, 16.0);
        return burned;
    }
}
