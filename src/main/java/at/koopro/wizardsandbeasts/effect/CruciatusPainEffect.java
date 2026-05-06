package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public final class CruciatusPainEffect extends MobEffect {
    public CruciatusPainEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        entity.hurt(level.damageSources().magic(), 1.0f);
        MobEffectInstance self = entity.getEffect(ModEffects.CRUCIATUS_PAIN);
        if (self != null && !entity.hasEffect(ModEffects.CRUCIO_SANITY_DRAIN)) {
            entity.addEffect(new MobEffectInstance(ModEffects.CRUCIO_SANITY_DRAIN, self.getDuration(), 0, false, true, true));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
