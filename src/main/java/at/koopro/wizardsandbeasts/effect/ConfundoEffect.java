package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public final class ConfundoEffect extends MobEffect {
    public ConfundoEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A0DAD);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        // Client overlay: {@link at.koopro.wizardsandbeasts.client.hud.MobEffectFullscreenOverlays}
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
