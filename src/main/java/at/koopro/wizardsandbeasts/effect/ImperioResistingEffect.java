package at.koopro.wizardsandbeasts.effect;

import net.minecraft.core.particles.ParticleTypes;
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
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 6, 0.2, 0.1, 0.2, 0.01);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 4 == 0;
    }
}
