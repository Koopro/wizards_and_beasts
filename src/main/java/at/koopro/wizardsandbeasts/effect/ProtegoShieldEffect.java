package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * "You have a shield up." Marker only: the shield entity owns the ward's integrity, radius and
 * lifetime, and this effect exists so the player sees an icon for as long as it stands and so
 * systems that only need a yes/no (the basilisk's gaze, for one) have something cheap to ask.
 *
 * <p>Amplifier is the {@link at.koopro.wizardsandbeasts.spell.protego.ProtegoTier} index, nothing
 * more. It used to pack tier and remaining deflections into one number, which meant the shield's
 * real state lived in two places that could disagree.
 */
public final class ProtegoShieldEffect extends MobEffect {
    public ProtegoShieldEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB0C4DE);
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
