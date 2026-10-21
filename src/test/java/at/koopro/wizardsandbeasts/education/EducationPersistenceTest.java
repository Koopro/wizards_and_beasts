package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.education.data.ExamSitting;
import at.koopro.wizardsandbeasts.education.data.PlayerEducationData;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Qualification;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saves: everything education keeps round-trips, and every save from before it loads as an empty record. */
class EducationPersistenceTest {

    @Test
    void aSittingInProgressSurvivesASave() {
        ExamSitting sitting = new ExamSitting(Qualification.NEWT, 10L, 12_010L,
                List.of(OWLSubject.POTIONS, OWLSubject.ASTRONOMY), EnumSet.of(OWLSubject.POTIONS),
                List.of(new ExamSitting.PaperQuestion(OWLSubject.ASTRONOMY, "education.paper.astronomy.which",
                        "education.constellation.leo.fact", List.of("x", "y", "z"), 2)),
                List.of(2));
        PlayerEducationData data = PlayerEducationData.EMPTY
                .withObservation(Astronomy.phaseBit(4), Astronomy.Constellation.LYRA.bit(), 33L)
                .withLesson(OWLSubject.CHARMS, 7L)
                .withSitting(Optional.of(sitting));
        data = new PlayerEducationData(data.moonPhases(), data.constellations(), data.lastObservedNight(), data.lessons(),
                data.lastLessonDay(), data.sitting(), List.of(new PlayerEducationData.ResultLine(Qualification.OWL,
                OWLSubject.CHARMS, OWLGrade.A, OWLGrade.E, true)));

        JsonElement saved = PlayerEducationData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        PlayerEducationData loaded = PlayerEducationData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(data, loaded);
        assertEquals(1, loaded.lessonsIn(OWLSubject.CHARMS));
        assertEquals(7L, loaded.lastLessonDay());
        assertTrue(loaded.sitting().orElseThrow().practicals().contains(OWLSubject.POTIONS));
        assertTrue(loaded.sitting().orElseThrow().answered());
    }

    @Test
    void aSaveWithoutEducationLoadsEmpty() {
        PlayerEducationData loaded = PlayerEducationData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .getOrThrow();
        assertEquals(PlayerEducationData.EMPTY, loaded);
    }

    @Test
    void anOwlSaveFromBeforeNewtsLoadsWithNone() {
        PlayerOWLData owl = PlayerOWLData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"grades\":{\"POTIONS\":\"E\"},\"examTaken\":true,\"sittings\":2,\"lastSatTick\":500}")).getOrThrow();
        assertEquals(Map.of(), owl.newtGrades());
        assertEquals(0, owl.newtSittings());
        assertEquals(2, owl.sittings());
        assertTrue(ExamRules.maySit(Qualification.NEWT, owl, 0L), "an old Potions E should open a seat at once");
    }

    @Test
    void newtGradesRoundTrip() {
        PlayerOWLData owl = PlayerOWLData.DEFAULT
                .afterSitting(Qualification.OWL, Map.of(OWLSubject.CHARMS, OWLGrade.O), 100L)
                .afterSitting(Qualification.NEWT, Map.of(OWLSubject.CHARMS, OWLGrade.E), 200L);
        PlayerOWLData loaded = PlayerOWLData.CODEC.parse(JsonOps.INSTANCE,
                PlayerOWLData.CODEC.encodeStart(JsonOps.INSTANCE, owl).getOrThrow()).getOrThrow();
        assertEquals(owl, loaded);
        assertEquals(OWLGrade.E, loaded.gradesAt(Qualification.NEWT).get(OWLSubject.CHARMS));
        assertEquals(200L, loaded.lastSatAt(Qualification.NEWT));
    }
}
