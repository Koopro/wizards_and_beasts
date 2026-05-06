package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

public final class CrucioSanityDrainEffect extends MobEffect {
    public CrucioSanityDrainEffect() {
        super(MobEffectCategory.HARMFUL, 0x4B0000);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            int exp = player.getData(ModAttachments.CRUCIO_EXPOSURE_TICKS.get()) + 1;
            player.setData(ModAttachments.CRUCIO_EXPOSURE_TICKS.get(), exp);
            if (player.tickCount % 40 == 0) {
                float ms = player.getData(ModAttachments.MENTAL_STABILITY.get());
                player.setData(ModAttachments.MENTAL_STABILITY.get(), Math.max(0f, ms - 1.5f));
                if (ms <= 10.0f) {
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, false, true, true));
                }
            }
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
