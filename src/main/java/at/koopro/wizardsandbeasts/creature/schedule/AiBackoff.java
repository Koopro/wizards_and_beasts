package at.koopro.wizardsandbeasts.creature.schedule;

import org.jspecify.annotations.NullMarked;

/**
 * Retry pacing for a search that can fail: a shelter, a place to flee to, a hoard. A search that found nothing is not
 * repeated on the next tick — it waits {@code base} ticks, then twice that, up to {@code max}; a success resets it.
 *
 * <p>The pattern this replaces is "search, fail, rest, and search again next tick", which turns a creature standing
 * where no answer exists into a search every tick for as long as it stands there. Behaviour a player can see is the
 * same (the creature rests or holds its ground either way); only the number of hopeless searches changes.
 *
 * <p><b>Cost:</b> two longs and an int per instance, no allocation, no world access. Not thread-safe; one per goal.
 */
@NullMarked
public final class AiBackoff {

    private final int base;
    private final int max;
    private long readyAt;
    private int failures;

    public AiBackoff(int base, int max) {
        if (base < 1 || max < base) {
            throw new IllegalArgumentException("backoff " + base + ".." + max);
        }
        this.base = base;
        this.max = max;
    }

    /** Whether the search may run at game time {@code now}. */
    public boolean ready(long now) {
        return now >= readyAt;
    }

    /** The search found nothing: wait longer before the next one. */
    public void fail(long now) {
        failures = Math.min(failures + 1, 16);
        long wait = Math.min((long) max, (long) base << (failures - 1));
        readyAt = now + wait;
    }

    /** The search found something: the next one may run at once. */
    public void succeed() {
        failures = 0;
        readyAt = 0L;
    }

    /** Consecutive failures since the last success. */
    public int failures() {
        return failures;
    }

    /** When the next search may run. */
    public long readyAt() {
        return readyAt;
    }
}
