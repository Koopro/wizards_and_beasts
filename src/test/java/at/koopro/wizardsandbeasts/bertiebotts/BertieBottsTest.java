package at.koopro.wizardsandbeasts.bertiebotts;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape of the bag.
 *
 * <p>{@link #theBagIsNotACoinFlip} is the one that matters. The brief's complaint about the old item
 * was that fifty-fifty reads as a gamble rather than as a joke, and the fix is only real if the
 * outcome space stays wide — a table that quietly collapsed back to two or three reachable flavours
 * would have the same problem with more code.
 */
class BertieBottsTest {

    @Test
    void theTierWeightsAreTheBriefedPercentages() {
        assertEquals(40, BeanTier.COMMON_PLEASANT.weight());
        assertEquals(35, BeanTier.COMMON_UNPLEASANT.weight());
        assertEquals(15, BeanTier.RARE_GOOD.weight());
        assertEquals(8, BeanTier.RARE_BAD.weight());
        assertEquals(2, BeanTier.LEGENDARY.weight());
    }

    @Test
    void theWeightsSumToOneHundredSoEachReadsAsAPercentage() {
        int total = 0;
        for (BeanTier tier : BeanTier.values()) {
            total += tier.weight();
        }
        assertEquals(BeanTier.TOTAL_WEIGHT, total);
        assertEquals(100, total, "weights are meant to be readable as percentages");
    }

    @Test
    void everyRollLandsInExactlyTheAdvertisedBand() {
        Map<BeanTier, Integer> seen = new EnumMap<>(BeanTier.class);
        for (int roll = 0; roll < BeanTier.TOTAL_WEIGHT; roll++) {
            seen.merge(BeanTier.byRoll(roll), 1, Integer::sum);
        }
        for (BeanTier tier : BeanTier.values()) {
            assertEquals(tier.weight(), seen.getOrDefault(tier, 0),
                    tier + " should occupy exactly " + tier.weight() + " of 100 rolls");
        }
    }

    @Test
    void everyTierHasSomethingInIt() {
        // An empty tier is a slice of the bag that silently falls back to honey.
        for (BeanTier tier : BeanTier.values()) {
            assertFalse(BeanFlavour.inTier(tier).isEmpty(), tier + " has no flavours");
        }
    }

    @Test
    void theBagIsNotACoinFlip() {
        RandomSource random = RandomSource.create(20260827L);
        Set<BeanFlavour> drawn = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            drawn.add(BertieBotts.draw(random));
        }
        assertEquals(BeanFlavour.values().length, drawn.size(),
                "every flavour must be reachable; the old item had two");
        assertTrue(drawn.size() >= 8,
                "\"every flavour\" needs to mean more than a handful; got " + drawn.size());
    }

    @Test
    void theObservedTierMixMatchesTheTable() {
        RandomSource random = RandomSource.create(11L);
        Map<BeanTier, Integer> counts = new EnumMap<>(BeanTier.class);
        int trials = 200_000;
        for (int i = 0; i < trials; i++) {
            counts.merge(BertieBotts.draw(random).tier(), 1, Integer::sum);
        }
        for (BeanTier tier : BeanTier.values()) {
            double observed = counts.getOrDefault(tier, 0) / (double) trials * 100.0;
            assertEquals(tier.weight(), observed, 1.0,
                    tier + " came out at " + observed + "%, expected " + tier.weight() + "%");
        }
    }

    @Test
    void flavoursInsideATierAreDrawnEvenly() {
        // Adding a flavour to a tier should make its siblings rarer without changing the tier's own
        // odds; that only holds if the within-tier draw is uniform.
        List<BeanFlavour> unpleasant = BeanFlavour.inTier(BeanTier.COMMON_UNPLEASANT);
        Map<BeanFlavour, Integer> counts = new EnumMap<>(BeanFlavour.class);
        int size = unpleasant.size();
        for (int index = 0; index < size * 100; index++) {
            counts.merge(BertieBotts.drawFrom(BeanTier.COMMON_UNPLEASANT, index), 1, Integer::sum);
        }
        for (BeanFlavour flavour : unpleasant) {
            assertEquals(100, counts.getOrDefault(flavour, 0), flavour + " was not drawn evenly");
        }
    }

    @Test
    void aNegativeIndexStillPicksARealFlavour() {
        // floorMod, not %, or a negative index throws in the middle of somebody's snack.
        BeanFlavour flavour = BertieBotts.drawFrom(BeanTier.RARE_BAD, -3);
        assertTrue(BeanFlavour.inTier(BeanTier.RARE_BAD).contains(flavour));
    }

    @Test
    void thePleasantAndUnpleasantHalvesAreNearlyBalanced() {
        // The joke needs the bad ones to be nearly as likely as the good ones. If pleasantness ever
        // ran away with the table, the beans stop being a risk worth talking about.
        int good = BeanTier.COMMON_PLEASANT.weight() + BeanTier.RARE_GOOD.weight();
        int bad = BeanTier.COMMON_UNPLEASANT.weight() + BeanTier.RARE_BAD.weight();
        assertTrue(Math.abs(good - bad) <= 15,
                "good " + good + "% vs bad " + bad + "% is too lopsided for Every-Flavour");
    }

    @Test
    void legendaryIsRareEnoughToBeWorthTelling() {
        assertEquals(2, BeanTier.LEGENDARY.weight());
        assertEquals(2, BeanFlavour.inTier(BeanTier.LEGENDARY).size(),
                "the brief names exactly two: Chocolate and Bogey");
    }

    @Test
    void everyFlavourNameIsDistinctSoTheJokeAlwaysLands() {
        Set<String> names = new HashSet<>();
        for (BeanFlavour flavour : BeanFlavour.values()) {
            assertTrue(names.add(flavour.getSerializedName()),
                    "duplicate serialized name: " + flavour.getSerializedName());
        }
    }

    @Test
    void theNutritionIsTheSameWhateverYouDrew() {
        assertEquals(1, BertieBotts.NUTRITION);
        assertEquals(0.15f, BertieBotts.SATURATION, 1e-6f);
        assertEquals(8, BertieBotts.COOLDOWN_TICKS);
    }
}
