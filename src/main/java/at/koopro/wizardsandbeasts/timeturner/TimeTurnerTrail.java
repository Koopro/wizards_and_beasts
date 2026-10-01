package at.koopro.wizardsandbeasts.timeturner;

import at.koopro.wizardsandbeasts.timeturner.TimeTurnerRules.Moment;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.List;

/**
 * The places a Time-Turner remembers its wearer standing, oldest first — see {@link TimeTurnerRules}.
 *
 * <p>Server-side only, held on the wearer's {@code TIME_TURNER_TRAIL} attachment and never saved: a trail
 * that survived a relog, a death or a stretch without the Time-Turner would let it reach back to hours it never
 * spent with the wearer.
 */
@NullMarked
public final class TimeTurnerTrail {

    private final ArrayDeque<Moment> moments = new ArrayDeque<>();

    /** Remembers one more place, forgetting the oldest beyond {@link TimeTurnerRules#TRAIL_CAPACITY}. */
    public void record(Moment moment) {
        moments.addLast(moment);
        while (moments.size() > TimeTurnerRules.TRAIL_CAPACITY) {
            moments.removeFirst();
        }
    }

    /** Forgets everything: the Time-Turner was set aside, or its hours were just spent. */
    public void clear() {
        moments.clear();
    }

    public boolean isEmpty() {
        return moments.isEmpty();
    }

    public List<Moment> oldestFirst() {
        return List.copyOf(moments);
    }
}
