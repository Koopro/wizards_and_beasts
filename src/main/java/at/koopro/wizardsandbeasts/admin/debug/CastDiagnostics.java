package at.koopro.wizardsandbeasts.admin.debug;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The last few cast events on the server, and how many times each kind of refusal has happened since start — what the
 * Debug section's live view shows when a cast "did nothing". Fed by {@code DebugHooks.logSpellCast}, the one hook every
 * cast outcome already passes (success, rejection, GAMP refusal or penalty, exception, refused packet).
 *
 * <p>Cheap on purpose: one small record per cast into a fixed ring, and a counter map capped at {@link #MAX_KINDS}
 * keys. Nothing is collected per tick or per frame, and nothing is written to the log from here.
 */
@NullMarked
public final class CastDiagnostics {

    public static final int CAPACITY = 64;
    public static final int MAX_KINDS = 64;

    /**
     * One cast event.
     *
     * @param event  {@code cast_success}, {@code cast_reject}, {@code cast_reject_packet}, {@code cast_gamp_reject},
     *               {@code cast_gamp_penalty}, {@code cast_exception}
     * @param detail the spell id for a success or exception, the reason for a refusal
     */
    public record Event(long timeMillis, long gameTick, String player, String event, String detail) {
        public boolean failure() {
            return !"cast_success".equals(event);
        }
    }

    private static final Deque<Event> RECENT = new ArrayDeque<>(CAPACITY);
    private static final Map<String, Integer> COUNTS = new HashMap<>();

    private CastDiagnostics() {}

    public static synchronized void record(String player, long gameTick, String event, String detail) {
        RECENT.addFirst(new Event(System.currentTimeMillis(), gameTick, player, event, detail));
        while (RECENT.size() > CAPACITY) {
            RECENT.removeLast();
        }
        String kind = "cast_success".equals(event) ? event : event + ":" + detail;
        if (COUNTS.containsKey(kind) || COUNTS.size() < MAX_KINDS) {
            COUNTS.merge(kind, 1, Integer::sum);
        }
    }

    /** Newest first. */
    public static synchronized List<Event> recent(int limit) {
        List<Event> out = new ArrayList<>(Math.min(limit, RECENT.size()));
        for (Event event : RECENT) {
            if (out.size() >= limit) {
                break;
            }
            out.add(event);
        }
        return out;
    }

    /** Counts by kind ({@code event:reason}, successes as {@code cast_success}), most frequent first. */
    public static synchronized List<Map.Entry<String, Integer>> counts(int limit) {
        return COUNTS.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Server stop: a new world starts with an empty record. */
    public static synchronized void clear() {
        RECENT.clear();
        COUNTS.clear();
    }
}
