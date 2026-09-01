package at.koopro.wizardsandbeasts.ministry.law;

import org.jspecify.annotations.NullMarked;

/**
 * The arithmetic of a Ministry fine. Pure — no player, no vault, no {@code Config}, no Minecraft — so the
 * numbers a player experiences can be pinned by unit tests without a game.
 *
 * <p>Three rules live here and nowhere else:
 *
 * <ol>
 *   <li><b>Priors bite.</b> A fine scales by the same repeat multiplier the notoriety side uses, so a
 *       career Apparator pays more than a first-timer for the identical offence.</li>
 *   <li><b>Debt freezes heat.</b> Notoriety cools only while you are square with the Ministry. Owing money
 *       is the mechanical reason a paperwork offence cannot simply be waited out.</li>
 *   <li><b>Debt heat is capped.</b> Ignoring a bill makes you <i>sought for questioning</i>, never
 *       Undesirable No. 1 — an unpaid parking ticket must not be able to grow into a manhunt, or the
 *       cheapest offence in the mod would eventually outrank the Killing Curse.</li>
 * </ol>
 */
@NullMarked
public final class FineSchedule {

    /**
     * Heat added per second while a fine stands unpaid. Deliberately a tenth of nothing: at this rate a
     * debtor needs roughly ten minutes of ignoring the Ministry to move one band, which is a nudge toward
     * the vault rather than a punishment for being poor for a moment.
     */
    public static final float DEBT_HEAT_PER_SECOND = 0.02f;

    /**
     * The hard ceiling debt heat may push notoriety to. Set to the {@link WantedLevel#WANTED} threshold so
     * an unpaid fine can just tip a clean wizard into the band where Aurors would be dispatched, and not
     * one point further. Heat from actual crimes is unaffected by this and may go far past it.
     */
    public static final float DEBT_HEAT_CEILING = WantedLevel.WANTED.threshold();

    private FineSchedule() {}

    /**
     * What one conviction costs.
     *
     * @param baseKnuts       the offence's tariff, {@link MagicalOffence#fineKnuts()}
     * @param repeatMultiplier the offender's priors for that same offence, 1.0 for a first offence
     * @param scalePercent    the server's global fine scale, 100 for the shipped tariff; 0 disables fines
     * @return the amount owed, never negative, and never rounded down to nothing for a real fine
     */
    public static long assess(int baseKnuts, float repeatMultiplier, int scalePercent) {
        if (baseKnuts <= 0 || scalePercent <= 0) {
            return 0L;
        }
        double scaled = (double) baseKnuts * Math.max(1.0f, repeatMultiplier) * (scalePercent / 100.0);
        // A fine the tariff says exists must cost at least one Knut: rounding a 1-Knut offence to zero
        // would file the offence and charge nothing, which reads as a bug rather than as leniency.
        return Math.max(1L, Math.round(scaled));
    }

    /**
     * How much of a debt Gringotts can actually settle from what is in the vault right now. Partial
     * payment is deliberate — the Ministry takes what is there and keeps the rest on the books, rather
     * than refusing everything until the player can clear the whole bill in one go.
     */
    public static long collectable(long owedKnuts, long vaultKnuts) {
        return Math.max(0L, Math.min(owedKnuts, vaultKnuts));
    }

    /** True while heat is allowed to cool. Debt, a live sentence and fugitive status each freeze it. */
    public static boolean mayCool(float notoriety, boolean owesFine, boolean fugitive, boolean serving) {
        return notoriety > 0.0f && !owesFine && !fugitive && !serving;
    }

    /**
     * Heat added by an unpaid fine over {@code elapsedTicks}, already limited so the result cannot carry
     * notoriety past {@link #DEBT_HEAT_CEILING}. Returns 0 once the debtor is at or above the ceiling for
     * any reason, including heat earned from real crimes.
     */
    public static float debtHeat(float currentNotoriety, int elapsedTicks) {
        if (currentNotoriety >= DEBT_HEAT_CEILING || elapsedTicks <= 0) {
            return 0.0f;
        }
        float gain = DEBT_HEAT_PER_SECOND * (elapsedTicks / 20.0f);
        return Math.min(gain, DEBT_HEAT_CEILING - currentNotoriety);
    }
}
