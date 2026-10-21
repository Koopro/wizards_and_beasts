package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.jspecify.annotations.NullMarked;

/**
 * How an attempt ended. The first four come from the judge and can be computed on either side; the rest
 * are server verdicts about the attempt as a whole. Every value has a lang key
 * ({@code spell.wizards_and_beasts.trace.outcome.<key>}) so a failure always says what went wrong.
 */
@NullMarked
public enum TraceOutcome {
    PASSED("passed"),
    /** The first touch was not on the start mark. */
    NOT_STARTED("not_started"),
    /** The pointer left the corridor. */
    LEFT_PATH("left_path"),
    /** Released or ran out before reaching the goal. */
    UNFINISHED("unfinished"),
    /** Stayed inside, but wandered too far from the line on average. */
    INACCURATE("inaccurate"),
    /** Took longer than the difficulty allows (server clock). */
    TOO_SLOW("too_slow"),
    /** Faster than a hand can trace this path: not a trace. */
    TOO_FAST("too_fast"),
    /** No usable sample stream. */
    MALFORMED("malformed"),
    /** The book or lesson that opened the challenge is gone. */
    SOURCE_GONE("source_gone"),
    /** A learning gate refused at the moment of learning (the detail line carries the gate's reason). */
    REFUSED("refused"),
    /** Closed, died, changed dimension, or replaced by a newer challenge. */
    CANCELLED("cancelled");

    private final String key;

    TraceOutcome(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String langKey() {
        return "spell.wizards_and_beasts.trace.outcome." + key;
    }

    public boolean passed() {
        return this == PASSED;
    }

    public static TraceOutcome byOrdinal(int ordinal) {
        TraceOutcome[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : MALFORMED;
    }
}
