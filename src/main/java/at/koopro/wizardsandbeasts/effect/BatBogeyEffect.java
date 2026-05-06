package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public final class BatBogeyEffect extends MobEffect {
    public BatBogeyEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        entity.hurt(level.damageSources().magic(), 1.0f);
        entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, true, true));
        entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, false, true, true));
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 60 == 0;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
