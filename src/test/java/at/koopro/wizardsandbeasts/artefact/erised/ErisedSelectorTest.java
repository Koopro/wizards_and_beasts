package at.koopro.wizardsandbeasts.artefact.erised;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.MagicalCondition;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityFilter;
import at.koopro.wizardsandbeasts.owl.career.track.CareerContext;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Mirror's choices, over the vision table that actually ships. */
class ErisedSelectorTest {

    private static final Path VISIONS = Path.of("src/main/resources/data/wizards_and_beasts/erised_visions");
    private static final Path LANG = Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");
    private static final Map<Identifier, ErisedVision> TABLE = new LinkedHashMap<>();

    @BeforeAll
    static void loadShippedTable() throws IOException {
        try (Stream<Path> files = Files.list(VISIONS)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                ErisedVision vision = ErisedVision.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))).getOrThrow();
                TABLE.put(Identifier.fromNamespaceAndPath("wizards_and_beasts", name), vision);
            }
        }
    }

    // ── the shipped table ────────────────────────────────────────────────

    @Test
    void everyVisionParsesAndHasItsLine() throws IOException {
        assertTrue(TABLE.size() >= 20, "table shrank to " + TABLE.size());
        String lang = Files.readString(LANG, StandardCharsets.UTF_8);
        for (ErisedVision vision : TABLE.values()) {
            assertTrue(lang.contains("\"" + vision.text() + "\""), "no lang line for " + vision.text());
        }
    }

    /**
     * Every heritage (through its variants) has visions of its own -- texts no other heritage is shown -- so what
     * the Mirror shows a giant is not a wizard's vision with the noun changed.
     */
    @Test
    void eachHeritageHasVisionsNoOtherHeritageSees() {
        Map<Heritage, Set<String>> own = new HashMap<>();
        for (Heritage heritage : Heritage.values()) {
            Set<String> seen = new HashSet<>();
            List<HeritageVariant> variants = new ArrayList<>();
            variants.add(null);
            for (HeritageVariant v : HeritageVariant.values()) {
                if (v.getParentHeritage() == heritage) {
                    variants.add(v);
                }
            }
            for (HeritageVariant variant : variants) {
                Gazer g = gazer(heritage, variant, Set.of(), List.of(), 5, 5, null);
                TABLE.forEach((id, vision) -> {
                    if (vision.shownTo(g) && mentionsHeritage(vision)) {
                        seen.add(vision.text());
                    }
                });
            }
            assertFalse(seen.isEmpty(), heritage + " has no vision of its own");
            own.put(heritage, seen);
        }
        for (Heritage a : Heritage.values()) {
            for (Heritage b : Heritage.values()) {
                if (a != b) {
                    Set<String> shared = new HashSet<>(own.get(a));
                    shared.retainAll(own.get(b));
                    assertTrue(shared.isEmpty(), a + " and " + b + " share " + shared);
                }
            }
        }
    }

    @Test
    void conditionsSelectTheirVisions() {
        Gazer werewolf = gazer(Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD, Set.of(MagicalCondition.LYCANTHROPY),
                List.of(), 5, 5, null);
        assertTrue(eligible(werewolf).contains("lycanthropy_moon"));
        assertFalse(eligible(gazer(Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD, Set.of(), List.of(), 5, 5, null))
                .contains("lycanthropy_moon"));

        Gazer mourner = gazer(Heritage.WIZARDKIND, null, Set.of(), List.of("Pip"), 5, 5, null);
        assertTrue(eligible(mourner).contains("lost_companion"));
        ErisedVision lost = TABLE.get(Identifier.fromNamespaceAndPath("wizards_and_beasts", "lost_companion"));
        assertEquals(Component.translatable(lost.text(), "Pip"), ErisedService.text(lost, mourner));

        Gazer healer = gazer(Heritage.WIZARDKIND, null, Set.of(), List.of(), 5, 5, Profession.HEALER);
        assertTrue(eligible(healer).contains("healer_empty_ward"));
        assertFalse(eligible(healer).contains("not_yet_chosen"), "a healer was shown the undecided vision");
        assertTrue(eligible(gazer(Heritage.WIZARDKIND, null, Set.of(), List.of(), 5, 5, null)).contains("not_yet_chosen"));
    }

    @Test
    void goalConditionReadsTheOwlSheet() {
        Gazer outstanding = new Gazer(Heritage.WIZARDKIND, null, Set.of(), List.of(), 5, 5, null,
                career(OWLGrade.O));
        Gazer acceptable = new Gazer(Heritage.WIZARDKIND, null, Set.of(), List.of(), 5, 5, null, career(OWLGrade.A));
        assertTrue(eligible(outstanding).contains("charms_outstanding"));
        assertFalse(eligible(acceptable).contains("charms_outstanding"));
    }

    // ── choosing ─────────────────────────────────────────────────────────

    @Test
    void sameSeedSameVisionAndRecentOnesAreAvoided() {
        Gazer g = gazer(Heritage.GIANT, HeritageVariant.GIANT_FULL, Set.of(), List.of(), 5, 5, null);
        ErisedSelector.Choice first = ErisedSelector.pickVision(g, TABLE, List.of(), 42L);
        assertNotNull(first);
        assertEquals(first, ErisedSelector.pickVision(g, TABLE, List.of(), 42L));
        for (long seed = 0; seed < 50; seed++) {
            ErisedSelector.Choice next = ErisedSelector.pickVision(g, TABLE, List.of(first.id().toString()), seed);
            assertNotNull(next);
            assertFalse(next.id().equals(first.id()), "the last vision was shown again at seed " + seed);
        }
    }

    @Test
    void aLoneVisionRepeatsRatherThanShowingNothing() {
        Map<Identifier, ErisedVision> one = Map.of(Identifier.fromNamespaceAndPath("t", "only"),
                new ErisedVision("t.only", 1, List.of(), java.util.Optional.empty()));
        ErisedSelector.Choice c = ErisedSelector.pickVision(gazer(null, null, Set.of(), List.of(), 0, 0, null), one,
                List.of("t:only"), 3L);
        assertNotNull(c);
        assertNull(ErisedSelector.pickVision(gazer(null, null, Set.of(), List.of(), 0, 0, null), Map.of(), List.of(), 3L));
    }

    @Test
    void cluesAreNeverRepeatedAndDoNotDependOnInputOrder() {
        List<ClueCandidate> candidates = List.of(
                clue(ClueCandidate.Kind.SPELL, "lumos"), clue(ClueCandidate.Kind.FLOO, "Leaky Cauldron"),
                clue(ClueCandidate.Kind.PLACE, "wizards_and_beasts:gringotts"));
        List<ClueCandidate> reversed = new ArrayList<>(candidates);
        java.util.Collections.reverse(reversed);
        assertEquals(ErisedSelector.pickClue(candidates, ErisedHistory.EMPTY, 9L),
                ErisedSelector.pickClue(reversed, ErisedHistory.EMPTY, 9L));

        ErisedHistory history = ErisedHistory.EMPTY;
        Set<String> given = new HashSet<>();
        for (int day = 0; day < 3; day++) {
            ClueCandidate c = ErisedSelector.pickClue(candidates, history, day);
            assertNotNull(c);
            assertTrue(given.add(c.key()), "clue repeated: " + c.key());
            history = history.gazed(day, null, c.key());
        }
        assertNull(ErisedSelector.pickClue(candidates, history, 7L), "a fourth clue out of three");
    }

    // ── the day's rules and the record ───────────────────────────────────

    @Test
    void threeGazesADayTheFirstFreeTheRestPull() {
        assertTrue(ErisedSelector.mayGiveClue(0));
        assertFalse(ErisedSelector.mayGiveClue(1));
        assertFalse(ErisedSelector.pullAfter(0).any());
        assertEquals(new ErisedSelector.Pull(30, 0), ErisedSelector.pullAfter(1));
        assertEquals(new ErisedSelector.Pull(60, 1), ErisedSelector.pullAfter(2));
        assertFalse(ErisedSelector.onlyReflection(2));
        assertTrue(ErisedSelector.onlyReflection(3));
    }

    @Test
    void historyCountsTodayKeepsLimitsAndRoundTrips() {
        ErisedHistory h = ErisedHistory.EMPTY.gazed(10, "a", "spell:lumos").gazed(10, "b", null);
        assertEquals(2, h.gazesOn(10));
        assertEquals(0, h.gazesOn(11), "a new day did not start fresh");
        assertEquals(1, h.gazed(11, "c", null).gazesOn(11));
        assertTrue(h.gaveClue("spell:lumos"));

        ErisedHistory many = ErisedHistory.EMPTY;
        for (int i = 0; i < ErisedHistory.MAX_CLUES + 10; i++) {
            many = many.gazed(i, "v" + i, "spell:s" + i);
        }
        assertEquals(ErisedHistory.MAX_RECENT, many.recent().size());
        assertEquals(ErisedHistory.MAX_CLUES, many.clues().size());
        assertEquals("v" + (ErisedHistory.MAX_CLUES + 9), many.recent().get(ErisedHistory.MAX_RECENT - 1));

        var json = ErisedHistory.CODEC.encodeStart(JsonOps.INSTANCE, h).getOrThrow();
        assertEquals(h, ErisedHistory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static boolean mentionsHeritage(ErisedVision vision) {
        return vision.when().stream().anyMatch(c -> c instanceof VisionCondition.HeritageIs);
    }

    private static Set<String> eligible(Gazer g) {
        Set<String> out = new HashSet<>();
        TABLE.forEach((id, vision) -> {
            if (vision.shownTo(g)) {
                out.add(id.getPath());
            }
        });
        return out;
    }

    private static ClueCandidate clue(ClueCandidate.Kind kind, String target) {
        return new ClueCandidate(kind, target, Component.literal(target));
    }

    private static Gazer gazer(Heritage heritage, HeritageVariant variant, Set<MagicalCondition> conditions,
                               List<String> lost, int spells, int creatures, Profession profession) {
        return new Gazer(heritage, variant, conditions, lost, spells, creatures, profession, career(OWLGrade.T));
    }

    private static CareerContext career(OWLGrade charms) {
        return new CareerContext() {
            @Override public OWLGrade grade(OWLSubject subject) { return subject == OWLSubject.CHARMS ? charms : OWLGrade.T; }
            @Override public int pages(DiscoveryTier minTier, int minRating) { return 0; }
            @Override public int researchCompleted() { return 0; }
            @Override public int activityTotal(ActivityFilter filter, boolean distinct) { return 0; }
            @Override public int contractsCompleted() { return 0; }
        };
    }
}
