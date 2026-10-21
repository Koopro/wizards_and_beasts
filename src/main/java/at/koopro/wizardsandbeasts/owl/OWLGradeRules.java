package at.koopro.wizardsandbeasts.owl;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * What an O.W.L. or N.E.W.T. examiner looks at, and what each answer is worth. Pure: no player, no registry.
 *
 * <p>Every subject reads something the game already records as the player plays — a spell's
 * practised tier, a brew collected, a Mandrake pulled, a bestiary page filled, a tome read, a night
 * charted through a telescope — so the exam certifies a build rather than asking for a separate grind.
 * The previous calculator counted skill-web nodes per tree against thresholds written for the 168-node
 * web (Herbology needed 15 nodes of a tree that now has 8) and read seven counters nothing ever wrote, so
 * most subjects could only grade Troll. {@code OWLGradeRulesTest} joins these ladders against the shipped
 * spell and node data and fails if an examined subject cannot reach O.
 *
 * <p>This is the <em>coursework</em> half of a grade. The sitting at the desk ({@code education.ExamRules})
 * may lift a subject one step for a practical done or a written section passed under the clock.
 *
 * <p>Thresholds are a {@link Ladder}: the score that earns A, E and O. Below A, a score of a third
 * of A or more is Poor, anything above zero Dreadful, and nothing at all Troll.
 */
@NullMarked
public final class OWLGradeRules {

    /** Points a spell's practised tier is worth: Proficient 1, Mastered 2. */
    public static final int PROFICIENT_POINTS = 1;
    public static final int MASTERED_POINTS = 2;

    /** Score needed for A, E and O. */
    public record Ladder(int a, int e, int o) {}

    /**
     * The evidence a candidate brings to the desk. Every field is read from live data by
     * {@link OWLGradeCalculator}.
     *
     * @param charmTierPoints   tier points over practised Utility spells
     * @param defenceTierPoints tier points over practised Combat and Defence spells (never Dark Arts)
     * @param animagusComplete  the Animagus transformation is complete
     * @param animagusRegistered and registered with the Ministry
     * @param brewPoints        complexity-weighted brews collected
     * @param plantsHarvested   mature Mandrakes pulled
     * @param herbologyNodes    Herbology web nodes held
     * @param studiedPages      bestiary pages at STUDIED or deeper
     * @param knownPages        bestiary pages at KNOWN
     * @param wandloreNodes     Wandlore web nodes held
     * @param loreRead          distinct lore tomes read
     * @param researchComplete  creatures whose bestiary research is complete (the field journal's checklist)
     * @param astronomyPoints   distinct moon phases observed plus distinct constellations charted by telescope
     * @param lessons           lessons taken from a keeper, per subject ({@code education.LessonServices})
     */
    public record Evidence(int charmTierPoints, int defenceTierPoints, boolean animagusComplete,
                           boolean animagusRegistered, int brewPoints, int plantsHarvested, int herbologyNodes,
                           int studiedPages, int knownPages, int wandloreNodes, int loreRead,
                           int researchComplete, int astronomyPoints, Map<OWLSubject, Integer> lessons) {
        public static final Evidence NONE = new Evidence(0, 0, false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());

        public int lessonsIn(OWLSubject subject) {
            return lessons.getOrDefault(subject, 0);
        }
    }

    // Charms: Utility spells practised. 3 = three spells proficient; 16 = eight mastered.
    static final Ladder CHARMS = new Ladder(3, 9, 16);
    // Defence: Combat and Defence spells practised, on the same scale.
    static final Ladder DEFENCE = new Ladder(3, 8, 16);
    // Transfiguration: 1 for a completed Animagus, 2 once registered. There is no partial credit.
    static final Ladder TRANSFIGURATION = new Ladder(1, 1, 2);
    // Potions: unchanged from the original calculator; brew points were always live.
    static final Ladder POTIONS = new Ladder(10, 30, 50);
    // Herbology: a Mandrake is 1, a Herbology node 3 (the tree has 8, so the web alone tops out at E).
    static final Ladder HERBOLOGY = new Ladder(6, 15, 30);
    static final int HERBOLOGY_NODE_POINTS = 3;
    // Care of Magical Creatures: a studied page 1, a known page 1 more.
    static final Ladder CREATURES = new Ladder(3, 8, 16);
    // Ancient Runes: Wandlore nodes (the tree has 11).
    static final Ladder RUNES = new Ladder(2, 5, 8);
    // History of Magic: three tomes exist, so each one read is a grade.
    static final Ladder HISTORY = new Ladder(1, 2, 3);
    // Astronomy: 8 moon phases + 12 constellations can be charted, one observation a night.
    static final Ladder ASTRONOMY = new Ladder(2, 5, 9);

    // N.E.W.T. ladders: the same evidence, read deeper. History of Magic has no N.E.W.T. (three tomes are the
    // whole subject, and the O.W.L. already asks for all of them). Transfiguration's coursework tops out at E
    // (a registered Animagus); its O is earned only by the practical — taking the form in the sitting.
    static final Ladder NEWT_CHARMS = new Ladder(16, 22, 28);
    static final Ladder NEWT_DEFENCE = new Ladder(12, 16, 20);
    static final Ladder NEWT_TRANSFIGURATION = new Ladder(1, 2, 3);
    static final Ladder NEWT_POTIONS = new Ladder(50, 80, 120);
    static final Ladder NEWT_HERBOLOGY = new Ladder(30, 45, 65);
    static final Ladder NEWT_CREATURES = new Ladder(16, 30, 50);
    static final Ladder NEWT_RUNES = new Ladder(8, 10, 13);
    static final Ladder NEWT_ASTRONOMY = new Ladder(9, 14, 20);

    /**
     * Lessons with a keeper that count toward an O.W.L.: three lessons are worth the subject's A threshold, and
     * no more — a teacher prepares you, the work earns the grade. N.E.W.T.s never count lessons.
     */
    public static final int LESSONS_TO_A = 3;

    private OWLGradeRules() {}

    /** Tier points one spell is worth after {@code hits} practised casts. */
    public static int tierPoints(int hits, int proficientAt, int masteredAt) {
        if (hits >= masteredAt) return MASTERED_POINTS;
        if (hits >= proficientAt) return PROFICIENT_POINTS;
        return 0;
    }

    /** The ladder for {@code subject} at {@code level}, or {@code null} for a paper that is not sat. */
    public static @Nullable Ladder ladder(Qualification level, OWLSubject subject) {
        return level == Qualification.OWL ? ladder(subject) : newtLadder(subject);
    }

    /** The O.W.L. ladder for an examined subject, or {@code null} for one that is not sat. */
    public static @Nullable Ladder ladder(OWLSubject subject) {
        return switch (subject) {
            case CHARMS -> CHARMS;
            case DEFENCE_AGAINST_DARK_ARTS -> DEFENCE;
            case TRANSFIGURATION -> TRANSFIGURATION;
            case POTIONS -> POTIONS;
            case HERBOLOGY -> HERBOLOGY;
            case CARE_OF_MAGICAL_CREATURES -> CREATURES;
            case ANCIENT_RUNES -> RUNES;
            case HISTORY_OF_MAGIC -> HISTORY;
            case ASTRONOMY -> ASTRONOMY;
            case ARITHMANCY, DIVINATION, MUGGLE_STUDIES -> null;
        };
    }

    /** The N.E.W.T. ladder, or {@code null} for a subject with no N.E.W.T. */
    public static @Nullable Ladder newtLadder(OWLSubject subject) {
        return switch (subject) {
            case CHARMS -> NEWT_CHARMS;
            case DEFENCE_AGAINST_DARK_ARTS -> NEWT_DEFENCE;
            case TRANSFIGURATION -> NEWT_TRANSFIGURATION;
            case POTIONS -> NEWT_POTIONS;
            case HERBOLOGY -> NEWT_HERBOLOGY;
            case CARE_OF_MAGICAL_CREATURES -> NEWT_CREATURES;
            case ANCIENT_RUNES -> NEWT_RUNES;
            case ASTRONOMY -> NEWT_ASTRONOMY;
            case HISTORY_OF_MAGIC, ARITHMANCY, DIVINATION, MUGGLE_STUDIES -> null;
        };
    }

    /** The candidate's work in a subject, before lessons; 0 for one that is not sat. */
    public static int score(OWLSubject subject, Evidence e) {
        return switch (subject) {
            case CHARMS -> e.charmTierPoints();
            case DEFENCE_AGAINST_DARK_ARTS -> e.defenceTierPoints();
            case TRANSFIGURATION -> e.animagusComplete() ? (e.animagusRegistered() ? 2 : 1) : 0;
            case POTIONS -> e.brewPoints();
            case HERBOLOGY -> e.plantsHarvested() + e.herbologyNodes() * HERBOLOGY_NODE_POINTS;
            // A completed study — every hands-on act, half its behaviours watched — is a point beyond the page's depth.
            case CARE_OF_MAGICAL_CREATURES -> e.studiedPages() + e.knownPages() + e.researchComplete();
            case ANCIENT_RUNES -> e.wandloreNodes();
            case HISTORY_OF_MAGIC -> e.loreRead();
            case ASTRONOMY -> e.astronomyPoints();
            case ARITHMANCY, DIVINATION, MUGGLE_STUDIES -> 0;
        };
    }

    /**
     * What lessons add to an O.W.L. score: up to the subject's A threshold, never past it — and never into E, so a
     * subject whose ladder puts A and E on the same mark (Transfiguration: the Animagus is done or it is not) cannot
     * be taught at all.
     */
    public static int lessonCredit(Ladder ladder, int lessons) {
        return lessonCap(ladder) * Math.clamp(lessons, 0, LESSONS_TO_A) / LESSONS_TO_A;
    }

    private static int lessonCap(Ladder ladder) {
        return Math.max(0, Math.min(ladder.a(), ladder.e() - 1));
    }

    /** Whether a lesson can count toward this subject's O.W.L. at all. */
    public static boolean teachable(OWLSubject subject) {
        Ladder ladder = ladder(subject);
        return ladder != null && lessonCap(ladder) > 0;
    }

    /** The coursework score at {@code level}: the work, plus lesson credit for an O.W.L. only. */
    public static int score(Qualification level, OWLSubject subject, Evidence e) {
        int work = score(subject, e);
        Ladder owl = ladder(subject);
        return level == Qualification.OWL && owl != null ? work + lessonCredit(owl, e.lessonsIn(subject)) : work;
    }

    /** The grade a score earns on a ladder. */
    public static OWLGrade grade(int score, Ladder ladder) {
        if (score >= ladder.o()) return OWLGrade.O;
        if (score >= ladder.e()) return OWLGrade.E;
        if (score >= ladder.a()) return OWLGrade.A;
        if (score <= 0) return OWLGrade.T;
        if (score * 3 < ladder.a()) return OWLGrade.D;
        return OWLGrade.P;
    }

    /** The coursework grade at {@code level}, or {@code null} for a paper that is not sat. */
    public static @Nullable OWLGrade grade(Qualification level, OWLSubject subject, Evidence e) {
        Ladder ladder = ladder(level, subject);
        return ladder == null ? null : grade(score(level, subject, e), ladder);
    }

    /** One subject's O.W.L. coursework grade (lessons counted), or {@code null} when the subject is not sat. */
    public static @Nullable OWLGrade grade(OWLSubject subject, Evidence e) {
        return grade(Qualification.OWL, subject, e);
    }

    /** O.W.L. grades for every examined subject. Subjects that are not sat are absent, not Troll. */
    public static Map<OWLSubject, OWLGrade> gradeAll(Evidence e) {
        return gradeAll(Qualification.OWL, e);
    }

    /** Coursework grades for every paper sat at {@code level}. */
    public static Map<OWLSubject, OWLGrade> gradeAll(Qualification level, Evidence e) {
        Map<OWLSubject, OWLGrade> grades = new EnumMap<>(OWLSubject.class);
        for (OWLSubject subject : OWLSubject.values()) {
            OWLGrade grade = grade(level, subject, e);
            if (grade != null) grades.put(subject, grade);
        }
        return Collections.unmodifiableMap(grades);
    }

    /**
     * A resit keeps the better of the two papers in each subject, so sitting again never costs a
     * grade already earned. Grades in subjects that are no longer sat are dropped.
     */
    public static Map<OWLSubject, OWLGrade> best(Map<OWLSubject, OWLGrade> previous, Map<OWLSubject, OWLGrade> fresh) {
        return best(Qualification.OWL, previous, fresh);
    }

    /** {@link #best(Map, Map)} for either level: papers not sat at {@code level} are dropped. */
    public static Map<OWLSubject, OWLGrade> best(Qualification level, Map<OWLSubject, OWLGrade> previous,
                                                 Map<OWLSubject, OWLGrade> fresh) {
        Map<OWLSubject, OWLGrade> out = new EnumMap<>(OWLSubject.class);
        for (OWLSubject subject : OWLSubject.values()) {
            if (ladder(level, subject) == null) continue;
            OWLGrade a = previous.get(subject);
            OWLGrade b = fresh.get(subject);
            OWLGrade kept = a == null ? b : b == null ? a : (a.value >= b.value ? a : b);
            if (kept != null) out.put(subject, kept);
        }
        return Collections.unmodifiableMap(out);
    }
}
