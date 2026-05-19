package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public final class DisorientedEffect extends MobEffect {
    public DisorientedEffect() {
        super(MobEffectCategory.HARMFUL, 0x2E7D32);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        // Refresh nausea every second to keep the visual effect alive for DISORIENTED's duration.
        entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 30, 0, false, false, false));
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}
