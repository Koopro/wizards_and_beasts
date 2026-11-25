package at.koopro.wizardsandbeasts.education;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D2: the exam-week calendar. Notice first, then the window, every cycle. */
class ExamWeekTest {

    private static final ExamWeek.Rules FORTNIGHT = ExamWeek.Rules.DEFAULT;

    @Test
    void theWindowFollowsTheNotice() {
        assertEquals(14, FORTNIGHT.cycleDays());
        for (long day = 0; day < 3; day++) {
            assertFalse(ExamWeek.isOpen(FORTNIGHT, day), "open during the notice, day " + day);
        }
        for (long day = 3; day < 6; day++) {
            assertTrue(ExamWeek.isOpen(FORTNIGHT, day), "shut in the window, day " + day);
        }
        assertFalse(ExamWeek.isOpen(FORTNIGHT, 6));
        assertTrue(ExamWeek.isOpen(FORTNIGHT, 17), "the next fortnight's window");
    }

    @Test
    void theNextOpeningIsAlwaysAhead() {
        assertEquals(3, ExamWeek.nextOpening(FORTNIGHT, 0));
        assertEquals(17, ExamWeek.nextOpening(FORTNIGHT, 3), "on the first open day, the next is a cycle on");
        assertEquals(17, ExamWeek.nextOpening(FORTNIGHT, 10));
    }

    @Test
    void aPackThatAsksTooMuchIsTrimmed() {
        ExamWeek.Rules odd = new ExamWeek.Rules(5, 9, 9);
        assertEquals(5, odd.windowDays());
        assertEquals(0, odd.noticeDays());
        assertTrue(ExamWeek.isOpen(ExamWeek.Rules.ALWAYS, 12345));
    }
}
