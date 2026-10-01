package at.koopro.wizardsandbeasts.admin.history;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The audit trail of administrative changes.
 *
 * <p>An interface so the Phase-1 {@link InMemoryChangeHistory} (server lifetime, bounded) can be swapped for
 * a persistent store without touching {@link at.koopro.wizardsandbeasts.admin.AdminSettingService}. Server
 * thread only.
 */
@NullMarked
public interface AdminChangeHistory {

    AdminChangeRecord record(Identifier settingId, AdminChangeRecord.Kind kind, String oldValue, String newValue,
                             @Nullable UUID actorId, String actorName, long timestampMillis,
                             boolean applied, @Nullable AdminRejection rejection, @Nullable String group);

    /** A record outside any batch. */
    default AdminChangeRecord record(Identifier settingId, AdminChangeRecord.Kind kind, String oldValue, String newValue,
                                     @Nullable UUID actorId, String actorName, long timestampMillis,
                                     boolean applied, @Nullable AdminRejection rejection) {
        return record(settingId, kind, oldValue, newValue, actorId, actorName, timestampMillis, applied, rejection, null);
    }

    /** The record with this sequence, if still held. */
    default Optional<AdminChangeRecord> find(long sequence) {
        return recent(Integer.MAX_VALUE).stream().filter(r -> r.sequence() == sequence).findFirst();
    }

    /** Every record of one batch, oldest first. */
    default List<AdminChangeRecord> inGroup(String group) {
        List<AdminChangeRecord> out = new java.util.ArrayList<>(recent(Integer.MAX_VALUE).stream()
                .filter(r -> group.equals(r.group())).toList());
        java.util.Collections.reverse(out);
        return out;
    }

    /** Newest first, at most {@code limit}. */
    List<AdminChangeRecord> recent(int limit);

    /** The most recent record {@link AdminChangeRecord#undoable()} would accept. */
    Optional<AdminChangeRecord> lastUndoable();

    void markUndone(long sequence);

    /**
     * Whether any applied, not-undone change touched a setting matching {@code settingFilter}. The dashboard
     * asks it with "is restart-bound" to decide whether a restart is pending.
     */
    boolean anyApplied(Predicate<Identifier> settingFilter);

    void clear();
}
