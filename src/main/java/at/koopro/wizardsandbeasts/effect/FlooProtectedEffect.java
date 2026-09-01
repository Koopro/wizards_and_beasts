package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The fire in a Floo hearth does not burn the wizard standing in it.
 *
 * <h2>Why an effect and not a check</h2>
 * <p>{@link at.koopro.wizardsandbeasts.block.floo.FlooFlamesBlock} is already harmless on its own —
 * it is a plain block, not a {@code BaseFireBlock}, so nothing asks it to burn anybody. This effect
 * is not there to protect against the Floo fire. It is there to protect against <em>everything
 * else</em> in the block a traveller is standing in: a hearth built into a burning room, lava light
 * a chunk away, or the ordinary fire a player was already on fire from when they stepped in. The
 * fiction is that stepping into a lit grate is safe, and a wizard who arrives still burning from the
 * doorway breaks it.
 *
 * <p>A marker with no tick behaviour of its own. Everything it does happens in
 * {@link at.koopro.wizardsandbeasts.event.floo.FlooProtectionEvents}, which is the one place that
 * decides what "does not burn" covers.
 *
 * <h2>Why it expires so fast</h2>
 * <p>Applied for {@link #REFRESH_TICKS} and re-applied on every tick the player overlaps the flames.
 * It is deliberately shorter than the interval a player could walk out in, so protection ends with
 * the fire rather than trailing after it: a wizard who steps out of a green hearth into a burning
 * house is on fire again within a second, which is what "only while inside" has to mean if it is to
 * mean anything.
 */
public final class FlooProtectedEffect extends MobEffect {

    /**
     * How long one application lasts. Two thirds of a second.
     *
     * <p>Long enough that a tick of lag cannot flicker it off under a player standing still, short
     * enough that walking out of the fire is felt immediately.
     */
    public static final int REFRESH_TICKS = 13;

    public FlooProtectedEffect() {
        // The Floo green, without its alpha byte - a MobEffect colour is packed RGB.
        super(MobEffectCategory.BENEFICIAL,
                at.koopro.wizardsandbeasts.floo.FlooCues.EMERALD & 0x00FFFFFF);
    }
}
