package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Fizzing, and pleased about it.
 *
 * <p>Carries nothing itself. The Jump Boost and Slow Falling are applied alongside it as ordinary
 * vanilla effects — so a milk bucket takes all three and nothing has to be cleaned up — and the whizz
 * on each jump, the fizz at the feet and the pop at the end all live in {@code WhizzbeeHandler},
 * because a jump is an event and a {@link MobEffect} cannot see one.
 */
public final class FizzingEffect extends MobEffect {

    /** Sherbet lilac. */
    private static final int COLOUR = 0xC9A7E8;

    public FizzingEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
