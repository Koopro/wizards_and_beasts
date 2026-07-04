package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.SeekFireGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * First concrete {@link CreatureAbility}: a fire-dwelling kit. All fields are defaulted so it can be
 * declared with only the flags a creature needs; an absent {@code abilities} list leaves existing
 * creature JSON parsing unchanged.
 *
 * <p>Per-entity state (the dry-out timer) is <b>not</b> stored on this record — the record is the shared
 * definition value, identical across every entity that loads it. The counter lives on the entity and is
 * persisted through the existing {@code addAdditionalSaveData}/{@code readAdditionalSaveData} pattern
 * ({@link GenericBeastEntity#getFireDryTicks()} / {@link GenericBeastEntity#setFireDryTicks(int)}).
 *
 * @param fireImmune           immune to fire &amp; lava damage (OR-ed into {@code GenericBeastEntity.fireImmune()})
 * @param requiresFire         take {@code dryDamage} once out of fire/lava longer than the grace window
 * @param dryGraceTicks        ticks out of fire before dry damage begins
 * @param dryDamage            damage applied per second once past the grace window (bypasses fire immunity — DRY_OUT)
 * @param regenInFire          health/sec healed while in fire/lava ({@code 0} = off)
 * @param seekFireWhenDry      register an AI goal to path toward the nearest fire/lava once getting dry
 * @param igniteMeleeAttackers set a melee attacker on fire when it hits this creature
 * @param emberParticles       emit flame/ash particles at the body FX anchor (server-spawned)
 */
public record FireAffinity(
        boolean fireImmune,
        boolean requiresFire,
        int dryGraceTicks,
        float dryDamage,
        float regenInFire,
        boolean seekFireWhenDry,
        boolean igniteMeleeAttackers,
        boolean emberParticles
) implements CreatureAbility {

    /** Ignite duration (seconds) applied to a melee attacker when {@link #igniteMeleeAttackers} is set. */
    private static final int ATTACKER_BURN_SECONDS = 5;
    /** Embers are emitted on this server-tick cadence when {@link #emberParticles} is set. */
    private static final int EMBER_INTERVAL_TICKS = 6;
    /** Goal priority for the dry-seeking pathfind (sits alongside wander/look goals). */
    private static final int SEEK_FIRE_GOAL_PRIORITY = 6;

    public static final MapCodec<FireAffinity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("fire_immune", false).forGetter(FireAffinity::fireImmune),
            Codec.BOOL.optionalFieldOf("requires_fire", false).forGetter(FireAffinity::requiresFire),
            Codec.INT.optionalFieldOf("dry_grace_ticks", 200).forGetter(FireAffinity::dryGraceTicks),
            Codec.FLOAT.optionalFieldOf("dry_damage", 1.0f).forGetter(FireAffinity::dryDamage),
            Codec.FLOAT.optionalFieldOf("regen_in_fire", 0.0f).forGetter(FireAffinity::regenInFire),
            Codec.BOOL.optionalFieldOf("seek_fire_when_dry", false).forGetter(FireAffinity::seekFireWhenDry),
            Codec.BOOL.optionalFieldOf("ignite_melee_attackers", false).forGetter(FireAffinity::igniteMeleeAttackers),
            Codec.BOOL.optionalFieldOf("ember_particles", false).forGetter(FireAffinity::emberParticles)
    ).apply(instance, FireAffinity::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.FIRE_AFFINITY;
    }

    /** True when the entity is standing in lava or a fire block. Shared with {@link SeekFireGoal}. */
    public static boolean isInFireOrLava(@NonNull GenericBeastEntity entity) {
        if (entity.isInLava()) {
            return true;
        }
        Level level = entity.level();
        BlockPos feet = entity.blockPosition();
        return level.getBlockState(feet).is(BlockTags.FIRE)
                || level.getBlockState(feet.above()).is(BlockTags.FIRE);
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        boolean inFire = isInFireOrLava(entity);
        if (inFire) {
            entity.setFireDryTicks(0);
            if (regenInFire > 0 && entity.tickCount % 20 == 0 && entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(regenInFire);
            }
        } else if (requiresFire) {
            int dry = entity.getFireDryTicks() + 1;
            entity.setFireDryTicks(dry);
            // DRY_OUT is not a fire-typed source, so it bypasses this creature's own fire immunity.
            if (dry > dryGraceTicks && dry % 20 == 0) {
                entity.hurtServer(level, level.damageSources().dryOut(), dryDamage);
            }
        }
        if (emberParticles && entity.tickCount % EMBER_INTERVAL_TICKS == 0) {
            emitEmbers(level, entity);
        }
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, @NonNull DamageSource source, float amount) {
        if (!igniteMeleeAttackers) {
            return;
        }
        if (source.getDirectEntity() instanceof LivingEntity attacker && attacker.isAlive()) {
            attacker.igniteForSeconds(ATTACKER_BURN_SECONDS);
        }
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        if (seekFireWhenDry) {
            goalSelector.addGoal(SEEK_FIRE_GOAL_PRIORITY, new SeekFireGoal(entity, this, 1.0, 12));
        }
    }

    /** Server-side flame + ash emission at the body anchor (entity centre). No dynamic block-light. */
    private static void emitEmbers(@NonNull ServerLevel level, @NonNull GenericBeastEntity entity) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();
        double spread = entity.getBbWidth() * 0.5;
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 2, spread, spread * 0.5, spread, 0.0);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 1, spread, spread, spread, 0.0);
    }
}
