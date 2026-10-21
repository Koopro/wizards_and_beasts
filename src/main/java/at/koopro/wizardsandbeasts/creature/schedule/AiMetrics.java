package at.koopro.wizardsandbeasts.creature.schedule;

import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counters for the creature AI's work, so its budget can be read off a running server (the perf scenario's
 * {@code WANDB_AI} line, guard tests) instead of guessed. One {@link LongAdder} increment per counted event; nothing
 * is timed, nothing is allocated.
 */
@NullMarked
public final class AiMetrics {

    /** What is counted. */
    public enum Metric {
        /** One ecology look around (one bounded entity query). */
        ECOLOGY_LOOK,
        /** A look taken early because an event woke the creature. */
        EVENT_WAKE,
        /** An event wake held back by the minimum gap between looks. */
        EVENT_WAKE_DEFERRED,
        /** A path asked of vanilla navigation by a paced pursuit. */
        PATH_REQUEST,
        /** A search skipped because its backoff had not elapsed. */
        BACKOFF_SKIP,
        /** One query around a stimulus (spell, death, herd alarm). */
        STIMULUS_QUERY,
        /** A death no creature could care about, answered without a query. */
        STIMULUS_SKIPPED
    }

    private static final Map<Metric, LongAdder> COUNTERS = new EnumMap<>(Metric.class);

    static {
        for (Metric metric : Metric.values()) {
            COUNTERS.put(metric, new LongAdder());
        }
    }

    private AiMetrics() {}

    public static void inc(Metric metric) {
        COUNTERS.get(metric).increment();
    }

    public static long get(Metric metric) {
        return COUNTERS.get(metric).sum();
    }

    /** Every counter, as {@code name=value} pairs, for a log line or a debug report. */
    public static String summary() {
        StringBuilder out = new StringBuilder();
        for (Metric metric : Metric.values()) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(metric.name().toLowerCase(Locale.ROOT)).append('=').append(get(metric));
        }
        return out.toString();
    }
}
