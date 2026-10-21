package at.koopro.wizardsandbeasts.wand.ollivander;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.ministry.licence.LicenceUpgrade;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two new prices (documentation/ECONOMY.md §5): Ollivander's wand care, by wear, and the Ministry's endorsement
 * fee, by rank. Both pure.
 */
class WandCarePricingTest {

    private static final long FULL = 986L; // the default: two Galleons for a wand on the brink

    @Test
    void ollivanderTendsOnlyWhatIsWornAndNotSnapped() {
        assertEquals(WandCare.Verdict.SOUND, WandCare.verdict(1.0f));
        assertEquals(WandCare.Verdict.TENDABLE, WandCare.verdict(0.99f));
        assertEquals(WandCare.Verdict.TENDABLE, WandCare.verdict(0.11f));
        assertEquals(WandCare.Verdict.SNAPPED, WandCare.verdict(WandAllegianceRules.BROKEN_AT));
        assertEquals(WandCare.Verdict.SNAPPED, WandCare.verdict(0.0f));
    }

    @Test
    void thePriceFollowsTheWear() {
        assertEquals(0L, WandCare.price(1.0f, FULL), "a sound wand costs nothing");
        assertEquals(0L, WandCare.price(0.05f, FULL), "a snapped wand is not for sale at any price");
        assertEquals(0L, WandCare.price(0.5f, 0L), "no price set: free");
        long slight = WandCare.price(0.95f, FULL);
        long half = WandCare.price(0.55f, FULL);
        long brink = WandCare.price(0.1001f, FULL);
        assertTrue(slight < half && half < brink, () -> slight + " < " + half + " < " + brink);
        assertTrue(brink >= FULL && brink < FULL + CurrencyHelper.KNUTS_PER_SICKLE,
                () -> "a wand on the brink costs the full price, to the Sickle: " + brink);
    }

    @Test
    void thePriceIsAlwaysWholeSicklesAndAtLeastOne() {
        for (int i = 11; i < 100; i++) {
            long price = WandCare.price(i / 100.0f, FULL);
            assertEquals(0L, price % CurrencyHelper.KNUTS_PER_SICKLE, "integrity " + i + "%: " + price);
            assertTrue(price >= CurrencyHelper.KNUTS_PER_SICKLE);
        }
        assertEquals(CurrencyHelper.KNUTS_PER_SICKLE, WandCare.price(0.999f, FULL), "a scratch costs one Sickle");
    }

    @Test
    void endorsementFeesClimbWithRankAndSumToTheCanonCourse() {
        assertEquals(0L, LicenceUpgrade.fee(0, FULL));
        assertEquals(986L, LicenceUpgrade.fee(1, FULL));
        assertEquals(1972L, LicenceUpgrade.fee(2, FULL));
        assertEquals(2958L, LicenceUpgrade.fee(3, FULL));
        long ladder = LicenceUpgrade.fee(1, FULL) + LicenceUpgrade.fee(2, FULL) + LicenceUpgrade.fee(3, FULL);
        assertEquals(CurrencyHelper.toKnuts(12, 0, 0), ladder, "twelve Galleons: the Apparition course (HBP ch. 17)");
        assertEquals(0L, LicenceUpgrade.fee(2, 0L), "no fee set: stationery only");
    }
}
