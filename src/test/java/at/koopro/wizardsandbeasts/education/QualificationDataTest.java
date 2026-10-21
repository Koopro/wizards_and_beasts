package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.education.data.PlayerEducationData;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLGradeRules;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.ProfessionEligibility.Requirement;
import at.koopro.wizardsandbeasts.owl.Qualification;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped qualification gates and lessons, joined against the data they name: a gate on a brew or node that does
 * not exist, or a lesson for a subject nothing examines, would parse and silently do nothing.
 */
class QualificationDataTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang",
            "en_us.json");

    private static final Set<String> brews = new HashSet<>();
    private static final Set<String> nodes = new HashSet<>();
    private static final Set<String> capstones = new HashSet<>();
    private static List<QualificationGates.Gate> gates;

    @BeforeAll
    static void read() throws IOException {
        try (Stream<Path> files = Files.list(DATA.resolve("brewing_recipes"))) {
            for (Path f : files.toList()) {
                brews.add(json(f).get("outputBrewId").getAsString());
            }
        }
        try (Stream<Path> files = Files.walk(DATA.resolve("skill_nodes"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                nodes.add(json(f).get("id").getAsString());
            }
        }
        try (Stream<Path> files = Files.list(DATA.resolve("vocations"))) {
            for (Path f : files.toList()) {
                JsonObject v = json(f);
                if (v.has("capstoneNodeId")) {
                    String id = v.get("capstoneNodeId").getAsString();
                    capstones.add(id.substring(id.indexOf(':') + 1));
                }
            }
        }
        List<QualificationGates.Gate> all = new java.util.ArrayList<>();
        try (Stream<Path> files = Files.list(DATA.resolve(QualificationGates.DIRECTORY))) {
            for (Path f : files.toList()) {
                all.addAll(QualificationGates.GateFile.CODEC.parse(JsonOps.INSTANCE, json(f)).getOrThrow().gates());
            }
        }
        gates = all;
    }

    private static JsonObject json(Path f) throws IOException {
        return JsonParser.parseString(Files.readString(f)).getAsJsonObject();
    }

    @Test
    void everyGateStandsInFrontOfSomethingRealAndAsksForAPaperThatIsSat() {
        assertTrue(!gates.isEmpty(), "no gate loaded");
        for (QualificationGates.Gate gate : gates) {
            switch (gate.kind()) {
                case BREW -> assertTrue(brews.contains(gate.id()), "gate on unknown brew " + gate.id());
                case SKILL_NODE -> assertTrue(nodes.contains(gate.id()), "gate on unknown node " + gate.id());
            }
            for (Requirement r : gate.requires()) {
                assertTrue(OWLGradeRules.ladder(r.level(), r.subject()) != null,
                        gate.id() + " asks for " + r.level() + " " + r.subject() + ", which is never sat");
            }
        }
    }

    @Test
    void gatesTouchOnlyTheAdvancedEndNeverEverydayPlay() {
        for (String everyday : List.of("wizards_and_beasts:wiggenweld_potion", "wizards_and_beasts:pepperup_potion",
                "wizards_and_beasts:skele_gro", "wizards_and_beasts:mandrake_restoration_draught")) {
            assertTrue(gates.stream().noneMatch(g -> g.id().equals(everyday)), everyday + " is gated");
        }
        for (QualificationGates.Gate gate : gates) {
            if (gate.kind() == QualificationGates.Kind.SKILL_NODE) {
                assertTrue(capstones.contains(gate.id()), gate.id() + " is gated but is not a build's capstone");
            }
        }
    }

    @Test
    void anUnmetGateNamesTheFirstMissingPaper() {
        List<Requirement> requires = List.of(new Requirement(OWLSubject.POTIONS, OWLGrade.E),
                new Requirement(OWLSubject.POTIONS, OWLGrade.A, Qualification.NEWT));
        assertEquals(requires.get(0), QualificationGates.firstUnmet(requires, Map.of(), Map.of()));
        assertEquals(requires.get(1), QualificationGates.firstUnmet(requires, Map.of(OWLSubject.POTIONS, OWLGrade.O), Map.of()));
        assertNull(QualificationGates.firstUnmet(requires, Map.of(OWLSubject.POTIONS, OWLGrade.O),
                Map.of(OWLSubject.POTIONS, OWLGrade.A)));
    }

    @Test
    void lessonsAreOneADayAndRunOutAtAcceptable() {
        PlayerEducationData data = PlayerEducationData.EMPTY;
        assertNull(LessonServices.refusal(data, OWLSubject.POTIONS, 4L));
        data = data.withLesson(OWLSubject.POTIONS, 4L);
        assertEquals("one_a_day", LessonServices.refusal(data, OWLSubject.HERBOLOGY, 4L), "two lessons in one day");
        assertNull(LessonServices.refusal(data, OWLSubject.HERBOLOGY, 5L));
        data = data.withLesson(OWLSubject.POTIONS, 5L).withLesson(OWLSubject.POTIONS, 6L);
        assertEquals("taught_out", LessonServices.refusal(data, OWLSubject.POTIONS, 9L));
    }

    @Test
    void everyLessonALocationOffersIsARealServiceWithAName() throws IOException {
        JsonObject lang = json(LANG);
        Set<String> offered = new HashSet<>();
        try (Stream<Path> files = Files.list(DATA.resolve("locations"))) {
            for (Path f : files.toList()) {
                JsonObject location = json(f);
                if (!location.has("services")) continue;
                for (JsonElement s : location.getAsJsonArray("services")) {
                    String id = s.getAsJsonObject().get("service").getAsString();
                    if (id.contains(":lesson_")) offered.add(id.substring(id.indexOf(':') + 1));
                }
            }
        }
        Set<String> registered = new HashSet<>();
        for (OWLSubject subject : OWLSubject.values()) {
            if (OWLGradeRules.teachable(subject)) registered.add(LessonServices.serviceName(subject));
        }
        assertTrue(registered.containsAll(offered), "a location offers a lesson that is not registered: " + offered);
        assertTrue(offered.size() >= 7, "most examined subjects should have a teacher somewhere: " + offered);
        for (String service : registered) {
            assertTrue(lang.has("location.wizards_and_beasts.service." + service), "unnamed service " + service);
        }
    }

    @Test
    void theShippedTrackAsksForANewtOnlyAtItsTop() throws IOException {
        JsonArray ranks = json(DATA.resolve("career_tracks/magizoologist.json")).getAsJsonArray("ranks");
        for (int i = 0; i < ranks.size(); i++) {
            JsonObject rank = ranks.get(i).getAsJsonObject();
            boolean newt = rank.has("requires") && rank.getAsJsonArray("requires").toString().contains("newt_grade");
            assertEquals(i == ranks.size() - 1, newt, rank.get("id").getAsString());
        }
    }
}
