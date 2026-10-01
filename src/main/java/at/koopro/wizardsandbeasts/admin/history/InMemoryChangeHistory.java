package at.koopro.wizardsandbeasts.admin.history;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server-lifetime history in a bounded ring: the oldest record falls off once {@code capacity} is reached,
 * so a client spamming rejected requests costs a fixed amount of memory, not an unbounded one.
 */
@NullMarked
public final class InMemoryChangeHistory implements AdminChangeHistory {

    private final int capacity;
    /** Newest at the head. */
    private final Deque<AdminChangeRecord> records = new ArrayDeque<>();
    private long nextSequence = 1;
    /** Told about every new record and every undo — how a persistent store keeps a copy ({@code AdminHistoryStore}). */
    private @Nullable Listener listener;

    /** Persistence hook. */
    public interface Listener {
        void recorded(AdminChangeRecord record);

        void undone(long sequence);
    }

    public InMemoryChangeHistory(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public AdminChangeRecord record(Identifier settingId, AdminChangeRecord.Kind kind, String oldValue,
                                    String newValue, @Nullable UUID actorId, String actorName,
                                    long timestampMillis, boolean applied, @Nullable AdminRejection rejection,
                                    @Nullable String group) {
        AdminChangeRecord entry = new AdminChangeRecord(nextSequence++, settingId, kind, oldValue, newValue,
                actorId, actorName, timestampMillis, applied, rejection, false, group);
        records.addFirst(entry);
        while (records.size() > capacity) {
            records.removeLast();
        }
        if (listener != null) {
            listener.recorded(entry);
        }
        return entry;
    }

    /**
     * Replaces the held records with stored ones (newest first) and continues numbering after them — a server
     * starting on a world that already has a history. Listener is not told: these came from the store.
     */
    public void restore(List<AdminChangeRecord> newestFirst, long next) {
        records.clear();
        for (AdminChangeRecord record : newestFirst) {
            if (records.size() >= capacity) {
                break;
            }
            records.addLast(record);
        }
        nextSequence = Math.max(1, next);
    }

    public long nextSequence() {
        return nextSequence;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @Override
    public List<AdminChangeRecord> recent(int limit) {
        List<AdminChangeRecord> out = new ArrayList<>(Math.min(limit, records.size()));
        for (AdminChangeRecord entry : records) {
            if (out.size() >= limit) {
                break;
            }
            out.add(entry);
        }
        return out;
    }

    @Override
    public Optional<AdminChangeRecord> lastUndoable() {
        for (AdminChangeRecord entry : records) {
            if (entry.undoable()) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    @Override
    public void markUndone(long sequence) {
        // Rebuilt rather than mutated in place: records are immutable, and the deque is small and bounded.
        Deque<AdminChangeRecord> rebuilt = new ArrayDeque<>(records.size());
        for (Iterator<AdminChangeRecord> it = records.iterator(); it.hasNext(); ) {
            AdminChangeRecord entry = it.next();
            rebuilt.addLast(entry.sequence() == sequence ? entry.asUndone() : entry);
        }
        records.clear();
        records.addAll(rebuilt);
        if (listener != null) {
            listener.undone(sequence);
        }
    }

    @Override
    public boolean anyApplied(Predicate<Identifier> settingFilter) {
        for (AdminChangeRecord entry : records) {
            if (entry.applied() && !entry.undone() && settingFilter.test(entry.settingId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        records.clear();
    }
}
