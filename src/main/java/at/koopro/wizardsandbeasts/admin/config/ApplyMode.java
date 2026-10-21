package at.koopro.wizardsandbeasts.admin.config;

import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * When a stored value actually reaches the game. Declared per setting by whoever knows its reader, shown on every row,
 * tooltip and command answer, and never inferred by a widget.
 *
 * <ul>
 *   <li>{@link #RUNTIME}: the next read sees it (almost every setting).</li>
 *   <li>{@link #RELOAD}: read from data a {@code /reload} rebuilds.</li>
 *   <li>{@link #RESTART}: read once at server start.</li>
 *   <li>{@link #NEW_CHUNKS}: decided when terrain generates. Existing chunks keep what they have; undoing what already
 *       generated (or getting a one-per-world structure whose spot is already explored) needs a new world.</li>
 * </ul>
 */
@NullMarked
public enum ApplyMode {
    RUNTIME(""),
    RELOAD(" ↻"),
    RESTART(" ⟳"),
    NEW_CHUNKS(" ◫");

    private final String badge;

    ApplyMode(String badge) {
        this.badge = badge;
    }

    /** The mark drawn after a row's name; empty for runtime settings. */
    public String badge() {
        return badge;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Restart required", "New chunks only" … */
    public String labelKey() {
        return "admin.wizards_and_beasts.apply." + id();
    }

    /** One sentence for the tooltip. */
    public String explanationKey() {
        return labelKey() + ".desc";
    }

    public static ApplyMode byOrdinal(int ordinal) {
        ApplyMode[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : RUNTIME;
    }
}
