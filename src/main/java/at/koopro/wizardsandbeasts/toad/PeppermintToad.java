package at.koopro.wizardsandbeasts.toad;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A chocolate toad with a peppermint centre, which does not stop moving once eaten.
 *
 * <p>Novelty candy, and priced as such: twelve seconds of ordinary Speed and a joke. It is here to be
 * the cheap sweet somebody eats because it is funny, so nothing about it should ever compete with the
 * Whizzbee or a potion.
 *
 * <p>The mint does one small useful thing — it settles your stomach by exactly one step. Not a cure:
 * a bad case of Nausea takes several toads, which is a funnier outcome than one toad fixing it.
 */
@NullMarked
public final class PeppermintToad {

    /** How long it hops about. */
    public static final int DURATION_TICKS = 240;   // 12s

    /** Ticks between hops. Every three seconds — often enough to be a running gag, rare enough not to nag. */
    public static final int HOP_INTERVAL = 60;

    private PeppermintToad() {}

    /**
     * Settles a stomach by one step.
     *
     * <p>Nausea has no stacks, only an amplifier, so "one stack" means one level: amplifier 0 is
     * removed outright, and anything stronger comes down by one with its remaining duration intact.
     * Re-applying at full duration would make a toad a <em>better</em> answer the worse the nausea
     * was, which is the wrong way round for a novelty sweet.
     *
     * @return the amplifier that was cleared away, or {@code -1} if there was no nausea to settle
     */
    public static int settleStomach(LivingEntity eater) {
        @Nullable MobEffectInstance nausea = eater.getEffect(MobEffects.NAUSEA);
        if (nausea == null) {
            return -1;
        }
        int amplifier = nausea.getAmplifier();
        int duration = nausea.getDuration();
        eater.removeEffect(MobEffects.NAUSEA);
        if (amplifier > 0) {
            eater.addEffect(new MobEffectInstance(
                    MobEffects.NAUSEA, duration, amplifier - 1, false, true, true));
        }
        return amplifier;
    }
}
