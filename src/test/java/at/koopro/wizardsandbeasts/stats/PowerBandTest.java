package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Power bands now that the Heritage selection screen prints them as a {@code min–max}
 * range. A band whose min exceeded its max would render backwards, and a roll outside its own band
 * would contradict the number the player was shown before committing.
 */
class PowerBandTest {

    @Test
    void everyVariantResolvesAnOrderedBand() {
        for (Heritage heritage : Heritage.values()) {
            for (HeritageVariant variant : heritage.getSubtypes()) {
                int min = PowerBandTable.getBandMin(variant);
                int max = PowerBandTable.getBandMax(variant);
                assertTrue(min >= 0, variant.getId() + " band min " + min + " is negative");
                assertTrue(max <= 100, variant.getId() + " band max " + max + " exceeds the stat range");
                assertTrue(min < max,
                        variant.getId() + " band is " + min + "–" + max + ", which renders backwards");
            }
        }
    }

    @Test
    void rolledPowerAlwaysLandsInsideTheAdvertisedBand() {
        // The screen shows the band before the player commits; the roll happens after. If the two
        // disagree the character sheet contradicts the choice screen with nothing to explain it.
        RandomSource random = RandomSource.create(1234L);
        for (Heritage heritage : Heritage.values()) {
            for (HeritageVariant variant : heritage.getSubtypes()) {
                int max = PowerBandTable.getBandMax(variant);
                for (int i = 0; i < 200; i++) {
                    PowerBandTable.PowerRollResult result = PowerBandTable.rollInitialPower(variant, random);
                    assertTrue(result.power() >= 0 && result.power() <= 100,
                            variant.getId() + " rolled " + result.power() + ", outside the stat range");
                    // A prodigy deliberately breaks out of the normal band upward, so only the
                    // ordinary roll is held to the advertised ceiling.
                    if (!result.isProdigy()) {
                        assertTrue(result.power() <= max,
                                variant.getId() + " rolled " + result.power()
                                        + " above its advertised max " + max);
                    }
                }
            }
        }
    }

    @Test
    void squibIsTheOneVariantThatCannotGrow() {
        HeritageVariant squib = Heritage.WIZARDKIND.getSubtypes().stream()
                .filter(v -> "squib".equals(v.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("squib lineage is missing from Wizardkind"));
        assertEquals(0, PowerBandTable.getGrowthCap(squib));
        assertEquals(0, PowerBandTable.getBandMin(squib));
        assertTrue(PowerBandTable.getBandMax(squib) <= 10,
                "a squib must stay near-powerless — band max was " + PowerBandTable.getBandMax(squib));
    }

    @Test
    void bloodStatusBuysNoPower() {
        // The ruling, and canon's: blood purity is a prejudice, not a magical measurement. Every lineage with
        // magic rolls inside one band, so nothing the player picks on the selection screen makes them stronger.
        HeritageVariant reference = null;
        for (Heritage heritage : Heritage.values()) {
            for (HeritageVariant variant : heritage.getSubtypes()) {
                if ("squib".equals(variant.getId())) {
                    continue;
                }
                if (reference == null) {
                    reference = variant;
                    continue;
                }
                assertEquals(PowerBandTable.getBandMin(reference), PowerBandTable.getBandMin(variant),
                        variant.getId() + " rolls from a different floor than " + reference.getId());
                assertEquals(PowerBandTable.getBandMax(reference), PowerBandTable.getBandMax(variant),
                        variant.getId() + " reaches a different ceiling than " + reference.getId());
                assertEquals(PowerBandTable.getGrowthCap(reference), PowerBandTable.getGrowthCap(variant),
                        variant.getId() + " trains to a different cap than " + reference.getId());
            }
        }
    }
}
