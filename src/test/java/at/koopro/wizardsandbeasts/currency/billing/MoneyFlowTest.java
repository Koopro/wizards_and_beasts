package at.koopro.wizardsandbeasts.currency.billing;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money flows and the ledger that totals them: every flow is named and classified, credits are exactly the flows
 * that pay in, and the ledger's totals add, reverse and survive a save.
 */
class MoneyFlowTest {

    @Test
    void everyFlowHasItsText() throws IOException {
        Set<String> keys;
        try (Reader reader = Files.newBufferedReader(Path.of("src", "main", "resources", "assets", "wizards_and_beasts",
                "lang", "en_us.json"))) {
            keys = JsonParser.parseReader(reader).getAsJsonObject().keySet();
        }
        for (MoneyFlow flow : MoneyFlow.values()) {
            assertTrue(keys.contains(flow.translationKey()), flow.translationKey());
            assertSame(flow, MoneyFlow.byId(flow.id()));
        }
    }

    @Test
    void creditsAreTheFlowsThatPayIn() {
        for (MoneyFlow flow : MoneyFlow.values()) {
            if (flow.kind() == MoneyFlow.Kind.SOURCE) {
                assertTrue(flow.credits(), flow + " creates money, so it must pay in");
            }
            if (flow.kind() == MoneyFlow.Kind.SINK) {
                assertFalse(flow.credits(), flow + " destroys money, so it must charge");
            }
        }
        assertTrue(MoneyFlow.DRAGOT_SALE.credits());
        assertFalse(MoneyFlow.DRAGOT_PURCHASE.credits());
    }

    @Test
    void theGameHasMoreThanOneSinkAndASource() {
        long sinks = java.util.Arrays.stream(MoneyFlow.values()).filter(f -> f.kind() == MoneyFlow.Kind.SINK).count();
        assertTrue(sinks >= 6, "sinks: " + sinks);
        assertTrue(java.util.Arrays.stream(MoneyFlow.values()).anyMatch(f -> f.kind() == MoneyFlow.Kind.SOURCE));
    }

    @Test
    void theLedgerAddsReversesAndKeepsKindsApart() {
        GringottsLedger ledger = new GringottsLedger();
        ledger.record(MoneyFlow.CAREER_WAGE, 493);
        ledger.record(MoneyFlow.CAREER_WAGE, 406);
        ledger.record(MoneyFlow.WAND_CARE, 116);
        ledger.record(MoneyFlow.FLOO_REGISTRATION, 493);
        ledger.reverse(MoneyFlow.FLOO_REGISTRATION, 493); // the hearth failed after payment and was refunded
        ledger.record(MoneyFlow.ADMIN_DEPOSIT, 100_000);

        assertEquals(new GringottsLedger.Totals(2, 899), ledger.totals(MoneyFlow.CAREER_WAGE));
        assertEquals(GringottsLedger.Totals.NONE, ledger.totals(MoneyFlow.FLOO_REGISTRATION));
        assertEquals(899, ledger.knutsOf(MoneyFlow.Kind.SOURCE));
        assertEquals(116, ledger.knutsOf(MoneyFlow.Kind.SINK));
        assertEquals(100_000, ledger.knutsOf(MoneyFlow.Kind.ADMIN), "an administrator's hand never reads as gameplay");
    }

    @Test
    void circulationAndTheBlackMarketAreKeptApart() {
        assertTrue(MoneyFlow.COUNTER_DEPOSIT.credits() && !MoneyFlow.COUNTER_WITHDRAWAL.credits());
        assertEquals(MoneyFlow.Kind.CIRCULATION, MoneyFlow.COUNTER_DEPOSIT.kind());
        assertEquals(MoneyFlow.Kind.SOURCE, MoneyFlow.ILLICIT_SALE.kind());
        assertEquals(MoneyFlow.Kind.SINK, MoneyFlow.ILLICIT_PURCHASE.kind());
        GringottsLedger ledger = new GringottsLedger();
        ledger.record(MoneyFlow.COUNTER_DEPOSIT, 5_000);
        ledger.record(MoneyFlow.MARKET_SALE, 300);
        ledger.record(MoneyFlow.CERTIFICATION, 986);
        assertEquals(300, ledger.knutsOf(MoneyFlow.Kind.SOURCE), "pocket coins paid in read as money made");
        assertEquals(986, ledger.knutsOf(MoneyFlow.Kind.SINK));
        assertEquals(5_000, ledger.knutsOf(MoneyFlow.Kind.CIRCULATION));
    }

    @Test
    void theLedgerSurvivesASave() {
        GringottsLedger ledger = new GringottsLedger();
        ledger.record(MoneyFlow.LICENCE_ENDORSEMENT, 986);
        ledger.record(MoneyFlow.MINISTRY_FINE, 50);
        var json = GringottsLedger.TYPE.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, ledger)
                .getOrThrow();
        GringottsLedger loaded = GringottsLedger.TYPE.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                .getOrThrow();
        assertEquals(ledger.totals(MoneyFlow.LICENCE_ENDORSEMENT), loaded.totals(MoneyFlow.LICENCE_ENDORSEMENT));
        assertEquals(ledger.knutsOf(MoneyFlow.Kind.SINK), loaded.knutsOf(MoneyFlow.Kind.SINK));
    }
}
