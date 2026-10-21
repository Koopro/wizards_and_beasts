package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.education.data.ExamSitting;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLGradeRules;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Qualification;
import at.koopro.wizardsandbeasts.owl.career.CareerWork;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sitting: what it sets, when it may open, and how a practical or a written section moves a grade. */
class ExamRulesTest {

    private static ExamSitting sitting(Qualification level, List<OWLSubject> papers, Set<OWLSubject> done,
                                       List<ExamSitting.PaperQuestion> questions, List<Integer> answers) {
        return new ExamSitting(level, 100L, 100L + ExamRules.SITTING_TICKS, papers, done, questions, answers);
    }

    private static ExamSitting.PaperQuestion history(int answer) {
        return new ExamSitting.PaperQuestion(OWLSubject.HISTORY_OF_MAGIC, "q", "", List.of("a", "b", "c"), answer);
    }

    @Test
    void aSittingLiftsOneStepAndNeverFromTroll() {
        assertEquals(OWLGrade.T, ExamRules.lift(OWLGrade.T), "an examiner passed work that was never done");
        assertEquals(OWLGrade.P, ExamRules.lift(OWLGrade.D));
        assertEquals(OWLGrade.A, ExamRules.lift(OWLGrade.P));
        assertEquals(OWLGrade.E, ExamRules.lift(OWLGrade.A));
        assertEquals(OWLGrade.O, ExamRules.lift(OWLGrade.E));
        assertEquals(OWLGrade.O, ExamRules.lift(OWLGrade.O));
    }

    @Test
    void aWrittenSectionPassesAtHalfRightAndOnlyIfItAskedSomething() {
        assertFalse(ExamRules.writtenPassed(0, 0), "an empty section passed");
        assertFalse(ExamRules.writtenPassed(0, 2));
        assertTrue(ExamRules.writtenPassed(1, 2));
        assertTrue(ExamRules.writtenPassed(1, 1));
    }

    @Test
    void marksAreCourseworkLiftedWherePracticalsWereDone() {
        List<OWLSubject> papers = List.of(OWLSubject.CHARMS, OWLSubject.POTIONS, OWLSubject.HISTORY_OF_MAGIC,
                OWLSubject.HERBOLOGY);
        ExamSitting s = sitting(Qualification.OWL, papers, EnumSet.of(OWLSubject.CHARMS, OWLSubject.HERBOLOGY),
                List.of(history(1), history(2)), List.of(1, 0));
        Set<OWLSubject> lifted = ExamRules.lifted(s);
        assertEquals(EnumSet.of(OWLSubject.CHARMS, OWLSubject.HERBOLOGY, OWLSubject.HISTORY_OF_MAGIC), lifted,
                "one of two right passes History; untouched Potions stays");

        Map<OWLSubject, OWLGrade> coursework = new EnumMap<>(OWLSubject.class);
        coursework.put(OWLSubject.CHARMS, OWLGrade.A);
        coursework.put(OWLSubject.POTIONS, OWLGrade.E);
        coursework.put(OWLSubject.HISTORY_OF_MAGIC, OWLGrade.P);
        // Herbology has no coursework at all: Troll, and the practical cannot raise it.
        Map<OWLSubject, OWLGrade> marks = ExamRules.mark(coursework, papers, lifted);
        assertEquals(OWLGrade.E, marks.get(OWLSubject.CHARMS));
        assertEquals(OWLGrade.E, marks.get(OWLSubject.POTIONS));
        assertEquals(OWLGrade.A, marks.get(OWLSubject.HISTORY_OF_MAGIC));
        assertEquals(OWLGrade.T, marks.get(OWLSubject.HERBOLOGY));
        assertFalse(marks.containsKey(OWLSubject.ASTRONOMY), "a paper not sat was marked");
    }

    @Test
    void aWrittenPaperNotHandedInRaisesNothing() {
        ExamSitting s = sitting(Qualification.OWL, List.of(OWLSubject.HISTORY_OF_MAGIC), Set.of(),
                List.of(history(0)), List.of());
        assertTrue(ExamRules.lifted(s).isEmpty());
    }

    @Test
    void practicalsCloseWithTheClockAndAreCountedOnce() {
        ExamSitting s = sitting(Qualification.OWL, List.of(OWLSubject.CHARMS, OWLSubject.HISTORY_OF_MAGIC), Set.of(),
                List.of(), List.of());
        assertTrue(ExamRules.practicalOpen(s, OWLSubject.CHARMS, 150L));
        assertFalse(ExamRules.practicalOpen(s, OWLSubject.CHARMS, s.deadlineTick()), "a practical after the bell");
        assertFalse(ExamRules.practicalOpen(s, OWLSubject.POTIONS, 150L), "a paper not sat took a practical");
        assertFalse(ExamRules.practicalOpen(s, OWLSubject.HISTORY_OF_MAGIC, 150L), "a written paper took a practical");
        assertFalse(ExamRules.practicalOpen(s.withPractical(OWLSubject.CHARMS), OWLSubject.CHARMS, 150L),
                "a practical was credited twice");
    }

    @Test
    void everyPracticalHasASeamInTheWorld() {
        for (OWLSubject subject : OWLSubject.values()) {
            if (OWLGradeRules.ladder(subject) == null) continue;
            ExamRules.Practical practical = ExamRules.practical(subject);
            boolean viaWork = false;
            for (CareerWork.Kind kind : CareerWork.Kind.values()) {
                if (ExamRules.subjectFor(kind) == subject) viaWork = true;
            }
            boolean otherSeam = practical == ExamRules.Practical.WRITTEN
                    || practical == ExamRules.Practical.ANIMAGUS_FORM; // AnimagusTransformService
            assertTrue(viaWork || otherSeam, subject + " has a practical nothing can credit");
        }
    }

    @Test
    void anOwlSetsEveryExaminedPaperAndANewtOnlyTheSeatsEarned() {
        PlayerOWLData fresh = PlayerOWLData.DEFAULT;
        List<OWLSubject> owl = ExamRules.papers(Qualification.OWL, fresh);
        for (OWLSubject subject : OWLSubject.values()) {
            assertEquals(subject.examined(), owl.contains(subject), subject.name());
        }
        assertTrue(ExamRules.papers(Qualification.NEWT, fresh).isEmpty());

        Map<OWLSubject, OWLGrade> grades = new EnumMap<>(OWLSubject.class);
        grades.put(OWLSubject.POTIONS, OWLGrade.E);
        grades.put(OWLSubject.CHARMS, OWLGrade.A);
        grades.put(OWLSubject.HISTORY_OF_MAGIC, OWLGrade.O); // no N.E.W.T. in History
        PlayerOWLData sat = new PlayerOWLData(grades, true, 1, 1000L);
        assertEquals(List.of(OWLSubject.POTIONS), ExamRules.papers(Qualification.NEWT, sat));
        assertTrue(ExamRules.newtSeat(OWLSubject.POTIONS, grades));
        assertFalse(ExamRules.newtSeat(OWLSubject.CHARMS, grades), "an Acceptable earned a seat");
    }

    @Test
    void sittingsWaitAWeekPerLevelAndANewtNeedsASeat() {
        long week = ExamRules.RESIT_INTERVAL_TICKS;
        assertTrue(ExamRules.maySit(Qualification.OWL, PlayerOWLData.DEFAULT, 0L));
        assertFalse(ExamRules.maySit(Qualification.NEWT, PlayerOWLData.DEFAULT, 0L), "a N.E.W.T. before any O.W.L.");

        PlayerOWLData sat = PlayerOWLData.DEFAULT.afterSitting(Qualification.OWL, Map.of(OWLSubject.POTIONS, OWLGrade.O), 1000L);
        assertFalse(ExamRules.maySit(Qualification.OWL, sat, 1000L + week - 1));
        assertTrue(ExamRules.maySit(Qualification.OWL, sat, 1000L + week));
        assertTrue(ExamRules.maySit(Qualification.NEWT, sat, 1001L), "the N.E.W.T. waits on its own clock");

        PlayerOWLData newt = sat.afterSitting(Qualification.NEWT, Map.of(OWLSubject.POTIONS, OWLGrade.A), 2000L);
        assertEquals(1, newt.newtSittings());
        assertEquals(1, newt.sittings(), "a N.E.W.T. counted as an O.W.L. sitting");
        assertFalse(ExamRules.maySit(Qualification.NEWT, newt, 2000L + week - 1));
        assertTrue(ExamRules.maySit(Qualification.NEWT, newt, 2000L + week));
        assertNotNull(newt.newtGrades().get(OWLSubject.POTIONS));
    }
}
