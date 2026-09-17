package at.koopro.wizardsandbeasts.ministry.trace;

import org.jspecify.annotations.NullMarked;

/**
 * A wizard's age, and whether the Trace is still on them.
 *
 * <p>Canon: "the Trace … detects magical activity around the under-seventeens" and "breaks" on the
 * seventeenth birthday (<i>Deathly Hallows</i> ch. 4). An age is stored as the years a character had at a
 * given game tick, so it advances with world time — shared by every dimension — rather than with play time.
 *
 * <p>{@link #UNSET} means the Ministry holds no birth record, which reads as of age: a server that never
 * sets an age polices nobody's childhood.
 */
@NullMarked
public final class WizardingAge {

    /** No birth record. Treated as of age. */
    public static final int UNSET = -1;

    /** The Trace lifts at seventeen. */
    public static final int COMING_OF_AGE = 17;

    /** A Hogwarts letter arrives at eleven; nobody younger is given a wand. */
    public static final int YOUNGEST = 11;

    private WizardingAge() {}

    /**
     * Years old at {@code now}.
     *
     * @param yearsAtAnchor the age recorded at {@code anchorTick}, or {@link #UNSET}
     * @param ticksPerYear  game ticks one year of a character's life takes; at least one
     * @return the age now, or {@link #UNSET}
     */
    public static int yearsAt(int yearsAtAnchor, long anchorTick, long now, long ticksPerYear) {
        if (yearsAtAnchor < 0) {
            return UNSET;
        }
        long elapsed = Math.max(0L, now - anchorTick);
        long years = elapsed / Math.max(1L, ticksPerYear);
        return (int) Math.min(Integer.MAX_VALUE, yearsAtAnchor + years);
    }

    /** True while the Trace applies. An unset age is of age. */
    public static boolean isUnderage(int years) {
        return years != UNSET && years < COMING_OF_AGE;
    }
}
