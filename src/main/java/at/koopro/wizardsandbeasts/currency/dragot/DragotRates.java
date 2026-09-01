package at.koopro.wizardsandbeasts.currency.dragot;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import org.jspecify.annotations.NullMarked;

/**
 * The arithmetic of changing money, with no game attached.
 *
 * <p>Same split as {@code FineSchedule} and {@code LicenceRules}: a rate, a fee and a spread are
 * exactly the kind of numbers that quietly stop adding up, and none of them need a level to be
 * checked. Nothing here reads {@code Config} — the base rate is passed in, because a pure class that
 * touches the config spec cannot be loaded in a unit-test JVM.
 *
 * <h2>Why a Dragot is not a fourth coin</h2>
 * Knuts, Sickles and Galleons convert by fixed integer ratios and always will; that is one currency
 * with three denominations. A Dragot is a <em>different currency</em>: it converts at a rate that
 * moves, through a bank that takes a cut, and the result is not guaranteed to be a whole number of
 * anything. All three of those facts live in this class.
 */
@NullMarked
public final class DragotRates {

    /** Gringotts' cut on any exchange, taken from the proceeds. */
    public static final float GRINGOTTS_FEE = 0.05f;

    /** How far either side of the base rate a quote may land. */
    public static final float VARIANCE = 0.03f;

    /** Share of Dragots found in loot that are devalued. */
    public static final float DEVALUED_LOOT_CHANCE = 0.01f;

    /** Chance a vendor notices a devalued Dragot when one is offered. */
    public static final float DEVALUED_NOTICE_CHANCE = 0.20f;

    /** Markup a grudging vendor adds when they will take Dragots but would rather not. */
    public static final float PENALTY_MARKUP = 0.25f;

    /** Ticks a quoted rate stands for. Twenty seconds: long enough to decide, short enough to move. */
    public static final int QUOTE_LIFETIME_TICKS = 400;

    /** Guard rails on the configured base rate, so a typo cannot make Dragots free or priceless. */
    public static final double MIN_BASE_RATE = 0.01;
    public static final double MAX_BASE_RATE = 100.0;

    private DragotRates() {}

    /**
     * The rate a teller quotes right now: the configured base, moved by up to {@link #VARIANCE}.
     *
     * <p>{@code roll} is a uniform {@code [0,1)} draw supplied by the caller rather than taken from a
     * random source here, which is what makes the spread testable at its edges instead of
     * approximately.
     *
     * @param baseRate Galleons per Dragot, from config
     * @param roll     uniform {@code [0,1)}
     */
    public static float quotedRate(double baseRate, float roll) {
        double clamped = Math.max(MIN_BASE_RATE, Math.min(MAX_BASE_RATE, baseRate));
        double swing = (roll * 2.0 - 1.0) * VARIANCE;
        return (float) (clamped * (1.0 + swing));
    }

    /**
     * Knuts received for {@code dragots} at {@code rate}, after the Gringotts fee.
     *
     * <p>Rounded <em>down</em>. The bank does not round in your favour, and a floor is also the only
     * rounding that cannot mint value out of a long enough chain of exchanges.
     */
    public static long dragotsToKnuts(int dragots, float rate) {
        if (dragots <= 0) {
            return 0L;
        }
        double gross = (double) dragots * rate * CurrencyHelper.KNUTS_PER_GALLEON;
        return (long) Math.floor(gross * (1.0 - GRINGOTTS_FEE));
    }

    /**
     * Knuts required to buy {@code dragots} at {@code rate}, with the fee added on top.
     *
     * <p>Rounded <em>up</em>, for the same reason the other direction rounds down: buying and selling
     * the same coin back-to-back must never leave the player ahead, or the exchange is a money press.
     */
    public static long knutsToBuyDragots(int dragots, float rate) {
        if (dragots <= 0) {
            return 0L;
        }
        double net = (double) dragots * rate * CurrencyHelper.KNUTS_PER_GALLEON;
        return (long) Math.ceil(net * (1.0 + GRINGOTTS_FEE));
    }

    /** How many whole Dragots {@code knuts} will buy at {@code rate}. */
    public static int dragotsAffordable(long knuts, float rate) {
        if (knuts <= 0) {
            return 0;
        }
        double perDragot = rate * CurrencyHelper.KNUTS_PER_GALLEON * (1.0 + GRINGOTTS_FEE);
        if (perDragot <= 0.0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(knuts / perDragot));
    }

    /** The same sum with a grudging vendor's markup applied. */
    public static long withPenalty(long knuts) {
        return (long) Math.ceil(knuts * (1.0 + PENALTY_MARKUP));
    }

    /** The quoted rate as a percentage of the base, for a "+2.1%" style readout. */
    public static float driftPercent(double baseRate, float quoted) {
        if (baseRate <= 0.0) {
            return 0.0f;
        }
        return (float) ((quoted / baseRate - 1.0) * 100.0);
    }
}
