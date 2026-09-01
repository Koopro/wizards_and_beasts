package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The half-minute after chocolate when the cold cannot get back in.
 *
 * <p>Carries nothing itself: {@code ChocolateWardHandler} refuses the reapplication. An effect is the
 * right home for the <em>timer</em> — it shows in the HUD, it is visible to the player, and it
 * expires on its own — and the wrong home for the refusal, which has to happen on somebody else's
 * event.
 */
public final class ChocolateWardEffect extends MobEffect {

    /** Warm cocoa. */
    private static final int COLOUR = 0x6B4326;

    public ChocolateWardEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
