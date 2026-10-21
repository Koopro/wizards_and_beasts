package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.location.ServiceOffer;
import at.koopro.wizardsandbeasts.treasure.CurseBreaking.Outcome;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Curse-breaking arithmetic, artefact state, service prices, and the treasure-site data joined to what it names. */
class TreasureRulesTest {

    private static final Path DATA = Path.of("src/main/resources/data/wizards_and_beasts");
    private static final Identifier SITE = Identifier.fromNamespaceAndPath("wizards_and_beasts", "desert_tomb");

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TreasureArtefact cursed(CurseKind kind, int layers) {
        return new TreasureArtefact(SITE, Optional.of(kind), layers, false, 600);
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void revelioReadsOnceAndLiftsNothing() {
        TreasureArtefact jinx = cursed(CurseKind.JINX, 2);
        assertEquals(Outcome.IDENTIFIED, CurseBreaking.judge(jinx, "revelio"));
        TreasureArtefact read = CurseBreaking.apply(jinx, Outcome.IDENTIFIED);
        assertTrue(read.identified());
        assertEquals(2, read.layers());
        assertEquals(Outcome.NONE, CurseBreaking.judge(read, "wizards_and_beasts:revelio"));
    }

    @Test
    void theRightCounterLiftsALayerAndTheLastBreaksIt() {
        TreasureArtefact jinx = cursed(CurseKind.JINX, 2);
        assertEquals(Outcome.LAYER_LIFTED, CurseBreaking.judge(jinx, "finite_incantatem"));
        TreasureArtefact one = CurseBreaking.apply(jinx, Outcome.LAYER_LIFTED);
        assertEquals(1, one.layers());
        assertEquals(Outcome.BROKEN, CurseBreaking.judge(one, "wizards_and_beasts:finite_incantatem"));
        TreasureArtefact clean = CurseBreaking.apply(one, Outcome.BROKEN);
        assertFalse(clean.cursed());
        assertTrue(clean.curse().isEmpty());
        assertEquals(Outcome.NONE, CurseBreaking.judge(clean, "finite_incantatem"));
    }

    @Test
    void aWrongCounterFlaresAndAnyOtherSpellIsNoAttempt() {
        TreasureArtefact flagrante = cursed(CurseKind.FLAGRANTE, 3);
        assertEquals(Outcome.BACKLASH, CurseBreaking.judge(flagrante, "finite_incantatem"));
        assertEquals(flagrante, CurseBreaking.apply(flagrante, Outcome.BACKLASH));
        assertEquals(Outcome.NONE, CurseBreaking.judge(flagrante, "stupefy"));
        assertEquals(Outcome.NONE, CurseBreaking.judge(flagrante, "lumos"));
    }

    @Test
    void everyCurseHasItsOwnCounter() {
        java.util.Set<String> counters = new java.util.HashSet<>();
        for (CurseKind kind : CurseKind.values()) {
            assertTrue(counters.add(kind.counterSpell()), "two curses share " + kind.counterSpell());
            assertEquals(kind, CurseKind.counteredBy("wizards_and_beasts:" + kind.counterSpell()));
        }
    }

    /** A counter that is registered but COMING_SOON could never be cast: the curse could never be broken. */
    @Test
    void everyCounterIsACastableSpell() throws IOException {
        for (CurseKind kind : CurseKind.values()) {
            Path spell = DATA.resolve("spells/" + kind.counterSpell() + ".json");
            assertTrue(Files.exists(spell), "no spell " + kind.counterSpell());
            assertFalse(Files.readString(spell).contains("coming_soon"), kind.counterSpell() + " is not castable");
        }
        assertTrue(Files.exists(DATA.resolve("spells/revelio.json")));
        assertFalse(Files.readString(DATA.resolve("spells/revelio.json")).contains("coming_soon"));
    }

    @Test
    void anArtefactWithNoLayersIsClean() {
        TreasureArtefact odd = new TreasureArtefact(SITE, Optional.of(CurseKind.SEALING), 0, false, -5);
        assertFalse(odd.cursed());
        assertEquals(0, odd.valueKnuts());
        TreasureArtefact round = TreasureArtefact.CODEC.parse(JsonOps.INSTANCE,
                TreasureArtefact.CODEC.encodeStart(JsonOps.INSTANCE, cursed(CurseKind.WITHERING, 3)).getOrThrow())
                .getOrThrow();
        assertEquals(cursed(CurseKind.WITHERING, 3), round);
    }

    @Test
    void serviceDiscountsFloorAtHalfAndRatesClamp() {
        ServiceOffer offer = new ServiceOffer(SITE, "customer", 100, 0.2f, 0.4f, -0.05f);
        assertEquals(100, offer.feeFor(0));
        assertEquals(80, offer.feeFor(1));
        assertEquals(50, offer.feeFor(10));
        assertEquals(0.4f, offer.rateFor(0), 1e-6);
        assertEquals(0.3f, offer.rateFor(2), 1e-6);
        assertEquals(0.0f, offer.rateFor(20), 1e-6);
        assertEquals(1.0f, new ServiceOffer(SITE, "customer", 0, 0f, 0.9f, 0.5f).rateFor(3), 1e-6);
    }

    @Test
    void shippedSitesJoinTheirItemsTagsAndTables() throws IOException {
        try (Stream<Path> files = Files.list(DATA.resolve("treasure_sites"))) {
            List<Path> sites = files.sorted().toList();
            assertTrue(sites.size() >= 5, "sites: " + sites.size());
            for (Path file : sites) {
                String name = file.getFileName().toString();
                TreasureSite site = TreasureSite.CODEC.parse(JsonOps.INSTANCE, read(file)).getOrThrow();
                assertEquals(List.of(), site.validate(), name);
                assertTrue(Files.exists(DATA.resolve("tags/worldgen/structure/" + site.structures().getPath() + ".json")),
                        name + ": no structure tag " + site.structures());
                for (Identifier item : site.artefacts()) {
                    assertTrue(BuiltInRegistries.ITEM.containsKey(item), name + ": no item " + item);
                    assertTrue(BuiltInRegistries.ITEM.getValue(item) instanceof TreasureArtefactItem,
                            name + ": " + item + " is not an artefact");
                }
                for (Identifier table : site.lootTables()) {
                    assertTrue(table.getPath().startsWith("chests/"), name + ": " + table + " is not a chest");
                }
                TreasureArtefact rolled = site.roll(SITE, RandomSource.create(7));
                assertTrue(rolled.cursed() && rolled.layers() >= site.layers().min()
                        && rolled.layers() <= site.layers().max(), name + ": rolled " + rolled);
            }
        }
    }
}
