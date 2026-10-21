package at.koopro.wizardsandbeasts.owl.career.track;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import org.jspecify.annotations.NullMarked;

/**
 * What a career goal may read about the player. An interface so the goals evaluate the same way on a
 * live player ({@code ServerCareerContext}) and in a unit test.
 */
@NullMarked
public interface CareerContext {

    /** The player's best grade in {@code subject}; {@link OWLGrade#T} when never sat. */
    OWLGrade grade(OWLSubject subject);

    /** The player's best N.E.W.T. grade in {@code subject}; {@link OWLGrade#T} when never sat. */
    default OWLGrade newtGrade(OWLSubject subject) {
        return OWLGrade.T;
    }

    /** Bestiary pages at {@code minTier} or deeper for creatures classified at least {@code minRating} (0: any). */
    int pages(DiscoveryTier minTier, int minRating);

    /** Creatures whose field research is complete. */
    int researchCompleted();

    /** The lifetime count of matching activity in this career (distinct species when {@code distinct}). */
    int activityTotal(ActivityFilter filter, boolean distinct);

    /** Contracts completed in this career. */
    int contractsCompleted();

    /**
     * Knuts in the player's vault, for certification fees ({@link CareerGoal.Fee}). Unlimited by default — and on a
     * live player while Gringotts is closed — so a fee never blocks a rank where there is no money to pay it with.
     */
    default long vaultKnuts() {
        return Long.MAX_VALUE;
    }

    /** The player's band on a standing axis; neutral by default, so a test context need not model standing. */
    default at.koopro.wizardsandbeasts.standing.StandingBand standingBand(
            at.koopro.wizardsandbeasts.standing.StandingAxis axis) {
        return at.koopro.wizardsandbeasts.standing.StandingBand.NEUTRAL;
    }
}
