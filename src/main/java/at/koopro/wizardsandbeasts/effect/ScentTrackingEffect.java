package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A beast's nose: nearby living things are outlined through blocks, for the bearer alone.
 *
 * <p>A marker effect with no behaviour of its own. It exists so that one rule — "does this form grant
 * {@link at.koopro.wizardsandbeasts.form.sense.FormSense#SCENT_TRACK}" — can be decided once on the
 * server and read on the client without a packet, since mob effects already sync. The drawing is done by
 * {@code FormScentOutlineProvider}, entirely in the client's render state.
 *
 * <p>Granted and revoked by {@code FormSenseService}, never by a potion; it has no brew and no recipe.
 * Ambient and icon-less, because it is something a body has rather than something it drank.
 */
public final class ScentTrackingEffect extends MobEffect {

    /** Blood-warm, so the outlines read as a scent rather than as a targeting reticle. */
    private static final int COLOUR = 0xC2603A;

    public ScentTrackingEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
