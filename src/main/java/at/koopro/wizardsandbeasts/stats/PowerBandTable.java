package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.util.RandomSource;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps HeritageVariant IDs to Power roll bands.
 *
 * <p><b>Deviation note:</b> The prompt specified {@code pure_blood_common} and
 * {@code pure_blood_ancient} as separate variants, but the codebase contains only one
 * {@code pure_blood} Wizardkind variant. It is mapped to a merged band (30–85) covering
 * both ranges. {@code adopted_magical} is not in the prompt table; it receives the
 * muggle-born range (20–70) since it carries no formal magical lineage.
 * Non-Wizardkind variants (Werewolf, Goblin, etc.) are not in the table and fall through
 * to the default (20–70) band with a logged warning.</p>
 */
public final class PowerBandTable {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final float PRODIGY_CHANCE = 0.015f;
    private static final PowerBand PRODIGY_BAND = new PowerBand(70, 95, 15);
    private static final PowerBand DEFAULT_BAND = new PowerBand(20, 70, 15);

    private record PowerBand(int min, int max, int growthCap) {}

    private static final Map<String, PowerBand> BANDS = new HashMap<>();

    static {
        BANDS.put("squib",           new PowerBand(0, 10, 0));
        BANDS.put("muggle_born",     new PowerBand(20, 70, 15));
        BANDS.put("half_blood",      new PowerBand(25, 75, 15));
        // Code has a single pure_blood variant; merged band covers both common (30-70) and ancient (35-85) ranges.
        BANDS.put("pure_blood",      new PowerBand(30, 85, 15));
        // adopted_magical carries no formal lineage; treated as muggle-born equivalent.
        BANDS.put("adopted_magical", new PowerBand(20, 70, 15));
    }

    private PowerBandTable() {}

    /** Returns the growth cap for a given variant. Squib returns 0. Unknown variants return 15. */
    public static int getGrowthCap(HeritageVariant variant) {
        PowerBand band = BANDS.get(variant.getId());
        return band != null ? band.growthCap() : DEFAULT_BAND.growthCap();
    }

    /** Returns the maximum Power this variant can reach (band max). Unknown variants use 70. */
    public static int getBandMax(HeritageVariant variant) {
        PowerBand band = BANDS.get(variant.getId());
        return band != null ? band.max() : DEFAULT_BAND.max();
    }

    /**
     * Returns the lowest Power this variant can roll (band min). Unknown variants use 20.
     *
     * <p>Only the five Wizardkind variants have their own band, so every other heritage's lineages
     * resolve to {@link #DEFAULT_BAND} and report the same 20–70. That is pre-existing behaviour
     * that the selection screen's band readout now makes visible rather than something it changes.
     */
    public static int getBandMin(HeritageVariant variant) {
        PowerBand band = BANDS.get(variant.getId());
        return band != null ? band.min() : DEFAULT_BAND.min();
    }

    public static PowerRollResult rollInitialPower(HeritageVariant variant, RandomSource random) {
        PowerBand band = BANDS.get(variant.getId());

        if (band == null) {
            LOGGER.warn("[WizardsAndBeasts] PowerBandTable: unknown variant '{}', defaulting to (20, 70) band.",
                    variant.getId());
            band = DEFAULT_BAND;
        }

        // Squib: hard cap, no prodigy, no growth.
        if ("squib".equals(variant.getId())) {
            return new PowerRollResult(random.nextIntBetweenInclusive(band.min(), band.max()), false);
        }

        // Prodigy roll — 1.5% chance across all non-Squib heritages.
        if (random.nextFloat() < PRODIGY_CHANCE) {
            return new PowerRollResult(
                    random.nextIntBetweenInclusive(PRODIGY_BAND.min(), PRODIGY_BAND.max()),
                    true);
        }

        return new PowerRollResult(random.nextIntBetweenInclusive(band.min(), band.max()), false);
    }

    public record PowerRollResult(int power, boolean isProdigy) {}
}
