package at.koopro.wizardsandbeasts.currency.dragot;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exchange desk's arithmetic.
 *
 * <p>The one that matters most is {@link #buyingAndSellingBackIsAlwaysALoss}. An exchange that can be
 * cycled for profit is not a currency feature, it is a money press, and the two roundings that
 * prevent it ({@code floor} out, {@code ceil} in) are easy to "tidy up" into a bug that takes weeks
 * to notice on a server.
 */
class DragotRatesTest {

    private static final double BASE = 0.8;
    private static final float EPS = 1e-4f;

    @Test
    void theDefaultRateIsWhatTheConfigAdvertises() {
        // Mid roll = no drift, so the quote is exactly the configured base.
        assertEquals((float) BASE, DragotRates.quotedRate(BASE, 0.5f), EPS);
    }

    @Test
    void theSpreadIsBoundedAndSymmetric() {
        float low = DragotRates.quotedRate(BASE, 0.0f);
        float high = DragotRates.quotedRate(BASE, 1.0f);

        assertEquals(BASE * (1.0 - DragotRates.VARIANCE), low, EPS);
        assertEquals(BASE * (1.0 + DragotRates.VARIANCE), high, EPS);
        assertEquals(BASE - low, high - BASE, EPS, "the spread must not favour one side");
    }

    @Test
    void everyRollLandsInsideTheSpread() {
        float floor = (float) (BASE * (1.0 - DragotRates.VARIANCE));
        float ceiling = (float) (BASE * (1.0 + DragotRates.VARIANCE));
        for (int i = 0; i <= 1000; i++) {
            float quoted = DragotRates.quotedRate(BASE, i / 1000.0f);
            assertTrue(quoted >= floor - EPS && quoted <= ceiling + EPS,
                    "roll " + i / 1000.0f + " quoted " + quoted + ", outside [" + floor + ", " + ceiling + "]");
        }
    }

    @Test
    void anAbsurdConfiguredRateIsClampedRatherThanHonoured() {
        assertTrue(DragotRates.quotedRate(0.0, 0.5f) > 0.0f, "a free Dragot would break every price");
        assertTrue(DragotRates.quotedRate(1_000_000.0, 0.5f)
                <= DragotRates.MAX_BASE_RATE * (1.0 + DragotRates.VARIANCE) + EPS);
    }

    @Test
    void sellingTakesTheGringottsCut() {
        float rate = 1.0f; // one Galleon per Dragot, so the fee is the only thing moving the number
        long paid = DragotRates.dragotsToKnuts(10, rate);
        long gross = 10L * CurrencyHelper.KNUTS_PER_GALLEON;

        assertTrue(paid < gross, "the bank must take its cut");
        assertEquals(Math.floor(gross * (1.0 - DragotRates.GRINGOTTS_FEE)), paid, 1.0);
    }

    @Test
    void buyingAddsTheGringottsCut() {
        float rate = 1.0f;
        long cost = DragotRates.knutsToBuyDragots(10, rate);
        long net = 10L * CurrencyHelper.KNUTS_PER_GALLEON;

        assertTrue(cost > net, "the bank must take its cut in both directions");
    }

    @Test
    void buyingAndSellingBackIsAlwaysALoss() {
        // The invariant that stops the exchange being an infinite-money machine. Checked across the
        // whole spread and a range of sizes, because a rounding that only leaks at one rate is still
        // a leak somebody will find.
        for (int roll = 0; roll <= 10; roll++) {
            float rate = DragotRates.quotedRate(BASE, roll / 10.0f);
            for (int dragots : new int[] {1, 2, 7, 10, 43, 100, 999}) {
                long cost = DragotRates.knutsToBuyDragots(dragots, rate);
                long back = DragotRates.dragotsToKnuts(dragots, rate);
                assertTrue(back < cost,
                        "round trip profited at rate " + rate + " on " + dragots
                                + " Dragots: paid " + cost + ", got back " + back);
            }
        }
    }

    @Test
    void nothingIsExchangedForNothing() {
        assertEquals(0L, DragotRates.dragotsToKnuts(0, 0.8f));
        assertEquals(0L, DragotRates.dragotsToKnuts(-5, 0.8f));
        assertEquals(0L, DragotRates.knutsToBuyDragots(0, 0.8f));
        assertEquals(0, DragotRates.dragotsAffordable(0, 0.8f));
        assertEquals(0, DragotRates.dragotsAffordable(-100, 0.8f));
    }

    @Test
    void affordabilityAgreesWithThePriceItQuotes() {
        float rate = DragotRates.quotedRate(BASE, 0.5f);
        for (long purse : new long[] {100, 493, 5_000, 100_000}) {
            int affordable = DragotRates.dragotsAffordable(purse, rate);
            assertTrue(DragotRates.knutsToBuyDragots(affordable, rate) <= purse,
                    "quoted " + affordable + " affordable on " + purse + " Knuts but could not pay for them");
            assertTrue(DragotRates.knutsToBuyDragots(affordable + 1, rate) > purse,
                    "under-counted what " + purse + " Knuts would buy");
        }
    }

    @Test
    void aGrudgingVendorAlwaysChargesMore() {
        for (long knuts : new long[] {1, 17, 493, 10_000}) {
            assertTrue(DragotRates.withPenalty(knuts) > knuts,
                    "the penalty must actually cost something at " + knuts + " Knuts");
        }
    }

    @Test
    void driftReadsZeroAtTheBaseAndSignedEitherSide() {
        assertEquals(0.0f, DragotRates.driftPercent(BASE, (float) BASE), 0.01f);
        assertTrue(DragotRates.driftPercent(BASE, DragotRates.quotedRate(BASE, 1.0f)) > 0.0f);
        assertTrue(DragotRates.driftPercent(BASE, DragotRates.quotedRate(BASE, 0.0f)) < 0.0f);
        assertEquals(0.0f, DragotRates.driftPercent(0.0, 1.0f), EPS, "no base means no drift, not a divide by zero");
    }

    @Test
    void theCounterfeitOddsAreWorthPlayingAround() {
        assertTrue(DragotRates.DEVALUED_LOOT_CHANCE > 0.0f && DragotRates.DEVALUED_LOOT_CHANCE < 0.1f,
                "bad money should be a nasty surprise, not a tax on looting");
        assertTrue(DragotRates.DEVALUED_NOTICE_CHANCE > 0.0f && DragotRates.DEVALUED_NOTICE_CHANCE < 1.0f,
                "a dud that is always or never spotted is not a risk");

        // Spending a bad coin should usually work and reliably catch up with you.
        assertTrue(Math.pow(1.0 - DragotRates.DEVALUED_NOTICE_CHANCE, 1) > 0.5,
                "one purchase with a dud should usually go through");
        assertTrue(Math.pow(1.0 - DragotRates.DEVALUED_NOTICE_CHANCE, 10) < 0.2,
                "ten purchases on the same dud should very rarely all go through");
    }

    @Test
    void aDragotIsNotAGalleonAndNotAKnut() {
        // Guards the premise of the whole item: if the rate ever collapses onto a canonical coin's
        // value the Dragot has quietly become a fourth denomination.
        long oneDragot = DragotRates.dragotsToKnuts(1, (float) BASE);
        assertFalse(oneDragot == CurrencyHelper.KNUTS_PER_GALLEON, "a Dragot must not equal a Galleon");
        assertFalse(oneDragot == CurrencyHelper.KNUTS_PER_SICKLE, "a Dragot must not equal a Sickle");
        assertTrue(oneDragot > 1, "a Dragot must be worth more than a Knut");
    }
}
