package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.education.data.ExamSitting.PaperQuestion;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sky a telescope charts and the written paper drawn from what was read and charted — joined against the
 * shipped tomes and lang file, so a question can never point at text nobody can see.
 */
class AstronomyAndPaperTest {

    private static final Path LANG = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang",
            "en_us.json");
    private static final Path LORE_REGISTRY = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts",
            "registry", "LoreItemRegistry.java");

    private static JsonObject lang;
    private static final List<String> tomesShipped = new ArrayList<>();

    @BeforeAll
    static void read() throws IOException {
        lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        Matcher m = Pattern.compile("registerItem\\(\"([a-z_]+)\",\\s*props -> new LoreTomeItem")
                .matcher(Files.readString(LORE_REGISTRY));
        while (m.find()) tomesShipped.add(m.group(1));
    }

    @Test
    void twelveNightsShowEveryConstellationAndThePhaseNeverLocksStep() {
        Set<Astronomy.Constellation> seen = EnumSet.noneOf(Astronomy.Constellation.class);
        Set<Integer> phases = new HashSet<>();
        for (long night = 40; night < 52; night++) {
            seen.add(Astronomy.constellationOn(night));
            phases.add((int) Math.floorMod(night, 8L));
        }
        assertEquals(EnumSet.allOf(Astronomy.Constellation.class), seen);
        assertEquals(8, phases.size());
        assertEquals(20, Astronomy.maxPoints());
        assertEquals(20, Astronomy.points(0xFF, 0xFFF));
        assertEquals(2, Astronomy.points(Astronomy.phaseBit(3), Astronomy.Constellation.LEO.bit()));
    }

    @Test
    void anEveningAndTheSmallHoursAfterItAreOneNight() {
        long day = 5L * 24000L;
        assertEquals(Astronomy.night(day + 14000L), Astronomy.night(day + 24000L + 2000L));
        assertTrue(Astronomy.night(day + 14000L) != Astronomy.night(day + 24000L + 14000L));
    }

    @Test
    void everyTomeShippedHasPassagesAndQuestionsAndEveryKeyIsWorded() {
        assertTrue(!tomesShipped.isEmpty(), "no tome was read from the registry");
        assertEquals(new HashSet<>(tomesShipped), new HashSet<>(ExamQuestions.tomes()),
                "a tome without questions, or questions for a tome that does not exist");
        for (String tome : tomesShipped) {
            for (int n = 0; n < ExamQuestions.PASSAGES; n++) {
                assertWorded(ExamQuestions.passageKey(tome, n));
                PaperQuestion q = ExamQuestions.tomeQuestion(tome, n);
                assertWorded(q.question());
                q.options().forEach(AstronomyAndPaperTest::assertWorded);
                assertTrue(q.answer() >= 0 && q.answer() < q.options().size(), tome + " q" + n + " answer out of range");
            }
        }
        for (Astronomy.Constellation c : Astronomy.Constellation.values()) {
            assertWorded(c.nameKey());
            assertWorded(c.factKey());
            String fact = lang.get(c.factKey()).getAsString().toLowerCase();
            String name = lang.get(c.nameKey()).getAsString().toLowerCase();
            assertTrue(!fact.contains(name), c + "'s fact gives its own name away");
        }
        for (int p = 0; p < Astronomy.PHASES; p++) assertWorded(Astronomy.phaseKey(p));
        assertWorded("education.paper.astronomy.which");
    }

    @Test
    void theAnswersAreNotAlwaysInTheSamePlace() {
        Set<Integer> positions = new HashSet<>();
        for (String tome : ExamQuestions.tomes()) {
            for (int n = 0; n < ExamQuestions.PASSAGES; n++) positions.add(ExamQuestions.tomeQuestion(tome, n).answer());
        }
        assertEquals(3, positions.size());
    }

    @Test
    void aPaperAsksOnlyAboutWhatWasReadAndCharted() {
        List<OWLSubject> papers = List.of(OWLSubject.HISTORY_OF_MAGIC, OWLSubject.ASTRONOMY);
        assertTrue(ExamQuestions.draw(papers, List.of(), 0, new Random(1)).isEmpty(), "questions on nothing read");

        List<PaperQuestion> drawn = ExamQuestions.draw(papers, List.of("hogwarts_a_history", "not_a_tome"),
                Astronomy.Constellation.DRACO.bit(), new Random(7));
        long history = drawn.stream().filter(q -> q.subject() == OWLSubject.HISTORY_OF_MAGIC).count();
        long astronomy = drawn.stream().filter(q -> q.subject() == OWLSubject.ASTRONOMY).count();
        assertEquals(ExamRules.QUESTIONS_PER_SECTION, history);
        assertEquals(1, astronomy, "one constellation charted, one question");
        for (PaperQuestion q : drawn) {
            if (q.subject() == OWLSubject.HISTORY_OF_MAGIC) {
                assertTrue(q.question().startsWith("education.tome.hogwarts_a_history."), q.question());
            } else {
                assertEquals(Astronomy.Constellation.DRACO.factKey(), q.arg());
                assertEquals(Astronomy.Constellation.DRACO.nameKey(), q.options().get(q.answer()));
                assertEquals(3, new HashSet<>(q.options()).size(), "duplicate choices");
            }
        }

        // A N.E.W.T. sitting has no History paper: no History questions however much was read.
        List<PaperQuestion> newt = ExamQuestions.draw(List.of(OWLSubject.POTIONS), List.of("hogwarts_a_history"),
                0xFFF, new Random(3));
        assertTrue(newt.isEmpty());
    }

    private static void assertWorded(String key) {
        assertTrue(lang.has(key), "missing lang key " + key);
    }
}
