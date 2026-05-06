package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/** While active, the victim cannot pick up their Expelliarmus-dropped wand. */
public final class ExpelliarmusDisarmedEffect extends MobEffect {
    public ExpelliarmusDisarmedEffect() {
        super(MobEffectCategory.HARMFUL, 0xE8D5B7);
    }

    @Override
    public boolean isBeneficial() {
        return false;
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
