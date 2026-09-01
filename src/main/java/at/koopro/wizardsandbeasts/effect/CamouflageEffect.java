package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A Demiguise's own trick: not being <em>seen</em>, rather than not being <em>there</em>.
 *
 * <p>Invisibility hides your body; this hides your presence. Mobs will not choose you as a target
 * while it holds, which is a different and stronger thing — vanilla Invisibility only shortens the
 * range at which a mob notices you, and a zombie already walking toward you keeps coming.
 *
 * <p><b>It breaks the moment you swing.</b> A Demiguise's concealment is passive, and so is this:
 * see {@code CamouflageHandler}. That is what keeps it from being a free opening attack — the
 * effect is for getting past things, not for ambushing them.
 */
public final class CamouflageEffect extends MobEffect {

    /** Pale silver-grey — a Demiguise's coat. */
    private static final int COLOUR = 0xBFC7CF;

    public CamouflageEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
