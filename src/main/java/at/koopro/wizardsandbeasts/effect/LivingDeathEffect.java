package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Indistinguishable from a corpse.
 *
 * <p>The Draught of Living Death used to be Slowness V, Blindness, Mining Fatigue and Invisibility —
 * a list that makes you useless but does nothing about the one thing the potion is named for. The
 * canon description is that the drinker cannot be told from a dead body, and what that has to mean in
 * Minecraft is that <b>things stop hunting you</b>. That is the whole of this effect;
 * {@code LivingDeathHandler} is where it is read.
 *
 * <p>Harmful rather than beneficial, and deliberately so despite being a hiding effect. A drinker
 * under it is blind, cannot move and cannot fight — being overlooked is the compensation for being
 * helpless, not a stealth tool. Marking it beneficial would also make it survivable through a bucket
 * of milk in the wrong direction: {@code HealAndCure}'s empty {@code cure} form strips every harmful
 * effect, which is exactly the behaviour a healing draught should have against this.
 *
 * <p>Distinct from {@code CAMOUFLAGE} and {@code MELLOW}, which are the mod's two other targeting
 * effects. Camouflage is a Demiguise's trick and drops the moment you attack; Mellow only calms
 * neutral mobs. This one stops <em>hostiles</em>, and it does not care whether you attacked, because
 * you cannot.
 */
public final class LivingDeathEffect extends MobEffect {

    /** Grave, drained blue-grey. Matches the brew's own colour. */
    private static final int COLOUR = 0x2B3138;

    public LivingDeathEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
