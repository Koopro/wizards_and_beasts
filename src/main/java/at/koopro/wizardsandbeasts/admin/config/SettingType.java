package at.koopro.wizardsandbeasts.admin.config;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How one kind of value is read from text, bounded, and written back to text.
 *
 * <p>Text is the one representation every entry point shares: a command argument, a packet field and a
 * history record are all strings, so parsing lives here exactly once. Nothing downstream — not a widget,
 * not a command — decides whether a value is acceptable.
 *
 * <p>Open rather than sealed: a new type implements this and reports the {@link SettingKind} whose control
 * should draw it. The built-in five are in {@link SettingTypes}.
 *
 * @param <T> the value type
 */
@NullMarked
public interface SettingType<T> {

    SettingKind kind();

    /** Parses {@code raw}, or null when it is not a value of this type at all. Never throws. */
    @Nullable T parse(String raw);

    /** Whether a parsed value lies inside this type's bounds. */
    boolean inBounds(T value);

    /** Canonical text form — what {@link #parse} accepts back and what the client displays. */
    String format(T value);

    // ── metadata for the UI; the server never trusts the client to honour it ──

    /** Lower bound for numeric kinds, NaN otherwise. */
    default double min() {
        return Double.NaN;
    }

    /** Upper bound for numeric kinds, NaN otherwise. */
    default double max() {
        return Double.NaN;
    }

    /** Suggested UI increment for numeric kinds, NaN otherwise. Not enforced: commands may set any in-range value. */
    default double step() {
        return Double.NaN;
    }

    /** Permitted values for {@link SettingKind#ENUM}, in display order; empty otherwise. */
    default List<String> options() {
        return List.of();
    }

    /** Longest accepted text for {@link SettingKind#STRING}; 0 otherwise. */
    default int maxLength() {
        return 0;
    }
}
