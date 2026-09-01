package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Riding a bubble of Droobles.
 *
 * <p>Carries nothing itself — the drift, the ceiling and the pop all live in
 * {@code BubbleFloatHandler}, because all three depend on the player's live input and position and a
 * {@link MobEffect} sees neither. What the effect provides is the thing an effect is good at: a
 * visible, expiring timer that other systems can ask about.
 */
public final class BubbleFloatEffect extends MobEffect {

    /** Soap-film blue. */
    private static final int COLOUR = 0x8FD3E8;

    public BubbleFloatEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
