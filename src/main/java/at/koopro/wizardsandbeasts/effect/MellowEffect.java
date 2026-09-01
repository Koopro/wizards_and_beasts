package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Pleasantly unbothered, and not worth bothering.
 *
 * <p>Neutral things — wolves, bees, endermen, iron golems, piglins — stop minding you while it
 * lasts, and go straight back to minding you the instant you hit one. Hostile mobs are untouched:
 * this is a warm drink, not a concealment charm, and something that already wanted you dead is not
 * going to be talked round.
 *
 * <p>The FOV softening is client-side ({@code MellowFovHandler}) and is the only thing about
 * Butterbeer that touches the camera. Deliberately gentle and deliberately not a wobble — the point
 * is that this is drinkable on a server full of children.
 */
public final class MellowEffect extends MobEffect {

    /** Warm cream. */
    private static final int COLOUR = 0xF2D9A8;

    public MellowEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
