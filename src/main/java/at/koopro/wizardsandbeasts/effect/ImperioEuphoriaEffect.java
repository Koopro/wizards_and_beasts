package at.koopro.wizardsandbeasts.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public final class ImperioEuphoriaEffect extends MobEffect {
    public ImperioEuphoriaEffect() {
        super(MobEffectCategory.HARMFUL, 0xF0E68C);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        if (entity.tickCount % 10 == 0) {
            double x = entity.getX();
            double y = entity.getEyeY();
            double z = entity.getZ();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 4, 0.25, 0.15, 0.25, 0.02);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
