package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.MagicColours;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public final class ImperioResistingEffect extends MobEffect {
    public ImperioResistingEffect() {
        super(MobEffectCategory.NEUTRAL, 0xFF8C00);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        double x = entity.getX();
        double y = entity.getEyeY();
        double z = entity.getZ();
        // The will straining against the curse: stun-scarlet sparks, not vanilla fire.
        level.sendParticles(new SpellTintParticleOptions(ModParticles.ELECTRIC_ARC.get(), MagicColours.STUN),
                x, y, z, 6, 0.2, 0.1, 0.2, 0.01);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 4 == 0;
    }
}
