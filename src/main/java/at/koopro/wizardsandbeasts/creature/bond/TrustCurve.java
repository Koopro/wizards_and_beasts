package at.koopro.wizardsandbeasts.creature.bond;

import org.jspecify.annotations.NullMarked;

/**
 * How fast a keeper earns a creature's trust (systems audit B5, the Magizoologist's signature).
 *
 * <p>Canon: Newt Scamander walks into his case and every creature in it knows him (Fantastic Beasts, 2016); trust
 * that takes anyone else months he has in days. A wizard of the Magizoologist vocation runs every bondable creature's
 * trust curve at half: each kindness counts double. A wizard who has only studied creatures (the web's Creature
 * Knowledge) earns half again, as before.
 */
@NullMarked
public final class TrustCurve {

    /** The vocation flag. */
    public static final String HALF_CURVE = "trust_half_curve";

    private TrustCurve() {}

    /** The bond a kindness worth {@code amount} actually adds. */
    public static int scaled(int amount, boolean magizoologistVocation, boolean creatureKnowledge) {
        if (magizoologistVocation) {
            return amount * 2;
        }
        return creatureKnowledge ? (int) Math.ceil(amount * 1.5) : amount;
    }
}
