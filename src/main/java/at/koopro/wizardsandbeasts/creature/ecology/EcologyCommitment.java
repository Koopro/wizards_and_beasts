package at.koopro.wizardsandbeasts.creature.ecology;

import org.jspecify.annotations.NullMarked;

/**
 * Whether a creature drops what it is doing for what {@link EcologyBrain} would now choose. Pure: the current
 * decision and how long it has been held, whether it still makes sense, the new candidate and the cooldowns in, the
 * decision to act on out ({@code EcologyCommitmentTest}).
 *
 * <p>Without this the brain's choice was the creature's behaviour, re-made every second, so a creature at the edge
 * of a radius, or between two needs of similar weight, switched on alternate looks. The rules, first match wins:
 * <ol>
 *   <li>held past its maximum: let it go, even if the brain would choose it again (the caller cools it down);</li>
 *   <li>the same intent again: keep it (the new decision's cause and urgency are taken);</li>
 *   <li>a candidate still cooling down after it last ended or failed counts as nothing;</li>
 *   <li>nothing under way, or the current intent no longer makes sense ({@link EcologyBrain#sustains}): take the
 *       candidate;</li>
 *   <li>a candidate in a higher {@link EcologyIntent.Band}: switch at once — a predator interrupts a meal;</li>
 *   <li>held for less than its minimum: keep it;</li>
 *   <li>a candidate in a lower band: keep the current one, which still makes sense;</li>
 *   <li>the same band: switch only if the candidate is more pressing by at least {@link #MARGIN}.</li>
 * </ol>
 */
@NullMarked
public final class EcologyCommitment {

    /** How much more pressing a same-band candidate must be to replace the current intent. */
    public static final float MARGIN = 0.15f;

    /** Which intents are cooling down. {@link EcologyState} is the real one. */
    public interface Cooldowns {
        boolean cooling(EcologyIntent intent, long now);
    }

    private EcologyCommitment() {}

    public static EcologyBrain.Decision arbitrate(EcologyBrain.Decision current, long heldSince, boolean sustained,
                                                  EcologyBrain.Decision candidate, long now, Cooldowns cooldowns) {
        long held = now - heldSince;
        if (timedOut(current.intent(), held)) {
            // Held as long as it may be: let it go even if the brain would pick it again; the caller cools it down.
            return candidate.intent() == current.intent() || cooldowns.cooling(candidate.intent(), now)
                    ? EcologyBrain.Decision.NONE : candidate;
        }
        if (candidate.intent() == current.intent()) {
            return candidate;
        }
        EcologyBrain.Decision next = candidate.intent() != EcologyIntent.NONE && cooldowns.cooling(candidate.intent(), now)
                ? EcologyBrain.Decision.NONE : candidate;
        if (current.intent() == EcologyIntent.NONE || !sustained) {
            return next;
        }
        int rise = next.intent().band().compareTo(current.intent().band());
        if (rise > 0) {
            return next;
        }
        if (held < current.intent().minTicks() || rise < 0) {
            return current;
        }
        return next.urgency() >= current.urgency() + MARGIN ? next : current;
    }

    /** Whether leaving {@code current} after {@code held} ticks counts as timing out (and so cools it down). */
    public static boolean timedOut(EcologyIntent current, long held) {
        return current != EcologyIntent.NONE && held >= current.maxTicks();
    }
}
