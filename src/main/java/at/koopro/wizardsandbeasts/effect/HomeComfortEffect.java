package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.comfort.HomeComfort;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * The feeling of having eaten something you actually like.
 *
 * <p>Carries the trickle of healing itself, on a slow tick — one heart over ninety seconds, which is
 * a worse heal than almost any food and is meant to be. The part that matters is the fear-shortening,
 * and that lives in {@code HomeComfortHandler} because it happens to <em>other</em> effects as they
 * arrive.
 */
public final class HomeComfortEffect extends MobEffect {

    /** Treacle gold. */
    private static final int COLOUR = 0xC98A3B;

    public HomeComfortEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(HomeComfort.HEAL_PER_BEAT);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % HomeComfort.HEAL_INTERVAL == 0;
    }
}
