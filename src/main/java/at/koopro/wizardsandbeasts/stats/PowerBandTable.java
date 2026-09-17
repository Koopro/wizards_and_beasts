package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.util.RandomSource;

/**
 * The band a character's POWER is rolled inside, and how far training can carry it.
 *
 * <p><b>One band for everybody with magic.</b> Blood status is culture and prejudice, not power: canon says so
 * outright — Hermione Granger is Muggle-born and the best witch of her year, while Crabbe and Goyle are as
 * pure-blooded as anyone alive. The table used to hand pure-bloods 30–85, half-bloods 25–75 and Muggle-borns 20–70,
 * which is Death Eater ideology expressed as a dice range. Every lineage now rolls the same 20–85.
 *
 * <p>Two real exceptions remain, both physical rather than social:
 * <ul>
 *   <li>a <b>Squib</b> has no magic at all, so 0–10 with no growth;</li>
 *   <li>a <b>prodigy</b> roll (1.5%) reaches 70–95, which is luck, not lineage.</li>
 * </ul>
 *
 * <p>Conditions — lycanthropy, an Obscurus — never touch POWER. A bitten wizard is the same wizard.
 */
public final class PowerBandTable {

    private static final float PRODIGY_CHANCE = 0.015f;
    private static final PowerBand PRODIGY_BAND = new PowerBand(70, 95, 15);

    /** What any witch or wizard rolls, whatever family they were born into. */
    private static final PowerBand WIZARD_BAND = new PowerBand(20, 85, 15);

    /** No magic to roll and none to train. */
    private static final PowerBand SQUIB_BAND = new PowerBand(0, 10, 0);

    private static final String SQUIB = "squib";

    private record PowerBand(int min, int max, int growthCap) {}

    private PowerBandTable() {}

    private static PowerBand bandFor(HeritageVariant variant) {
        return SQUIB.equals(variant.getId()) ? SQUIB_BAND : WIZARD_BAND;
    }

    /** The growth cap for a lineage: 15 for everyone with magic, 0 for a Squib. */
    public static int getGrowthCap(HeritageVariant variant) {
        return bandFor(variant).growthCap();
    }

    /** The highest POWER this lineage can roll. */
    public static int getBandMax(HeritageVariant variant) {
        return bandFor(variant).max();
    }

    /** The lowest POWER this lineage can roll. */
    public static int getBandMin(HeritageVariant variant) {
        return bandFor(variant).min();
    }

    public static PowerRollResult rollInitialPower(HeritageVariant variant, RandomSource random) {
        PowerBand band = bandFor(variant);

        // A Squib has no magic: no prodigy roll, no growth, a hard cap.
        if (band == SQUIB_BAND) {
            return new PowerRollResult(random.nextIntBetweenInclusive(band.min(), band.max()), false);
        }

        if (random.nextFloat() < PRODIGY_CHANCE) {
            return new PowerRollResult(
                    random.nextIntBetweenInclusive(PRODIGY_BAND.min(), PRODIGY_BAND.max()),
                    true);
        }

        return new PowerRollResult(random.nextIntBetweenInclusive(band.min(), band.max()), false);
    }

    public record PowerRollResult(int power, boolean isProdigy) {}
}
