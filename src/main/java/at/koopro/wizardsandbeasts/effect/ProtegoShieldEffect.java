package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Amplifier encodes tier and deflection charges: {@code tier * 16 + remainingDeflections}.
 * Tier: 0 basic, 1 Totalum, 2 Maxima, 3 Horribilis.
 */
public final class ProtegoShieldEffect extends MobEffect {
    public ProtegoShieldEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB0C4DE);
    }

    public static int encodeAmplifier(int tier, int deflectionsRemaining) {
        return tier * 16 + Math.min(15, Math.max(0, deflectionsRemaining));
    }

    public static int tierFromAmplifier(int amplifier) {
        return Math.min(3, Math.max(0, amplifier / 16));
    }

    public static int deflectionsFromAmplifier(int amplifier) {
        return Math.min(15, Math.max(0, amplifier % 16));
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        return true;
    }
}
