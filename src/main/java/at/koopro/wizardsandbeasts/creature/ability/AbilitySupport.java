package at.koopro.wizardsandbeasts.creature.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Shared helpers for {@link CreatureAbility} implementations: a reusable {@link EffectSpec} (a codecable
 * status-effect payload), registry-safe effect resolution, AoE player gathering, and a small particle
 * palette. Keeps the individual ability records thin and consistent.
 */
public final class AbilitySupport {

    private AbilitySupport() {
    }

    /**
     * A single status effect to apply, declared in JSON. Reused by {@code status_on_hit}, {@code dread_aura},
     * {@code enrage} (self), {@code thorns} (attacker), and the signature abilities.
     *
     * @param effect        effect id (default {@code minecraft:poison})
     * @param amplifier     amplifier, 0 = level I
     * @param durationTicks duration in ticks
     */
    public record EffectSpec(@NonNull Identifier effect, int amplifier, int durationTicks) {
        public static final Codec<EffectSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("effect", Identifier.fromNamespaceAndPath("minecraft", "poison"))
                        .forGetter(EffectSpec::effect),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(EffectSpec::amplifier),
                Codec.INT.optionalFieldOf("duration_ticks", 120).forGetter(EffectSpec::durationTicks)
        ).apply(instance, EffectSpec::new));

        public static final Codec<List<EffectSpec>> LIST_CODEC = CODEC.listOf();

        /** Apply this effect to {@code target}, no-op if the effect id is unknown. */
        public void applyTo(@NonNull LivingEntity target) {
            Holder<MobEffect> holder = resolveEffect(effect);
            if (holder != null) {
                target.addEffect(new MobEffectInstance(holder, durationTicks, amplifier));
            }
        }
    }

    /** Resolve an effect id to its registry holder, or {@code null} if absent. */
    @Nullable
    public static Holder<MobEffect> resolveEffect(@NonNull Identifier id) {
        ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, id);
        return BuiltInRegistries.MOB_EFFECT.get(key).orElse(null);
    }

    /** Apply every spec in the list to the target. */
    public static void applyAll(@NonNull LivingEntity target, @NonNull List<EffectSpec> specs) {
        for (EffectSpec spec : specs) {
            spec.applyTo(target);
        }
    }

    /** Living players within {@code radius} of the entity (server-side AoE source). */
    public static List<Player> nearbyPlayers(@NonNull Mob source, double radius) {
        AABB box = source.getBoundingBox().inflate(radius);
        return source.level().getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && source.distanceToSqr(p) <= radius * radius);
    }

    /** Living non-self entities within {@code radius} (for heal auras / leech targeting). */
    public static List<LivingEntity> nearbyLiving(@NonNull Mob source, double radius) {
        AABB box = source.getBoundingBox().inflate(radius);
        return source.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != source && e.isAlive() && source.distanceToSqr(e) <= radius * radius);
    }

    /** True when the direct dealer of this damage is a living entity (a melee attacker). */
    @Nullable
    public static LivingEntity meleeAttacker(@Nullable Entity directEntity) {
        return directEntity instanceof LivingEntity living ? living : null;
    }

    /**
     * Named particle palette so abilities can pick a look from JSON without exposing raw registry ids. The
     * concrete {@link ParticleOptions} is resolved lazily through a {@link Supplier} so the enum can be
     * class-loaded (and its {@link #CODEC} used) without bootstrapping the particle registry — codec parsing
     * never touches {@code ParticleTypes}; only server-side emission does.
     */
    public enum Particle {
        FLAME(() -> ParticleTypes.FLAME),
        ASH(() -> ParticleTypes.ASH),
        SMOKE(() -> ParticleTypes.SMOKE),
        SOUL(() -> ParticleTypes.SOUL),
        GLOW(() -> ParticleTypes.GLOW),
        HAPPY(() -> ParticleTypes.HAPPY_VILLAGER),
        WITCH(() -> ParticleTypes.WITCH),
        ENCHANT(() -> ParticleTypes.ENCHANT),
        CRIT(() -> ParticleTypes.CRIT),
        ELECTRIC(() -> ParticleTypes.ELECTRIC_SPARK),
        BUBBLE(() -> ParticleTypes.BUBBLE),
        PORTAL(() -> ParticleTypes.PORTAL),
        SPORE(() -> ParticleTypes.SPORE_BLOSSOM_AIR),
        DAMAGE(() -> ParticleTypes.DAMAGE_INDICATOR);

        private final Supplier<ParticleOptions> options;

        Particle(Supplier<ParticleOptions> options) {
            this.options = options;
        }

        public ParticleOptions options() {
            return options.get();
        }

        public static final Codec<Particle> CODEC = Codec.STRING.xmap(
                s -> valueOf(s.toUpperCase(java.util.Locale.ROOT)),
                p -> p.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Emit a small particle puff at the entity's body anchor (centre of the bounding box). */
    public static void emitAtBody(@NonNull ServerLevel level, @NonNull Mob entity, @NonNull Particle particle, int count) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();
        double spread = entity.getBbWidth() * 0.5 + 0.1;
        level.sendParticles(particle.options(), x, y, z, count, spread, spread, spread, 0.0);
    }

    /** Emit a particle ring/cloud at an arbitrary point (for AoE auras and ranged impacts). */
    public static void emitAt(@NonNull ServerLevel level, @NonNull Particle particle,
                              double x, double y, double z, int count, double spread, double speed) {
        level.sendParticles(particle.options(), x, y, z, count, spread, spread, spread, speed);
    }
}
