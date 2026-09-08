package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Wolfsbane Potion: the werewolf keeps their mind through the change.
 *
 * <p>A marker effect with no attribute modifiers and no tick, and that is correct rather than lazy —
 * what the potion does is not expressible as a number. Everything keys off its <em>presence</em>:
 * {@code WerewolfRules.hasWolfsbane} reads it, {@code WerewolfMoonHandler.refreshControl} recomputes
 * the loss-of-control flag from it every second, and {@code WerewolfTransformService} lets a medicated
 * werewolf keep the shape past dawn and give it back when they choose.
 *
 * <p>It does <b>not</b> stop the transformation. Canon is explicit — Lupin still becomes a wolf every
 * month; the potion is what keeps him a person while he is one. A server that wants the potion to be a
 * cure sets {@code werewolfWolfsbaneSuppressesTransform}.
 *
 * <p>Categorised {@code BENEFICIAL} so milk, {@code /effect clear} and every "remove harmful effects"
 * path treat it as something the drinker wants — because it is, however unpleasant the brew.
 */
public final class WolfsbaneEffect extends MobEffect {

    /** The muted green of the brew itself, matching {@code brews/wolfsbane_potion.json}. */
    private static final int COLOUR = 0x6B7F4A;

    public WolfsbaneEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
