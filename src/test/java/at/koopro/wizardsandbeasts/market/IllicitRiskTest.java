package at.koopro.wizardsandbeasts.market;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chance a fence's deal reaches the Ministry, and which offence it files. */
class IllicitRiskTest {

    private static final IllicitRisk RISK = new IllicitRisk(0.08f, 16.0);

    @Test
    void aLawfulDealIsNeverReported() {
        assertEquals(0.0, RISK.chance(DealClass.LEGAL, 64, 3, 8, 100, 0, 10));
        assertEquals(0.0, RISK.chance(DealClass.CONTRABAND, 0, 0, 0, 0, 0, 1));
        assertEquals(0.0, RISK.chance(DealClass.CONTRABAND, 5, 0, 0, 0, 0, 0), "scale 0 is never reported");
    }

    @Test
    void moreEyesMoreHeatBiggerDealsMoreRisk() {
        double quiet = RISK.chance(DealClass.RESTRICTED, 1, 0, 0, 0, 0, 1);
        assertEquals(0.08 * 0.6, quiet, 1e-9, "one plain unlicensed sale is the base chance times its weight");
        assertTrue(RISK.chance(DealClass.RESTRICTED, 1, 1, 0, 0, 0, 1) > quiet, "an official saw nothing");
        assertTrue(RISK.chance(DealClass.RESTRICTED, 1, 0, 2, 0, 0, 1) > quiet, "witnesses saw nothing");
        assertTrue(RISK.chance(DealClass.RESTRICTED, 1, 0, 0, 50, 0, 1) > quiet, "heat changed nothing");
        assertTrue(RISK.chance(DealClass.RESTRICTED, 20, 0, 0, 0, 0, 1) > quiet, "volume changed nothing");
        assertTrue(RISK.chance(DealClass.CONTRABAND, 1, 0, 0, 0, 0, 1) > quiet, "contraband is no riskier");
        assertTrue(RISK.chance(DealClass.RESTRICTED, 1, 0, 0, 0, 0.6, 1) < quiet, "discretion did nothing");
    }

    @Test
    void discretionAndEyesAreCapped() {
        assertEquals(RISK.chance(DealClass.STOLEN, 3, 0, 0, 0, IllicitRisk.MAX_DISCRETION, 1),
                RISK.chance(DealClass.STOLEN, 3, 0, 0, 0, 5.0, 1), 1e-12, "discretion past the cap blinded the Ministry");
        assertEquals(RISK.chance(DealClass.STOLEN, 3, IllicitRisk.MAX_OFFICIALS, 0, 0, 0, 1),
                RISK.chance(DealClass.STOLEN, 3, 50, 0, 0, 0, 1), 1e-12);
        assertTrue(RISK.chance(DealClass.CONTRABAND, 2304, 50, 50, 100, 0, 10) <= IllicitRisk.CEILING,
                "a deal became certain");
    }

    @Test
    void splittingADealIsRiskierThanMakingItOnce() {
        // Volume is sub-linear, so n small deals carry more total risk than one deal of n.
        for (int n : new int[] {2, 5, 20, 64}) {
            double once = RISK.chance(DealClass.STOLEN, n, 0, 0, 0, 0, 1);
            double single = RISK.chance(DealClass.STOLEN, 1, 0, 0, 0, 0, 1);
            double split = 1.0 - Math.pow(1.0 - single, n);
            assertTrue(split > once, n + " units: split " + split + " vs once " + once);
        }
    }

    @Test
    void everyIllicitClassFilesAFineOffence() {
        for (DealClass deal : DealClass.values()) {
            MagicalOffence offence = IllicitDealing.offenceFor(deal);
            if (deal == DealClass.LEGAL) {
                assertNull(offence);
                continue;
            }
            assertNotNull(offence, deal.name());
            assertTrue(offence.fineable() && !offence.arrestable(), deal + " must be answered with a fine");
        }
        assertTrue(MagicalOffence.DEALING_CONTRABAND.fineKnuts() > MagicalOffence.TRAFFICKING_RESTRICTED.fineKnuts(),
                "contraband is fined below an unlicensed sale");
    }
}
