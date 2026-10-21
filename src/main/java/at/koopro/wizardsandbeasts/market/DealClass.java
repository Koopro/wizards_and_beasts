package at.koopro.wizardsandbeasts.market;

import org.jspecify.annotations.NullMarked;

/**
 * What the law makes of a deal. Decided per stack on the server ({@link MarketGoods#classify}), never trusted from
 * data or the client.
 *
 * <ul>
 *   <li>{@link #LEGAL} — anyone, anywhere.</li>
 *   <li>{@link #RESTRICTED} — a Ministry-controlled substance (item tag {@code restricted_ingredient}). A lawful
 *       counter asks for the Restricted Substances licence; a fence does not ask, and that is the crime.</li>
 *   <li>{@link #CONTRABAND} — goods nobody may trade (item tag {@code contraband}): a lawful counter refuses
 *       them outright.</li>
 *   <li>{@link #STOLEN} — another wizard's wand, or treasure from a Gringotts-claimed site that was never
 *       surrendered to the bank.</li>
 *   <li>{@link #SERVICE} — magic sold under the counter: a memory charm on a witness, a forged endorsement.</li>
 * </ul>
 *
 * @param weight how much more likely than a plain unlicensed sale this deal is to reach the Ministry
 */
@NullMarked
public enum DealClass {
    LEGAL(0.0f),
    RESTRICTED(0.6f),
    CONTRABAND(1.0f),
    STOLEN(0.8f),
    SERVICE(0.7f);

    private final float weight;

    DealClass(float weight) {
        this.weight = weight;
    }

    public float weight() {
        return weight;
    }

    /** Whether a lawful counter may handle it at all (restricted goods: with papers). */
    public boolean lawful() {
        return this == LEGAL || this == RESTRICTED;
    }

    /** The more serious of two classes, for a deal of mixed goods. */
    public DealClass worse(DealClass other) {
        return other.weight > weight ? other : this;
    }
}
