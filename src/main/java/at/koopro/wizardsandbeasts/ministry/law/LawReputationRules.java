package at.koopro.wizardsandbeasts.ministry.law;

import at.koopro.wizardsandbeasts.ministry.trace.Verdict;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.standing.StandingBand;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What the law does to a wizard's three reputations. Pure: the numbers a player sees move are pinned by
 * {@code LawReputationRulesTest}.
 *
 * <ul>
 *   <li><b>Public</b> reputation reads the verdict, the way the Daily Prophet would: a conviction is news in
 *       proportion to the sentence, an acquittal is a small item. Overturned on appeal, the story is retracted.</li>
 *   <li><b>Professional</b> reputation takes the same hit, twice over for those whose profession is enforcing the
 *       law — an Auror convicted of dark magic is a scandal, a herbologist is a footnote.</li>
 *   <li><b>Conduct</b> — the Ministry's own view, folded into Ministry standing — reads how the wizard dealt with
 *       the Ministry, not what they did: answering a summons and pleading honestly count for them, being caught
 *       after ignoring one counts against.</li>
 * </ul>
 */
@NullMarked
public final class LawReputationRules {

    /** Public standing an acquittal restores. */
    public static final float ACQUITTAL_CREDIT = 2.0f;
    /** Conduct for answering a summons in person. */
    public static final int CONDUCT_ANSWERED = 3;
    /** Conduct for a guilty plea — cooperation with the court. */
    public static final int CONDUCT_PLEADED = 2;
    /** Conduct for surrendering once Aurors were looking. */
    public static final int CONDUCT_SURRENDERED = 1;
    /** Conduct for being caught after the summons lapsed. */
    public static final int CONDUCT_CAUGHT = -5;
    /** Conduct for an appeal the court upheld in the wizard's favour. */
    public static final int CONDUCT_VINDICATED = 3;

    private LawReputationRules() {}

    /** Public standing lost to a verdict. */
    public static float publicPenalty(Verdict verdict) {
        return switch (verdict) {
            case DISMISSED -> 0.0f;
            case WARNING -> 2.0f;
            case FINE -> 5.0f;
            case WAND_CONFISCATION -> 10.0f;
            case AZKABAN_REFERRAL -> 25.0f;
        };
    }

    /** Professional standing lost to a verdict. */
    public static float professionPenalty(Verdict verdict, @Nullable Profession profession) {
        return publicPenalty(verdict) * (enforcesTheLaw(profession) ? 2.0f : 1.0f);
    }

    /** The professions held to the law they enforce. */
    public static boolean enforcesTheLaw(@Nullable Profession profession) {
        return profession == Profession.AUROR || profession == Profession.HIT_WIZARD
                || profession == Profession.OBLIVIATOR;
    }

    /** How a hearing's circumstances move conduct. */
    public static int conductFor(boolean appearedVoluntarily, boolean surrendered, boolean pleadedGuilty) {
        int conduct = appearedVoluntarily ? CONDUCT_ANSWERED : surrendered ? CONDUCT_SURRENDERED : CONDUCT_CAUGHT;
        return conduct + (pleadedGuilty ? CONDUCT_PLEADED : 0);
    }

    /** Public standing an appeal gives back: the difference between the two verdicts' penalties. */
    public static float publicRestored(Verdict before, Verdict after) {
        return Math.max(0.0f, publicPenalty(before) - publicPenalty(after));
    }

    /** Whether a wizard is believed as a witness. The public's pariah is not. */
    public static boolean credibleWitness(StandingBand publicBand) {
        return publicBand != StandingBand.STRONG_NEGATIVE;
    }

    /** Whether a wizard's Ministry standing counts as a character reference at a hearing. */
    public static boolean goodStanding(StandingBand ministryBand) {
        return ministryBand.isPositive();
    }
}
