package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The glow that stays with you after a hot Butterbeer.
 *
 * <p>Freezing simply stops happening: powder snow, a Snowy Slopes night, a soak in a frozen river.
 * Vanilla has no cold <em>damage</em> beyond freezing, so freezing immunity is the whole of what
 * "resistance to cold biomes" can honestly mean here — see {@code ButterbeerHandler}, which also
 * unwinds any frost already accumulated rather than only holding the line from now on.
 */
public final class WarmthEffect extends MobEffect {

    /** Butterbeer gold. */
    private static final int COLOUR = 0xE0A54A;

    public WarmthEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
