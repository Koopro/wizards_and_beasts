package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A clear head, and the sharper hour that follows it.
 *
 * <p>Carries no attribute modifiers and does nothing on tick. Everything it is worth is applied at
 * the moment experience is awarded ({@code HogwartsComfort}), which is the only place a "+10% XP"
 * effect can honestly live — there is no attribute for it and no per-tick work to do.
 */
public final class HogwartsComfortEffect extends MobEffect {

    /** Pumpkin orange. */
    private static final int COLOUR = 0xE07A22;

    public HogwartsComfortEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
