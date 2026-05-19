package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public final class TarantallegraEffect extends MobEffect {
    public TarantallegraEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A0DAD);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        double strafe = (level.random.nextDouble() - 0.5D) * 0.35D;
        entity.push(strafe, 0.0D, -strafe);
        entity.hurtMarked = true;
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 4 == 0;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
