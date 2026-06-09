package at.koopro.wizardsandbeasts.spell.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Single application envelope handed to a {@link SpellEffectComponent#apply(SpellEffectContext)}.
 *
 * <p>It deliberately carries the minimum a primitive effect needs: the caster, an optional impact
 * {@code target}, the world, and a world {@code position} for positional effects (light, explosion).
 * The {@code subject} convenience resolves to the impact target when present, else the caster — so a
 * component authored on a SELF spell (no target) and the same component fired at a projectile/cone/
 * targeted impact (target present) behave consistently.
 *
 * <p><b>Not yet wired into the cast pipeline.</b> This is Step-2 infrastructure: the vocabulary and
 * its application logic exist and compile, but no spell currently constructs or runs components. The
 * eventual wiring (cast-time for SELF, and impact sites for projectile/cone/targeted) is a separate,
 * approved step because it touches shared cast/executor code.
 */
public record SpellEffectContext(
        @NonNull ServerPlayer caster,
        @Nullable LivingEntity target,
        @NonNull ServerLevel level,
        @NonNull Vec3 position) {

    /** Impact target when present, otherwise the caster. */
    public @NonNull LivingEntity subject() {
        return target != null ? target : caster;
    }

    /** Self cast (no impact target); position defaults to the caster's eye. */
    public static SpellEffectContext ofSelf(@NonNull ServerPlayer caster, @NonNull ServerLevel level) {
        return new SpellEffectContext(caster, null, level, caster.getEyePosition());
    }

    /** Impact against a living entity; position defaults to the target's bounding-box center. */
    public static SpellEffectContext ofTarget(@NonNull ServerPlayer caster, @NonNull LivingEntity target,
                                              @NonNull ServerLevel level) {
        return new SpellEffectContext(caster, target, level, target.getBoundingBox().getCenter());
    }

    /** Positional impact (block hit / explosion point) with no living target. */
    public static SpellEffectContext ofPosition(@NonNull ServerPlayer caster, @NonNull ServerLevel level,
                                                @NonNull Vec3 position) {
        return new SpellEffectContext(caster, null, level, position);
    }
}
