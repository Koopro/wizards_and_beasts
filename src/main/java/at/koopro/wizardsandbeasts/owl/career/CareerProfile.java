package at.koopro.wizardsandbeasts.owl.career;

import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * What a career is for in play. Pure table, no player.
 *
 * <p>A career used to be a word on the results sheet that nothing read. It now does two things, both
 * through systems that already exist:
 *
 * <ul>
 *   <li><b>Paid work.</b> Each career names one kind of work ({@link CareerWork.Kind}) — duelling for an
 *       Auror, brewing for a Healer, tending creatures for a Magizoologist. The first time in an in-game
 *       day the holder does that work, the wage goes into their Gringotts vault. That is the mod's only
 *       earned, repeatable income: before it, every coin was found once in a chest. It is paid per
 *       working day rather than per act, so the work cannot be farmed and a day off costs a day's pay.</li>
 *   <li><b>Papers.</b> With the Ministry module on, a career carries the licence its work needs: an
 *       Auror the trainee's warrant, a Magizoologist the dangerous-creatures licence, a Curse-Breaker
 *       Gringotts' own pass. Licences were otherwise unobtainable, which locked everyone out of every
 *       gate the Ministry runs.</li>
 * </ul>
 *
 * <p>No career multiplies a spell. A career is a job, and a job pays and grants standing to do it —
 * the stat side of a build lives in the skill web (documentation/SYSTEM_INTERACTION_MAP.md §5).
 *
 * @param work        the work that earns the day's wage
 * @param dailyWage   Knuts paid for a working day (493 Knuts = 1 Galleon)
 * @param careerPapers licences issued with the career, on top of those the O.W.L. passes earn
 */
@NullMarked
public record CareerProfile(CareerWork.Kind work, int dailyWage, Set<LicenseType> careerPapers) {

    private static final int GALLEON = 493;
    private static final int SICKLE = 29;

    /** The profile for a career. Every constant has one, offered or not. */
    public static CareerProfile of(Profession profession) {
        return switch (profession) {
            case AUROR -> new CareerProfile(CareerWork.Kind.DUELLING, GALLEON,
                    EnumSet.of(LicenseType.AUROR_TRAINEE, LicenseType.RESTRICTED_MAGIC));
            case HIT_WIZARD -> new CareerProfile(CareerWork.Kind.DUELLING, 15 * SICKLE, EnumSet.of(LicenseType.MINISTRY_ACCESS));
            case HEALER -> new CareerProfile(CareerWork.Kind.BREWING, GALLEON, EnumSet.of(LicenseType.RESTRICTED_SUBSTANCES));
            case POTIONEER -> new CareerProfile(CareerWork.Kind.BREWING, 14 * SICKLE, EnumSet.of(LicenseType.RESTRICTED_SUBSTANCES));
            case HERBOLOGIST -> new CareerProfile(CareerWork.Kind.HARVEST, 12 * SICKLE, EnumSet.of(LicenseType.RESTRICTED_SUBSTANCES));
            case MAGIZOOLOGIST -> new CareerProfile(CareerWork.Kind.CREATURE_CARE, 14 * SICKLE, EnumSet.of(LicenseType.DANGEROUS_CREATURES));
            case WANDMAKER -> new CareerProfile(CareerWork.Kind.WANDCRAFT, 16 * SICKLE, EnumSet.noneOf(LicenseType.class));
            case CURSE_BREAKER -> new CareerProfile(CareerWork.Kind.CHARMWORK, 19 * SICKLE,
                    EnumSet.of(LicenseType.MINISTRY_ACCESS, LicenseType.RESTRICTED_MAGIC));
            case OBLIVIATOR -> new CareerProfile(CareerWork.Kind.CHARMWORK, 15 * SICKLE, EnumSet.of(LicenseType.MINISTRY_ACCESS));
            case UNSPEAKABLE -> new CareerProfile(CareerWork.Kind.CHARMWORK, GALLEON, EnumSet.of(LicenseType.MINISTRY_ACCESS));
            case PROFESSOR -> new CareerProfile(CareerWork.Kind.TEACHING, 13 * SICKLE, EnumSet.noneOf(LicenseType.class));
            case QUIDDITCH_PLAYER -> new CareerProfile(CareerWork.Kind.FLYING, 12 * SICKLE, EnumSet.of(LicenseType.BROOM));
            case JOURNALIST -> new CareerProfile(CareerWork.Kind.CORRESPONDENCE, 10 * SICKLE, EnumSet.noneOf(LicenseType.class));
            case ARITHMANCER -> new CareerProfile(CareerWork.Kind.CHARMWORK, 0, EnumSet.noneOf(LicenseType.class));
        };
    }

    /**
     * Licences a passing O.W.L. earns on its own: a pass in the subject a licence is examined on
     * ({@link LicenseType#examinedSubject()}) is the application. Apparition and Animagus are left out —
     * each has its own test or register — and the Auror's warrant comes only with the career.
     */
    static final Set<LicenseType> ISSUED_ON_PASS = EnumSet.of(
            LicenseType.BROOM, LicenseType.DANGEROUS_CREATURES,
            LicenseType.MINISTRY_ACCESS, LicenseType.RESTRICTED_SUBSTANCES);

    /** Every licence a candidate with these grades and this career (may be null) is issued. */
    public static Set<LicenseType> papersFor(Map<OWLSubject, OWLGrade> grades, @Nullable Profession career) {
        Set<LicenseType> out = EnumSet.noneOf(LicenseType.class);
        for (LicenseType type : ISSUED_ON_PASS) {
            OWLGrade grade = grades.get(type.examinedSubject());
            if (grade != null && grade.passing) out.add(type);
        }
        if (career != null && career.offered()) out.addAll(of(career).careerPapers());
        return out;
    }

    /** Lang key naming the work, for the selection screen and the payday message. */
    public String workKey() {
        return "owls.career.work." + work.name().toLowerCase();
    }
}
