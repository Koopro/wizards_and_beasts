package at.koopro.wizardsandbeasts.heritage.werewolf;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The two decisions a feral werewolf makes about its prey, with no Minecraft in them.
 *
 * <ol>
 *   <li><b>Which of these is best?</b> {@link #pickBestIndex} — highest band, nearest within the band.</li>
 *   <li><b>Is that worth abandoning what I am already chasing?</b> {@link #shouldSwitch} — only if the
 *       current target is gone, or the new one is in a strictly higher band.</li>
 * </ol>
 *
 * <p>Splitting these out of {@link FeralTargeting} is what makes the aggression rules testable: they
 * are the part with the interesting behaviour and the part most likely to be tuned, and asserting them
 * in-game means standing in a field at midnight with a stopwatch. {@code FeralTargetSelectionTest}
 * covers them instead.
 *
 * <p><b>Distance never causes a switch.</b> That is the "high aggression, stick to your target" rule:
 * a nearer sheep does not distract a wolf already running down a further one, because both are
 * {@link FeralTargetPriority#ANIMAL}. Only a genuinely more interesting <em>kind</em> of prey does —
 * a player stepping into the clearing pulls the wolf off the sheep immediately. Distance decides only
 * <em>within</em> a band, when the wolf is choosing fresh.
 */
public final class FeralTargetSelection {

    private FeralTargetSelection() {}

    /**
     * One thing the wolf could go for, reduced to the two facts the decision needs.
     *
     * <p>The compact constructor sanitises rather than rejects. This is fed from live world state on a
     * server tick, and a {@code NaN} distance out of a degenerate position is not worth an exception
     * that would stop a werewolf mid-hunt — it is worth being sorted last.
     */
    public record Candidate(FeralTargetPriority priority, double distance) {
        public Candidate {
            if (priority == null) {
                throw new IllegalArgumentException("priority");
            }
            if (Double.isNaN(distance)) {
                distance = Double.MAX_VALUE;
            } else if (distance < 0.0) {
                distance = 0.0;
            }
        }
    }

    /**
     * Band weight minus distance in blocks. Higher is better.
     *
     * <p>The bands are spaced far enough apart that this can never let distance promote a candidate out
     * of its band — see {@link FeralTargetPriority}.
     */
    public static double score(Candidate candidate) {
        return candidate.priority().weight() - candidate.distance();
    }

    /**
     * Index of the best candidate, or {@code -1} when there are none.
     *
     * <p>Ties go to the earliest index, which keeps the choice stable: the entity query returns things
     * in a consistent order, so two identical cows at identical range do not make the wolf oscillate.
     */
    public static int pickBestIndex(List<Candidate> candidates) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < candidates.size(); i++) {
            double score = score(candidates.get(i));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /**
     * Whether to abandon the current target for {@code best}.
     *
     * @param current the target being chased, or {@code null} if there is none or it is no longer
     *                valid — dead, out of range, or gone from the world. Validity is expressed by
     *                passing null so that this method has one rule and not two.
     * @param best    the best candidate found this scan, or {@code null} if nothing qualifies
     */
    public static boolean shouldSwitch(@Nullable Candidate current, @Nullable Candidate best) {
        if (best == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        return best.priority().outranks(current.priority());
    }
}
