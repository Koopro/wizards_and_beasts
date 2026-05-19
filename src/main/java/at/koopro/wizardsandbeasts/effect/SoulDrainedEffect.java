package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public final class SoulDrainedEffect extends MobEffect {

    public SoulDrainedEffect() {
        super(MobEffectCategory.HARMFUL, 0xD8D8D8);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,    44, 3, false, true, true));
        entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,   44, 0, false, true, true));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,    44, 2, false, true, true));
        entity.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 44, 2, false, true, true));
        // Soul consumed — attrition damage bypasses armor
        entity.hurt(level.damageSources().magic(), 1.0f);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }

}
