package at.koopro.wizardsandbeasts.market;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.ExplorationFees;
import at.koopro.wizardsandbeasts.bestiary.data.EntryHistory;
import at.koopro.wizardsandbeasts.currency.billing.MoneyJournal;
import at.koopro.wizardsandbeasts.owl.career.track.CareerContext;
import at.koopro.wizardsandbeasts.owl.career.track.CareerGoal;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityFilter;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the economy saves, and the small rules around it: market pressure and the player's trade record survive a
 * save, weekly limits roll over, the money journal stays bounded, a malformed good is refused at load, a
 * certification fee reads the vault, and the finder's fee never pays twice.
 */
class MarketStateTest {

    @Test
    void pressureSurvivesASave() {
        MarketBook book = new MarketBook();
        String key = MarketBook.key("wizards_and_beasts:apothecary", MarketDefinition.Side.BUYS, "dittany");
        book.set(key, 12.5, 48_000L);
        var json = MarketBook.TYPE.codec().encodeStart(JsonOps.INSTANCE, book).getOrThrow();
        MarketBook loaded = MarketBook.TYPE.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(12.5, loaded.pressure(key, 48_000L, 2.0), 1e-9);
        assertEquals(6.25, loaded.pressure(key, 48_000L + 2 * MarketPricing.TICKS_PER_DAY, 2.0), 1e-9);
        assertEquals(1, loaded.reset("wizards_and_beasts:apothecary|"));
        assertEquals(0.0, loaded.pressure(key, 48_000L, 2.0));
    }

    @Test
    void weeklyPurchasesRollOverAndTotalsAccumulate() {
        PlayerMarketRecord record = PlayerMarketRecord.EMPTY
                .traded("k", 2, 348, true, 10)
                .traded("k", 1, 174, true, 10);
        assertEquals(3, record.line("k").boughtIn(10));
        assertEquals(0, record.line("k").boughtIn(11), "last week's purchases count against this week");
        PlayerMarketRecord next = record.traded("k", 1, 174, true, 11);
        assertEquals(1, next.line("k").boughtIn(11));
        assertEquals(4, next.line("k").units());
        assertEquals(696, next.line("k").knuts());
        PlayerMarketRecord sold = next.traded("k", 5, 60, false, 11);
        assertEquals(1, sold.line("k").boughtIn(11), "a sale counted as a purchase");

        var json = PlayerMarketRecord.CODEC.encodeStart(JsonOps.INSTANCE, sold).getOrThrow();
        assertEquals(sold, PlayerMarketRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void theJournalStaysBoundedAndSurvivesASave() {
        MoneyJournal journal = MoneyJournal.EMPTY;
        for (int i = 0; i < MoneyJournal.CAPACITY + 10; i++) {
            journal = journal.with(new MoneyJournal.Entry("market_sale", i, i));
        }
        assertEquals(MoneyJournal.CAPACITY, journal.entries().size());
        assertEquals(10, journal.entries().getFirst().knuts(), "the oldest entries were not the ones dropped");
        var json = MoneyJournal.CODEC.encodeStart(JsonOps.INSTANCE, journal).getOrThrow();
        assertEquals(journal, MoneyJournal.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void aMalformedGoodIsRefusedAtLoad() {
        for (String bad : new String[] {
                "{\"id\":\"x\",\"price_knuts\":10}",                                       // item good with no item
                "{\"id\":\"x\",\"item\":\"minecraft:stick\"}",                              // no price
                "{\"id\":\"x\",\"kind\":\"wand\",\"price_knuts\":10}",                      // wand with no condition
                "{\"id\":\"x\",\"kind\":\"treasure\"}",                                     // treasure with no rate
                "{\"id\":\"Bad Id\",\"item\":\"minecraft:stick\",\"price_knuts\":1}",       // id not snake_case
                "{\"id\":\"x\",\"item\":\"minecraft:stick\",\"price_knuts\":1,\"depth\":0}" // no depth
        }) {
            assertTrue(MarketGood.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(bad)).isError(), bad);
        }
        assertFalse(MarketGood.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"id\":\"ok\",\"item\":\"minecraft:stick\",\"price_knuts\":3}")).isError());
        MarketDefinition sellsAWand = MarketDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"sells\":[{\"id\":\"w\",\"kind\":\"wand\",\"condition\":\"unbonded\",\"price_knuts\":9}]}")).getOrThrow();
        assertFalse(sellsAWand.validate(Set.of()).isEmpty(), "a trader may only sell plain items");
    }

    @Test
    void aCertificationFeeReadsTheVault() {
        CareerGoal fee = CareerGoal.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"fee\",\"knuts\":986}")).getOrThrow();
        assertEquals(986, fee.target());
        assertFalse(fee.met(context(500)));
        assertTrue(fee.met(context(986)));
        assertTrue(fee.met(context(Long.MAX_VALUE)), "an unlimited vault (Gringotts closed) waits on a fee");
    }

    private static CareerContext context(long vault) {
        return new CareerContext() {
            @Override public OWLGrade grade(OWLSubject subject) { return OWLGrade.T; }
            @Override public int pages(DiscoveryTier minTier, int minRating) { return 0; }
            @Override public int researchCompleted() { return 0; }
            @Override public int activityTotal(ActivityFilter filter, boolean distinct) { return 0; }
            @Override public int contractsCompleted() { return 0; }
            @Override public long vaultKnuts() { return vault; }
        };
    }

    @Test
    void theFindersFeeIsPaidOnceAndScalesWithDanger() {
        assertTrue(ExplorationFees.baseFee(5) > ExplorationFees.baseFee(4));
        assertTrue(ExplorationFees.baseFee(2) > ExplorationFees.baseFee(1));
        assertTrue(ExplorationFees.baseFee(0) > 0);
        EntryHistory glimpsed = EntryHistory.NONE.reached(DiscoveryTier.ENCOUNTERED, 1, "", "");
        assertFalse(ExplorationFees.alreadyRecorded(glimpsed));
        assertTrue(ExplorationFees.alreadyRecorded(glimpsed.reached(DiscoveryTier.OBSERVED, 2, "", "")));
        assertTrue(ExplorationFees.alreadyRecorded(EntryHistory.NONE.reached(DiscoveryTier.KNOWN, 2, "", "")),
                "a page that jumped straight to Known was paid and must not pay again");
        assertEquals(Map.of(), EntryHistory.NONE.reachedDay());
    }
}
