package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.NonNull;

public final class StupefyEffect extends MobEffect {
    public StupefyEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A0DAD);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        // SLOWNESS 255 brings movement speed to exactly 0 (formula: speed * max(0, 1 - 0.15 * 255))
        entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 254, false, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 5, false, false, false));
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 5 == 0;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
