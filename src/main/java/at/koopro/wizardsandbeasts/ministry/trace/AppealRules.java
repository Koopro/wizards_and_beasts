package at.koopro.wizardsandbeasts.ministry.trace;

import org.jspecify.annotations.NullMarked;

/**
 * Appeals to the full Wizengamot. Pure.
 *
 * <ul>
 *   <li>One appeal per case, lodged within the window, and only against a contested ruling: a wizard who pleaded
 *       guilty has already said what happened.</li>
 *   <li>The case is heard again on the same charges, contested, at a bar raised by
 *       {@link EvidenceRules#APPEAL_BONUS}, with the wizard's character as it stands <em>now</em>.</li>
 *   <li>An appeal can only help. A ruling the appeal court would make heavier stands as it was — nobody is
 *       punished for asking.</li>
 * </ul>
 */
@NullMarked
public final class AppealRules {

    /** Why an appeal may not be lodged, or {@link #ALLOWED}. */
    public enum Eligibility {
        ALLOWED,
        NO_CASE,
        NOTHING_TO_APPEAL,
        PLEADED_GUILTY,
        ALREADY_APPEALED,
        WINDOW_CLOSED
    }

    /** What the appeal court did, and the ruling that now stands. */
    public record Review(ClosedCase.Appeal outcome, Wizengamot.Ruling standing) {}

    private AppealRules() {}

    public static Eligibility eligibility(ClosedCase closed, long now) {
        // Checked first: an overturned ruling stands as a dismissal, and the honest answer is that it was appealed.
        if (closed.appeal() != ClosedCase.Appeal.NONE) {
            return Eligibility.ALREADY_APPEALED;
        }
        if (closed.ruling().verdict() == Verdict.DISMISSED) {
            return Eligibility.NOTHING_TO_APPEAL;
        }
        if (closed.hearing().plea() == Plea.GUILTY) {
            return Eligibility.PLEADED_GUILTY;
        }
        if (now > closed.appealBy()) {
            return Eligibility.WINDOW_CLOSED;
        }
        return Eligibility.ALLOWED;
    }

    /** Re-hears the case. Never returns a heavier ruling than the one appealed. */
    public static Review review(ClosedCase closed, boolean goodStandingNow) {
        Wizengamot.Ruling before = closed.ruling();
        Wizengamot.Ruling after = Wizengamot.rule(closed.hearing().onAppeal(goodStandingNow));
        if (after.verdict() == Verdict.DISMISSED) {
            return new Review(ClosedCase.Appeal.OVERTURNED, after);
        }
        if (after.severity() < before.severity()) {
            return new Review(ClosedCase.Appeal.REDUCED, after);
        }
        return new Review(ClosedCase.Appeal.UPHELD, before);
    }

    /** The last tick an appeal may be lodged for a ruling handed down at {@code closedAt}. */
    public static long appealBy(long closedAt, int windowDays) {
        return windowDays <= 0 ? 0L : closedAt + windowDays * 24000L;
    }
}
