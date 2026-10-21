package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.education.data.ExamSitting;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLGradeRules;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Qualification;
import at.koopro.wizardsandbeasts.owl.career.CareerWork;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a sitting at the examination desk runs and is marked. Pure.
 *
 * <p><b>Coursework sets the grade; the sitting can lift it one step.</b> Everything a candidate earned by playing
 * ({@link OWLGradeRules}) is the floor and is never at risk. Opening a sitting starts a ten-minute clock; inside it
 * each subject has one practical — the same act the subject's coursework already counts, done now, under the
 * examiner's eye (a charm cast, a brew handed in, a beast fed, the Animagus form taken) — or, for History of Magic
 * and Astronomy, a section of the written paper. A subject whose practical is done, or whose section is at least
 * half right, is raised one grade. A Troll is never raised: an examiner cannot pass work that was never done.
 *
 * <p>N.E.W.T. seats are per subject and need an O.W.L. of Exceeds Expectations or better in it (canon: Snape takes
 * only Outstanding, most masters take E). Resits wait {@link #RESIT_INTERVAL_TICKS} per level; the best paper per
 * subject is always kept.
 */
@NullMarked
public final class ExamRules {

    /** Ten minutes: long enough to cast, brew a short draught or walk to a pen, short enough to feel the clock. */
    public static final long SITTING_TICKS = 10L * 60L * 20L;
    /** Seven in-game days between sittings at the same level: long enough that a resit marks a week of real progress. */
    public static final long RESIT_INTERVAL_TICKS = 7L * 24000L;
    /** The O.W.L. grade a N.E.W.T. seat asks for. */
    public static final OWLGrade NEWT_SEAT = OWLGrade.E;
    /** Questions per written section at most: a check that the reading happened, not a quiz. */
    public static final int QUESTIONS_PER_SECTION = 2;

    /** What a subject's practical is. */
    public enum Practical {
        /** A Utility spell cast that counted as practice. */
        CHARM_CAST,
        /** A Combat or Defence spell cast that counted as practice. */
        DUEL_CAST,
        /** The Animagus form taken. */
        ANIMAGUS_FORM,
        /** A brew collected from a cauldron, or a finished brew handed in at the desk. */
        BREW,
        /** A mature crop harvested, or a specimen handed in at the desk. */
        HARVEST,
        /** A bonded creature fed or a bestiary page taken deeper. */
        CREATURE_CARE,
        /** A wand finished at the bench. */
        WANDCRAFT,
        /** The subject's section of the written paper. */
        WRITTEN;

        public String translationKey() {
            return "education.practical." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private ExamRules() {}

    public static Practical practical(OWLSubject subject) {
        return switch (subject) {
            case CHARMS -> Practical.CHARM_CAST;
            case DEFENCE_AGAINST_DARK_ARTS -> Practical.DUEL_CAST;
            case TRANSFIGURATION -> Practical.ANIMAGUS_FORM;
            case POTIONS -> Practical.BREW;
            case HERBOLOGY -> Practical.HARVEST;
            case CARE_OF_MAGICAL_CREATURES -> Practical.CREATURE_CARE;
            case ANCIENT_RUNES -> Practical.WANDCRAFT;
            case HISTORY_OF_MAGIC, ASTRONOMY, ARITHMANCY, DIVINATION, MUGGLE_STUDIES -> Practical.WRITTEN;
        };
    }

    /** The subject whose practical a piece of career work is, or {@code null} for work no paper examines. */
    public static @Nullable OWLSubject subjectFor(CareerWork.@Nullable Kind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case CHARMWORK -> OWLSubject.CHARMS;
            case DUELLING -> OWLSubject.DEFENCE_AGAINST_DARK_ARTS;
            case BREWING -> OWLSubject.POTIONS;
            case HARVEST -> OWLSubject.HERBOLOGY;
            case CREATURE_CARE -> OWLSubject.CARE_OF_MAGICAL_CREATURES;
            case WANDCRAFT -> OWLSubject.ANCIENT_RUNES;
            case TEACHING, FLYING, CORRESPONDENCE -> null;
        };
    }

    /** Whether an O.W.L. sheet earns a N.E.W.T. seat in {@code subject}. */
    public static boolean newtSeat(OWLSubject subject, Map<OWLSubject, OWLGrade> owlGrades) {
        OWLGrade owl = owlGrades.get(subject);
        return OWLGradeRules.newtLadder(subject) != null && owl != null && owl.value >= NEWT_SEAT.value;
    }

    /** The papers a sitting at {@code level} sets for a candidate with this record, in subject order. */
    public static List<OWLSubject> papers(Qualification level, PlayerOWLData record) {
        List<OWLSubject> out = new ArrayList<>();
        for (OWLSubject subject : OWLSubject.values()) {
            if (level == Qualification.OWL ? OWLGradeRules.ladder(subject) != null
                    : newtSeat(subject, record.grades())) {
                out.add(subject);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Game time from which {@code level} may be sat (again); 0 when it may be sat now. */
    public static long nextSittingTick(Qualification level, PlayerOWLData record) {
        long last = record.lastSatAt(level);
        if (record.sittingsAt(level) <= 0 || last <= 0) return 0L;
        return last + RESIT_INTERVAL_TICKS;
    }

    /** Whether a sitting at {@code level} may open now. A N.E.W.T. needs an O.W.L. sat and at least one seat. */
    public static boolean maySit(Qualification level, PlayerOWLData record, long now) {
        if (level == Qualification.NEWT && (!record.examTaken() || papers(level, record).isEmpty())) {
            return false;
        }
        return now >= nextSittingTick(level, record);
    }

    /** One grade up, never above O, and never from Troll. */
    public static OWLGrade lift(OWLGrade grade) {
        if (grade == OWLGrade.T || grade == OWLGrade.O) return grade;
        return OWLGrade.values()[grade.ordinal() - 1];
    }

    /** A written section passes at half right or better, and only if it set a question. */
    public static boolean writtenPassed(int correct, int asked) {
        return asked > 0 && correct * 2 >= asked;
    }

    /** Subjects the sitting raises: practicals done, written sections passed. */
    public static Set<OWLSubject> lifted(ExamSitting sitting) {
        EnumSet<OWLSubject> out = EnumSet.noneOf(OWLSubject.class);
        for (OWLSubject subject : sitting.papers()) {
            if (practical(subject) == Practical.WRITTEN) {
                int asked = 0;
                int correct = 0;
                for (int i = 0; i < sitting.questions().size(); i++) {
                    ExamSitting.PaperQuestion q = sitting.questions().get(i);
                    if (q.subject() != subject) continue;
                    asked++;
                    if (i < sitting.answers().size() && sitting.answers().get(i) == q.answer()) correct++;
                }
                if (writtenPassed(correct, asked)) out.add(subject);
            } else if (sitting.practicals().contains(subject)) {
                out.add(subject);
            }
        }
        return out;
    }

    /** This sitting's marks: coursework per paper, lifted where the sitting earned it. Papers not sat are absent. */
    public static Map<OWLSubject, OWLGrade> mark(Map<OWLSubject, OWLGrade> coursework, List<OWLSubject> papers,
                                                 Set<OWLSubject> lifted) {
        Map<OWLSubject, OWLGrade> out = new EnumMap<>(OWLSubject.class);
        for (OWLSubject subject : papers) {
            OWLGrade grade = coursework.getOrDefault(subject, OWLGrade.T);
            out.put(subject, lifted.contains(subject) ? lift(grade) : grade);
        }
        return Collections.unmodifiableMap(out);
    }

    /** Whether a practical of this kind is still owed in the sitting. */
    public static boolean practicalOpen(ExamSitting sitting, OWLSubject subject, long now) {
        return now < sitting.deadlineTick() && sitting.sits(subject) && practical(subject) != Practical.WRITTEN
                && !sitting.practicals().contains(subject);
    }
}
