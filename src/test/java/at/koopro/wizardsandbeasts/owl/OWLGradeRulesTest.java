package at.koopro.wizardsandbeasts.owl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The O.W.L. marking scheme against the content the game actually ships.
 *
 * <p>The scheme this replaced was internally consistent and unreachable: Herbology asked for 15 nodes
 * of an 8-node tree, Ancient Runes for 12 of an 11-node tree, History of Magic for 8 of 3 tomes, and
 * Defence read a cast counter nothing wrote. Each piece looked fine alone. So the reachability checks
 * here join the ladders with the shipped spell, node and tome data — the composed inequality, not a
 * per-ladder ceiling (tasks/lessons.md, "Fixing one term of a formula is not fixing the formula").
 */
class OWLGradeRulesTest {

    private static final Path SPELLS = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spells");
    private static final Path NODES = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "skill_nodes");
    private static final Path LORE_REGISTRY = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts",
            "registry", "LoreItemRegistry.java");

    private static int utilitySpells;
    private static int duellingSpells;
    private static int herbologyNodes;
    private static int wandloreNodes;
    private static int loreTomes;

    @BeforeAll
    static void readShippedContent() throws IOException {
        try (Stream<Path> files = Files.walk(SPELLS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject spell = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String state = spell.has("implementationState")
                        ? spell.get("implementationState").getAsString() : "implemented";
                if ("coming_soon".equals(state)) continue;
                String category = spell.get("category").getAsString();
                if ("utility".equals(category)) utilitySpells++;
                if ("combat".equals(category) || "defense".equals(category)) duellingSpells++;
            }
        }
        herbologyNodes = countNodes("herbology");
        wandloreNodes = countNodes("wandlore");
        Matcher tomes = Pattern.compile("new LoreTomeItem\\(").matcher(Files.readString(LORE_REGISTRY));
        while (tomes.find()) loreTomes++;
    }

    private static int countNodes(String tree) throws IOException {
        try (Stream<Path> files = Files.walk(NODES.resolve(tree))) {
            return (int) files.filter(p -> p.toString().endsWith(".json")).count();
        }
    }

    /** The strongest candidate the shipped content allows, short of unbounded grinding. */
    private static OWLGradeRules.Evidence bestCandidate() {
        return new OWLGradeRules.Evidence(
                utilitySpells * OWLGradeRules.MASTERED_POINTS,
                duellingSpells * OWLGradeRules.MASTERED_POINTS,
                true, true,
                OWLGradeRules.NEWT_POTIONS.o(),
                OWLGradeRules.NEWT_HERBOLOGY.o(),
                herbologyNodes,
                OWLGradeRules.NEWT_CREATURES.o(),
                OWLGradeRules.NEWT_CREATURES.o(),
                wandloreNodes,
                loreTomes,
                0, // completed research is a bonus on top; every subject must reach O without it
                at.koopro.wizardsandbeasts.education.Astronomy.maxPoints(),
                Map.of()); // lessons only ever reach A: every O must be earned without them
    }

    /** The best N.E.W.T. sheet: coursework, every paper lifted by its practical in the sitting. */
    private static Map<OWLSubject, OWLGrade> bestNewts() {
        Map<OWLSubject, OWLGrade> out = new EnumMap<>(OWLSubject.class);
        OWLGradeRules.gradeAll(Qualification.NEWT, bestCandidate())
                .forEach((s, g) -> out.put(s, at.koopro.wizardsandbeasts.education.ExamRules.lift(g)));
        return out;
    }

    @Test
    void contentWasRead() {
        assertTrue(utilitySpells > 0 && duellingSpells > 0 && herbologyNodes > 0 && wandloreNodes > 0 && loreTomes > 0,
                "a data join read nothing: " + utilitySpells + "/" + duellingSpells + "/" + herbologyNodes
                        + "/" + wandloreNodes + "/" + loreTomes);
    }

    @Test
    void everyExaminedSubjectCanReachOutstandingFromShippedContent() {
        Map<OWLSubject, OWLGrade> grades = OWLGradeRules.gradeAll(bestCandidate());
        for (OWLSubject subject : OWLSubject.values()) {
            if (!subject.examined()) continue;
            assertEquals(OWLGrade.O, grades.get(subject), subject + " cannot reach O with everything the game ships");
        }
    }

    @Test
    void theWebAloneDoesNotCertifyHerbologyOrRunesIsReachable() {
        // Nodes help, but Outstanding Herbology needs harvests too; Runes is the web and nothing else.
        assertTrue(herbologyNodes * OWLGradeRules.HERBOLOGY_NODE_POINTS >= OWLGradeRules.HERBOLOGY.a(),
                "the whole Herbology tree is not even an A");
        assertTrue(herbologyNodes * OWLGradeRules.HERBOLOGY_NODE_POINTS < OWLGradeRules.HERBOLOGY.o(),
                "the Herbology tree alone is Outstanding: harvesting stopped mattering");
        assertTrue(wandloreNodes >= OWLGradeRules.RUNES.o(), "Ancient Runes O needs more Wandlore nodes than exist");
        assertTrue(loreTomes >= OWLGradeRules.HISTORY.o(), "History of Magic O needs more tomes than exist");
    }

    @Test
    void subjectsWithNoContentAreNotSatAtAll() {
        Map<OWLSubject, OWLGrade> grades = OWLGradeRules.gradeAll(bestCandidate());
        for (OWLSubject subject : List.of(OWLSubject.ARITHMANCY, OWLSubject.DIVINATION,
                OWLSubject.MUGGLE_STUDIES)) {
            assertFalse(subject.examined());
            assertNull(OWLGradeRules.ladder(subject));
            assertFalse(grades.containsKey(subject), subject + " was graded");
        }
    }

    @Test
    void aBlankCandidateIsTrollEverywhereItSits() {
        Map<OWLSubject, OWLGrade> grades = OWLGradeRules.gradeAll(OWLGradeRules.Evidence.NONE);
        grades.forEach((subject, grade) -> assertEquals(OWLGrade.T, grade, subject.name()));
    }

    @Test
    void ladderEdges() {
        OWLGradeRules.Ladder ladder = new OWLGradeRules.Ladder(6, 15, 30);
        assertEquals(OWLGrade.T, OWLGradeRules.grade(0, ladder));
        assertEquals(OWLGrade.D, OWLGradeRules.grade(1, ladder));
        assertEquals(OWLGrade.P, OWLGradeRules.grade(2, ladder));
        assertEquals(OWLGrade.A, OWLGradeRules.grade(6, ladder));
        assertEquals(OWLGrade.E, OWLGradeRules.grade(15, ladder));
        assertEquals(OWLGrade.O, OWLGradeRules.grade(30, ladder));
    }

    @Test
    void tierPointsFollowProficiency() {
        assertEquals(0, OWLGradeRules.tierPoints(49, 50, 200));
        assertEquals(1, OWLGradeRules.tierPoints(50, 50, 200));
        assertEquals(2, OWLGradeRules.tierPoints(200, 50, 200));
    }

    @Test
    void aResitNeverCostsAGrade() {
        Map<OWLSubject, OWLGrade> first = new EnumMap<>(OWLSubject.class);
        first.put(OWLSubject.CHARMS, OWLGrade.O);
        first.put(OWLSubject.POTIONS, OWLGrade.P);
        first.put(OWLSubject.ARITHMANCY, OWLGrade.T); // an old save's grade in a subject no longer sat
        Map<OWLSubject, OWLGrade> second = new EnumMap<>(OWLSubject.class);
        second.put(OWLSubject.CHARMS, OWLGrade.A);
        second.put(OWLSubject.POTIONS, OWLGrade.E);
        Map<OWLSubject, OWLGrade> kept = OWLGradeRules.best(first, second);
        assertEquals(OWLGrade.O, kept.get(OWLSubject.CHARMS));
        assertEquals(OWLGrade.E, kept.get(OWLSubject.POTIONS));
        assertFalse(kept.containsKey(OWLSubject.ARITHMANCY));
    }

    @Test
    void everyOfferedCareerIsReachableAndAsksOnlyForExaminedSubjects() {
        Map<OWLSubject, OWLGrade> grades = OWLGradeRules.gradeAll(bestCandidate());
        Map<OWLSubject, OWLGrade> newts = bestNewts();
        List<String> problems = new ArrayList<>();
        for (Profession profession : Profession.values()) {
            if (!profession.offered()) {
                if (ProfessionEligibility.isEligible(profession, grades, newts, false)) {
                    problems.add(profession + " is not offered but a candidate qualified");
                }
                continue;
            }
            for (ProfessionEligibility.Requirement r : ProfessionEligibility.requirements(profession, false)) {
                if (!r.subject().examined()) problems.add(profession + " asks for unexamined " + r.subject());
            }
            if (!ProfessionEligibility.isEligible(profession, grades, newts, false)) {
                problems.add(profession + " cannot be reached by the best candidate");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void aNewWizardQualifiesForTheOpenCareersOnly() {
        Map<OWLSubject, OWLGrade> grades = OWLGradeRules.gradeAll(OWLGradeRules.Evidence.NONE);
        Map<OWLSubject, OWLGrade> newts = Map.of();
        for (Profession profession : Profession.values()) {
            boolean open = ProfessionEligibility.requirements(profession, false).isEmpty()
                    && profession != Profession.PROFESSOR;
            assertEquals(open, ProfessionEligibility.isEligible(profession, grades, newts, false), profession.name());
        }
    }

    @Test
    void astronomyIsExaminedAndTheSkyCanCertifyIt() {
        assertTrue(OWLSubject.ASTRONOMY.examined());
        assertEquals(OWLGrade.O, OWLGradeRules.grade(OWLSubject.ASTRONOMY, bestCandidate()));
        assertTrue(at.koopro.wizardsandbeasts.education.Astronomy.maxPoints() >= OWLGradeRules.NEWT_ASTRONOMY.o(),
                "the N.E.W.T. asks for more of the sky than there is");
    }

    @Test
    void everyNewtSubjectCanReachOutstandingAndOnlyTransfigurationNeedsThePractical() {
        Map<OWLSubject, OWLGrade> coursework = OWLGradeRules.gradeAll(Qualification.NEWT, bestCandidate());
        Map<OWLSubject, OWLGrade> best = bestNewts();
        for (OWLSubject subject : OWLSubject.values()) {
            if (OWLGradeRules.newtLadder(subject) == null) {
                assertFalse(coursework.containsKey(subject), subject + " has a N.E.W.T. grade but no ladder");
                continue;
            }
            assertTrue(subject.examined(), subject + " has a N.E.W.T. but no O.W.L.");
            OWLGrade expected = subject == OWLSubject.TRANSFIGURATION ? OWLGrade.E : OWLGrade.O;
            assertEquals(expected, coursework.get(subject), subject + " coursework at N.E.W.T.");
            assertEquals(OWLGrade.O, best.get(subject), subject + " cannot reach N.E.W.T. O even with the practical");
        }
        assertNull(OWLGradeRules.newtLadder(OWLSubject.HISTORY_OF_MAGIC), "History of Magic has no N.E.W.T.");
    }

    @Test
    void everyNewtLadderIsHarderThanItsOwl() {
        for (OWLSubject subject : OWLSubject.values()) {
            OWLGradeRules.Ladder newt = OWLGradeRules.newtLadder(subject);
            OWLGradeRules.Ladder owl = OWLGradeRules.ladder(subject);
            if (newt == null) continue;
            assertTrue(newt.a() >= owl.a() && newt.o() > owl.o(), subject + " N.E.W.T. is not above the O.W.L.");
        }
    }

    @Test
    void lessonsReachAcceptableAndNoFurtherAndNeverCountAtNewt() {
        for (OWLSubject subject : OWLSubject.values()) {
            OWLGradeRules.Ladder ladder = OWLGradeRules.ladder(subject);
            if (ladder == null) continue;
            OWLGradeRules.Evidence onlyLessons = new OWLGradeRules.Evidence(0, 0, false, false, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, Map.of(subject, 99));
            assertEquals(OWLGradeRules.teachable(subject) ? OWLGrade.A : OWLGrade.T,
                    OWLGradeRules.grade(Qualification.OWL, subject, onlyLessons),
                    subject + ": lessons alone must earn exactly A, or nothing where A is already E");
            if (OWLGradeRules.newtLadder(subject) != null) {
                assertEquals(OWLGrade.T, OWLGradeRules.grade(Qualification.NEWT, subject, onlyLessons),
                        subject + ": a N.E.W.T. counted lessons");
            }
        }
        assertFalse(OWLGradeRules.teachable(OWLSubject.TRANSFIGURATION), "an Animagus cannot be taught in a lesson");
        assertEquals(0, OWLGradeRules.lessonCredit(OWLGradeRules.POTIONS, 0));
        assertEquals(OWLGradeRules.POTIONS.a(),
                OWLGradeRules.lessonCredit(OWLGradeRules.POTIONS, OWLGradeRules.LESSONS_TO_A));
    }

    @Test
    void eliteCareersAskForNewtsAndTheRestDoNot() {
        java.util.Set<Profession> elite = java.util.EnumSet.of(Profession.AUROR, Profession.HEALER,
                Profession.UNSPEAKABLE, Profession.HIT_WIZARD, Profession.CURSE_BREAKER);
        for (Profession profession : Profession.values()) {
            assertEquals(elite.contains(profession), ProfessionEligibility.asksForNewts(profession, false),
                    profession.name());
            for (ProfessionEligibility.Requirement r : ProfessionEligibility.requirements(profession, false)) {
                if (r.level() == Qualification.NEWT) {
                    assertTrue(OWLGradeRules.newtLadder(r.subject()) != null, profession + " asks for a N.E.W.T. never sat");
                }
            }
        }
        assertFalse(ProfessionEligibility.asksForNewts(Profession.CURSE_BREAKER, true), "Gringotts trains its own");
    }

    @Test
    void anAurorWithOwlsAloneIsNotQualified() {
        Map<OWLSubject, OWLGrade> owls = OWLGradeRules.gradeAll(bestCandidate());
        assertFalse(ProfessionEligibility.isEligible(Profession.AUROR, owls, Map.of(), false));
        assertTrue(ProfessionEligibility.isEligible(Profession.MAGIZOOLOGIST, owls, Map.of(), false),
                "an ordinary career asked for a N.E.W.T.");
        List<ProfessionEligibility.Requirement> unmet =
                ProfessionEligibility.unmet(Profession.AUROR, owls, Map.of(), false);
        assertTrue(!unmet.isEmpty() && unmet.stream().allMatch(r -> r.level() == Qualification.NEWT),
                "only the N.E.W.T.s should be missing: " + unmet);
    }

    @Test
    void aNewtResitKeepsTheBestPaperAndDropsUnsatSubjects() {
        Map<OWLSubject, OWLGrade> first = Map.of(OWLSubject.POTIONS, OWLGrade.E, OWLSubject.HISTORY_OF_MAGIC, OWLGrade.O);
        Map<OWLSubject, OWLGrade> second = Map.of(OWLSubject.POTIONS, OWLGrade.A, OWLSubject.CHARMS, OWLGrade.A);
        Map<OWLSubject, OWLGrade> kept = OWLGradeRules.best(Qualification.NEWT, first, second);
        assertEquals(OWLGrade.E, kept.get(OWLSubject.POTIONS));
        assertEquals(OWLGrade.A, kept.get(OWLSubject.CHARMS));
        assertFalse(kept.containsKey(OWLSubject.HISTORY_OF_MAGIC), "a N.E.W.T. never sat was kept");
    }
}
