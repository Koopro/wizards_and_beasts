package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Noticing what other people have decided is not there.
 *
 * <p>Carries nothing: the outlines are drawn entirely client-side by
 * {@code WrackspurtOutlineProvider}, off an effect the client already replicates. The effect exists
 * to be a visible, expiring timer — which is the only part of this a {@link MobEffect} is good for.
 */
public final class WrackspurtSightEffect extends MobEffect {

    /** Wrackspurt violet. */
    private static final int COLOUR = 0xB07ACB;

    public WrackspurtSightEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
