package at.koopro.wizardsandbeasts.admin.history;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One attempted administrative change, accepted or not.
 *
 * <p>Values are recorded as the setting type's canonical text, so a record stays readable (and an undo
 * stays replayable) without holding a reference to the setting that produced it.
 *
 * @param sequence    monotonically increasing per server lifetime; identifies the record for undo
 * @param settingId   the id as requested — for an unknown-setting rejection, exactly what was sent
 * @param kind        what kind of action produced the change
 * @param oldValue    value before the attempt ("" when there was no such setting)
 * @param newValue    value after an applied change, or the rejected request text
 * @param actorId     null for the console
 * @param applied     true when the value was stored
 * @param rejection   why it was refused; null when applied
 * @param undone      true once {@link AdminChangeHistory#markUndone} has consumed it
 * @param group       the batch it belongs to (a profile applied, a snapshot restored, a group reverted), or null
 */
@NullMarked
public record AdminChangeRecord(long sequence,
                                Identifier settingId,
                                Kind kind,
                                String oldValue,
                                String newValue,
                                @Nullable UUID actorId,
                                String actorName,
                                long timestampMillis,
                                boolean applied,
                                @Nullable AdminRejection rejection,
                                boolean undone,
                                @Nullable String group) {

    /** A record outside any batch. */
    public AdminChangeRecord(long sequence, Identifier settingId, Kind kind, String oldValue, String newValue,
                             @Nullable UUID actorId, String actorName, long timestampMillis, boolean applied,
                             @Nullable AdminRejection rejection, boolean undone) {
        this(sequence, settingId, kind, oldValue, newValue, actorId, actorName, timestampMillis, applied, rejection,
                undone, null);
    }

    public enum Kind {
        CHANGE,
        RESET,
        UNDO,
        /** Part of a profile, snapshot or import being applied. */
        PROFILE,
        /** Undoing one change or a whole group, from the history page. */
        REVERT
    }

    /** Whether "undo last change" may pick this record. An undo is not itself undoable — there is no redo. */
    public boolean undoable() {
        return applied && !undone && kind != Kind.UNDO && kind != Kind.REVERT;
    }

    AdminChangeRecord asUndone() {
        return new AdminChangeRecord(sequence, settingId, kind, oldValue, newValue, actorId, actorName,
                timestampMillis, applied, rejection, true, group);
    }
}
