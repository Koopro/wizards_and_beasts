package at.koopro.wizardsandbeasts.market;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trader's arithmetic. The load-bearing test is {@link #farmingCannotOutrunTheDailyCeiling}: however fast a good
 * is sold, a market pays no more per day than its data's ceiling — the property that makes the market an
 * anti-inflation device instead of a money press.
 */
class MarketPricingTest {

    private static final long DAY = MarketPricing.TICKS_PER_DAY;

    @Test
    void pressureHalvesEveryHalfLife() {
        assertEquals(40.0, MarketPricing.decay(80.0, 2 * DAY, 2.0), 1e-9);
        assertEquals(20.0, MarketPricing.decay(80.0, 4 * DAY, 2.0), 1e-9);
        assertEquals(80.0, MarketPricing.decay(80.0, 0, 2.0), 1e-9);
        assertEquals(0.0, MarketPricing.decay(-3.0, DAY, 2.0), 1e-9, "negative pressure is no pressure");
        assertEquals(80.0, MarketPricing.decay(80.0, -DAY, 2.0), 1e-9, "a clock running backwards changes nothing");
    }

    @Test
    void sellingLowersThePriceAndBuyingRaisesIt() {
        double previousSell = Double.MAX_VALUE;
        double previousBuy = 0.0;
        for (int p = 0; p < 100; p++) {
            double sell = MarketPricing.sellUnit(100, 8, p);
            double buy = MarketPricing.buyUnit(100, 8, p);
            assertTrue(sell < previousSell, "sell price did not fall at pressure " + p);
            assertTrue(buy > previousBuy, "buy price did not rise at pressure " + p);
            assertTrue(buy >= sell, "a trader sold for less than it pays at pressure " + p);
            previousSell = sell;
            previousBuy = buy;
        }
        assertEquals(50.0, MarketPricing.sellUnit(100, 8, 8), 1e-9, "depth units halve the price");
        assertEquals(200.0, MarketPricing.buyUnit(100, 8, 8), 1e-9, "depth units double the asking price");
    }

    @Test
    void aBatchIsPricedUnitByUnitAndStopsWhenWorthless() {
        MarketPricing.Batch batch = MarketPricing.sell(12, 32, 0, 10, 1.0);
        long expected = 0;
        for (int i = 0; i < 10; i++) {
            expected += (long) Math.floor(12.0 * 32 / (32 + i));
        }
        assertEquals(10, batch.units());
        assertEquals(expected, batch.knuts());
        assertEquals(10.0, batch.pressureAfter(), 1e-9);

        MarketPricing.Batch flooded = MarketPricing.sell(6, 4, 0, 1000, 1.0);
        assertTrue(flooded.units() < 1000, "a saturated trader kept buying worthless units");
        assertTrue(flooded.knuts() <= 6L * flooded.units());

        MarketPricing.Batch bought = MarketPricing.buy(290, 4, 0, 3, 1.0);
        assertEquals(290 + (long) Math.ceil(290 * 5 / 4.0) + (long) Math.ceil(290 * 6 / 4.0), bought.knuts());
    }

    @Test
    void sellingAndBuyingBackIsAlwaysALoss() {
        // Same good, same trader, any pressure and batch: buying n back costs more than selling n paid.
        for (int depth : new int[] {1, 2, 4, 16, 64}) {
            for (double pressure : new double[] {0, 1, 7.5, 40}) {
                for (int n : new int[] {1, 3, 20}) {
                    MarketPricing.Batch sold = MarketPricing.sell(87, depth, pressure, n, 1.0);
                    MarketPricing.Batch rebought = MarketPricing.buy(87, depth, sold.pressureAfter(), sold.units(), 1.0);
                    assertTrue(rebought.knuts() > sold.knuts() || sold.units() == 0,
                            "round trip profited at depth " + depth + ", pressure " + pressure + ", n " + n);
                }
            }
        }
    }

    @Test
    void farmingCannotOutrunTheDailyCeiling() {
        // Dump r units a day, every day, for sixty days (long past steady state), at a shipped-like good.
        double base = 87;
        int depth = 8;
        double halfLife = 2.0;
        double ceiling = MarketPricing.dailyIncomeCeiling(base, depth, halfLife);
        for (int perDay : new int[] {1, 8, 64, 1000, 100_000}) {
            double pressure = 0.0;
            long lastDay = 0;
            for (int day = 0; day < 60; day++) {
                pressure = MarketPricing.decay(pressure, DAY, halfLife);
                MarketPricing.Batch batch = MarketPricing.sell(base, depth, pressure, perDay, 1.0);
                pressure = batch.pressureAfter();
                lastDay = batch.knuts();
            }
            assertTrue(lastDay <= ceiling * 1.5 + base,
                    perDay + " units/day earned " + lastDay + " against a ceiling of " + ceiling);
        }
        // And the analytic steady state never reaches it.
        for (double rate : new double[] {1, 10, 1e3, 1e6}) {
            assertTrue(MarketPricing.steadyDailyIncome(base, depth, halfLife, rate) < ceiling);
        }
    }

    @Test
    void theShortGoodIsStableForADayAndMoves() {
        Set<Integer> seen = new HashSet<>();
        for (long day = 0; day < 200; day++) {
            int index = MarketPricing.inDemandToday(9, day, 1234L);
            assertTrue(index >= 0 && index < 9);
            assertEquals(index, MarketPricing.inDemandToday(9, day, 1234L), "the same day gave two answers");
            seen.add(index);
        }
        assertTrue(seen.size() >= 7, "the demand barely moves: " + seen);
        assertEquals(-1, MarketPricing.inDemandToday(0, 5, 1L));
    }

    @Test
    void woodRarityPricesWandsAndNeverTheElderWand() {
        assertEquals(1.0, MarketGoods.rarityMultiplier("common"));
        assertTrue(MarketGoods.rarityMultiplier("rare") > MarketGoods.rarityMultiplier("uncommon"));
        assertEquals(0.0, MarketGoods.rarityMultiplier("legendary"), "an elder-grade wood is never bought");
    }
}
