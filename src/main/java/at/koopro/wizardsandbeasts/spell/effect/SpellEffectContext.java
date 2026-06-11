package at.koopro.wizardsandbeasts.spell.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Single application envelope handed to {@link SpellEffectComponent#apply(SpellEffectContext)}.
 *
 * <p>Carries the caster, an optional impact {@code target}, the world, a world {@code position} for
 * positional effects, and the cast's <b>scaling multipliers</b> ({@code damageMult},
 * {@code durationMult}, {@code controlMult}) so components reproduce the legacy proficiency/wand/skill
 * scaling instead of applying flat values. The {@code subject} convenience resolves to the impact
 * target when present, else the caster.
 *
 * <p>Multipliers default to {@code 1.0} (the factory methods); invocation sites that have a
 * {@code CastContext}/{@code SpellScalingProfile} call {@link #withScaling(float, float, float)} to
 * thread the real values. {@code 1.0} reproduces the un-scaled base behavior.
 */
public record SpellEffectContext(
        @NonNull ServerPlayer caster,
        @Nullable LivingEntity target,
        @NonNull ServerLevel level,
        @NonNull Vec3 position,
        float damageMult,
        float durationMult,
        float controlMult) {

    /** Impact target when present, otherwise the caster. */
    public @NonNull LivingEntity subject() {
        return target != null ? target : caster;
    }

    /** Self cast (no impact target); position defaults to the caster's eye. */
    public static SpellEffectContext ofSelf(@NonNull ServerPlayer caster, @NonNull ServerLevel level) {
        return new SpellEffectContext(caster, null, level, caster.getEyePosition(), 1.0f, 1.0f, 1.0f);
    }

    /** Impact against a living entity; position defaults to the target's bounding-box center. */
    public static SpellEffectContext ofTarget(@NonNull ServerPlayer caster, @NonNull LivingEntity target,
                                              @NonNull ServerLevel level) {
        return new SpellEffectContext(caster, target, level, target.getBoundingBox().getCenter(), 1.0f, 1.0f, 1.0f);
    }

    /** Positional impact (block hit / explosion point) with no living target. */
    public static SpellEffectContext ofPosition(@NonNull ServerPlayer caster, @NonNull ServerLevel level,
                                                @NonNull Vec3 position) {
        return new SpellEffectContext(caster, null, level, position, 1.0f, 1.0f, 1.0f);
    }

    /** Copy with scaling multipliers set (sourced from the cast's {@code SpellScalingProfile}/modifiers). */
    public SpellEffectContext withScaling(float damageMult, float durationMult, float controlMult) {
        return new SpellEffectContext(caster, target, level, position, damageMult, durationMult, controlMult);
    }

    /**
     * Copy re-pointed at {@code newTarget} (position = its center), preserving caster/level/scaling.
     * Used by {@code aoe_apply} to run a nested component list against each entity in range.
     */
    public SpellEffectContext forTarget(@NonNull LivingEntity newTarget) {
        return new SpellEffectContext(caster, newTarget, level, newTarget.getBoundingBox().getCenter(),
                damageMult, durationMult, controlMult);
    }
}
