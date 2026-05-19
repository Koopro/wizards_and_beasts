package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

public final class DementorChillEffect extends MobEffect {

    public DementorChillEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A5A8A);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        switch (amplifier) {
            case 0 ->
                entity.hurt(level.damageSources().magic(), 1.0f);
            case 1 -> {
                entity.hurt(level.damageSources().magic(), 1.0f);
                drainHunger(entity);
            }
            case 2 -> {
                entity.hurt(level.damageSources().magic(), 2.0f);
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 42, 1, false, true, true));
            }
            default -> {
                entity.hurt(level.damageSources().magic(), 2.0f);
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 42, 2, false, true, true));
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 42, 1, false, true, true));
                drainHunger(entity);
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }

    private static void drainHunger(LivingEntity entity) {
        if (entity instanceof Player player) {
            player.getFoodData().addExhaustion(1.0f);
        }
    }
}
