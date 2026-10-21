package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.data.EntryHistory;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.network.bestiary.BestiaryDataSyncPayload;
import at.koopro.wizardsandbeasts.network.bestiary.SyncBestiaryEntriesPayload;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the server knows about a player's journal reaches the client whole and unchanged, and old saves still load.
 * The payloads are run through their real stream codecs on a real buffer.
 */
class JournalSyncTest {

    private static final Identifier NIFFLER = Identifier.fromNamespaceAndPath("wizards_and_beasts", "niffler");

    private static PlayerBestiaryData sample() {
        Map<Identifier, DiscoveryTier> tiers = new HashMap<>(Map.of(NIFFLER, DiscoveryTier.STUDIED));
        Map<Identifier, Set<String>> notes = new HashMap<>(Map.of(NIFFLER, Set.of("treasure-seeker")));
        EntryHistory history = EntryHistory.NONE
                .reached(DiscoveryTier.ENCOUNTERED, 3, "minecraft:badlands", "minecraft:overworld")
                .reached(DiscoveryTier.OBSERVED, 4, "minecraft:plains", "minecraft:overworld")
                .withAct("feed");
        return new PlayerBestiaryData(tiers, new HashMap<>(Map.of(NIFFLER, 1200L)), new HashMap<>(Map.of(NIFFLER, 600)),
                notes, new HashMap<>(Map.of(NIFFLER, history)));
    }

    @Test
    void historyRecordsTheFirstMeetingOnlyOnce() {
        EntryHistory h = EntryHistory.NONE.reached(DiscoveryTier.ENCOUNTERED, 3, "minecraft:badlands", "minecraft:overworld")
                .reached(DiscoveryTier.OBSERVED, 9, "minecraft:plains", "minecraft:the_nether");
        assertEquals(3, h.firstSeenDay());
        assertEquals("minecraft:badlands", h.firstSeenBiome(), "a later depth overwrote where it was first met");
        assertEquals(9, h.dayReached(DiscoveryTier.OBSERVED));
        assertEquals(-1, h.dayReached(DiscoveryTier.KNOWN));
        assertEquals(h, h.reached(DiscoveryTier.OBSERVED, 20, "x", "y"), "a depth reached twice keeps its first day");
    }

    @Test
    void thePlayerRecordSurvivesTheWireWhole() {
        PlayerBestiaryData data = sample();
        ByteBuf buf = Unpooled.buffer();
        BestiaryDataSyncPayload.STREAM_CODEC.encode(buf, new BestiaryDataSyncPayload(data));
        PlayerBestiaryData received = BestiaryDataSyncPayload.STREAM_CODEC.decode(buf).data();
        assertEquals(data.tiers(), received.tiers());
        assertEquals(data.fieldNotes(), received.fieldNotes());
        assertEquals(data.history(), received.history());
        assertEquals(data.observation(), received.observation());
    }

    @Test
    void aSaveFromBeforeTheJournalLoadsWithNoHistory() {
        PlayerBestiaryData old = PlayerBestiaryData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"tier_levels\":{\"wizards_and_beasts:niffler\":2}}")).getOrThrow();
        assertEquals(DiscoveryTier.OBSERVED, old.tiers().get(NIFFLER));
        assertEquals(EntryHistory.NONE, old.historyOf(NIFFLER));
        PlayerBestiaryData back = PlayerBestiaryData.CODEC.parse(JsonOps.INSTANCE,
                PlayerBestiaryData.CODEC.encodeStart(JsonOps.INSTANCE, sample()).getOrThrow()).getOrThrow();
        assertEquals(sample().history(), back.history());
    }

    @Test
    void theBookItsLayoutAndItsFactsSurviveTheWire() throws Exception {
        BestiaryEntry niffler = BestiaryEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/data/wizards_and_beasts/bestiary/entries/niffler.json")))).getOrThrow();
        JournalFacts facts = new JournalFacts("wary", List.of("treasure-seeker", "curious"),
                List.of(Identifier.withDefaultNamespace("gold_nugget")), true, Optional.empty(),
                List.of(new JournalFacts.Harvest(Identifier.withDefaultNamespace("gold_ingot"), DiscoveryTier.STUDIED, 600)));
        SyncBestiaryEntriesPayload sent = new SyncBestiaryEntriesPayload(List.of(niffler), JournalLayout.DEFAULT,
                Map.of(niffler.id(), facts));
        ByteBuf buf = Unpooled.buffer();
        SyncBestiaryEntriesPayload.STREAM_CODEC.encode(buf, sent);
        SyncBestiaryEntriesPayload got = SyncBestiaryEntriesPayload.STREAM_CODEC.decode(buf);
        assertEquals(sent.entries(), got.entries());
        assertEquals(sent.layout(), got.layout());
        assertEquals(sent.facts(), got.facts());
        assertTrue(got.entries().getFirst().journal().signatureCaption().isPresent(), "the journal block was lost");
    }
}
