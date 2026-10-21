package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.broom.BroomTier;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import org.jspecify.annotations.NullMarked;

/**
 * Every number and threshold the licence system is made of, with no game attached.
 *
 * <p>Same split as {@code SneakoscopeTuning} and {@code FineSchedule}: the parts a player experiences
 * — how likely a forgery is to be spotted, which broom needs papers, what grade buys which
 * endorsement — are arithmetic, and arithmetic that cannot be unit-tested is arithmetic that drifts.
 * No Minecraft level, no {@code Config} read, no player.
 */
@NullMarked
public final class LicenceRules {

    /**
     * Chance, per Ministry interaction, that a forged licence is spotted.
     *
     * <p>Fifteen percent is roughly a coin-flip across four or five dealings: a forgery gets you
     * through the door and buys you a handful of transactions, and then it is a matter of when rather
     * than whether. That is the shape the brief asked for and it is also the only shape that makes
     * forging worth doing <em>and</em> worth being afraid of.
     */
    public static final float FORGERY_DETECTION_CHANCE = 0.15f;

    /** Blocks a Ministry official has to be within to be alerted by a detection. */
    public static final double ALERT_RADIUS = 24.0;

    /**
     * The lowest broom tier the Ministry does not care about. Anything at or above
     * {@link BroomTier#RACING} needs papers.
     *
     * <p>School and standard brooms are transport; a racing broom is what somebody outruns an Auror
     * on. Drawing the line here means the starter broom every player gets is never gated, which keeps
     * the licence a thing you grow into rather than a wall in front of the flight module.
     */
    public static final BroomTier LICENSED_BROOM_TIER = BroomTier.RACING;

    /** Ministry-palette blocks within {@link #AREA_SCAN_RADIUS} that make a position "inside the Ministry". */
    public static final int AREA_BLOCK_THRESHOLD = 24;
    /** Half-extent of the cube sampled around a player when deciding whether they are in a Ministry area. */
    public static final int AREA_SCAN_RADIUS = 6;

    /** Ticks between two Ministry-trespass filings, so standing in a lobby is one offence, not sixty. */
    public static final int TRESPASS_COOLDOWN_TICKS = 1200;

    /** Ticks between area checks. The scan is a 13³ box, so it does not want to run every tick. */
    public static final int AREA_CHECK_INTERVAL_TICKS = 40;

    private LicenceRules() {}

    /** Whether riding a broom of this tier needs a {@link LicenseType#BROOM} licence. */
    public static boolean requiresBroomLicence(BroomTier tier) {
        return tier.sortIndex() >= LICENSED_BROOM_TIER.sortIndex();
    }

    /**
     * The O.W.L. grade an examiner wants before endorsing {@code targetRank}.
     *
     * <p>Rank 1 asks only for a pass, rank 2 for an E, rank 3 for an O. The mod has no N.E.W.T. system
     * — the brief named one, but nothing in the repo implements it — so the endorsement ladder is
     * built out of the exam that does exist rather than out of a stub of one that does not.
     *
     * @return the grade required, or {@code null} when {@code targetRank} is not a rank you can be
     *         endorsed <em>to</em> (0, or past {@link LicenseType#MAX_RANK})
     */
    public static OWLGrade gradeRequiredFor(int targetRank) {
        return switch (targetRank) {
            case 1 -> OWLGrade.A;
            case 2 -> OWLGrade.E;
            case 3 -> OWLGrade.O;
            default -> null;
        };
    }

    /** Whether {@code held} is good enough for an endorsement that asks for {@code required}. */
    public static boolean gradeSatisfies(OWLGrade held, OWLGrade required) {
        return held.value >= required.value;
    }

    /**
     * True when a licence at {@code rank} is enough for a job that wants {@code requiredRank}.
     *
     * <p>Ranks are cumulative, like {@code MinistryRank}: anything a lower endorsement permits, a
     * higher one permits too, which keeps every permission check to one comparison.
     */
    public static boolean rankSatisfies(int rank, int requiredRank) {
        return rank >= requiredRank;
    }

    /** Whether a scan that found {@code blocks} Ministry blocks counts as being inside the Ministry. */
    public static boolean isMinistryDensity(int blocks) {
        return blocks >= AREA_BLOCK_THRESHOLD;
    }

    // ── the licence office ──

    /**
     * The licences a wizard may apply for over the counter. Apparition has its own test, Animagus its own
     * register, and the Auror's warrant comes only with the career; everything else is paperwork and a fee.
     */
    public static boolean soldAtOffice(LicenseType type) {
        return switch (type) {
            case APPARITION, ANIMAGUS, AUROR_TRAINEE -> false;
            default -> true;
        };
    }

    /**
     * The O.W.L. an application needs in the licence's examined subject. Licensed dark practice asks for an
     * Exceeds Expectations in Defence; everything else for a pass.
     */
    public static OWLGrade gradeToApply(LicenseType type) {
        return type == LicenseType.RESTRICTED_MAGIC ? OWLGrade.E : OWLGrade.A;
    }

    /** Why the licence office would refuse an application, or {@link #ISSUE}. */
    public enum Application {
        ISSUE,
        RENEW,
        NOT_SOLD_HERE,
        GRADE,
        SUSPENDED,
        OPEN_CASE,
        DISTRUSTED,
        ALREADY_HELD
    }

    /**
     * What the office does with an application. A suspension, an open case or a Ministry that thinks the applicant
     * a criminal ({@code distrusted}) refuses before anything else; a licence already held and valid is not issued
     * twice, but one that has expired is renewed.
     *
     * @param held        the applicant carries a licence of this type made out to them, not revoked
     * @param heldExpired that licence has expired
     */
    public static Application apply(LicenseType type, OWLGrade grade, boolean suspended, boolean openCase,
                                    boolean distrusted, boolean held, boolean heldExpired) {
        if (!soldAtOffice(type)) {
            return Application.NOT_SOLD_HERE;
        }
        if (suspended) {
            return Application.SUSPENDED;
        }
        if (openCase) {
            return Application.OPEN_CASE;
        }
        if (distrusted) {
            return Application.DISTRUSTED;
        }
        if (held) {
            return heldExpired ? Application.RENEW : Application.ALREADY_HELD;
        }
        return gradeSatisfies(grade, gradeToApply(type)) ? Application.ISSUE : Application.GRADE;
    }

    /** When a licence issued at {@code now} expires: never when the term is 0. */
    public static long expiryFor(long now, int termDays) {
        return termDays <= 0 ? LicenseData.NEVER_EXPIRES : now + termDays * 24000L;
    }
}
