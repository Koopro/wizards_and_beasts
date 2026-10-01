package at.koopro.wizardsandbeasts.admin;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Why the server refused an administrative request. Sent on the wire by {@link #name()}. */
@NullMarked
public enum AdminRejection {
    /** The actor lacks the capability — or is not an administrator at all. */
    UNAUTHORIZED,
    /** No setting has that id. */
    UNKNOWN_SETTING,
    /** Read on each player's client; the server's copy is not authoritative, so it will not write it. */
    CLIENT_ONLY,
    /** The text is not a value of the setting's type. */
    INVALID_VALUE,
    /** A value of the right type outside the setting's bounds. */
    OUT_OF_RANGE,
    /** Rejected by a rule relating it to other settings; the result's detail key says which. */
    CONFLICT,
    /** The backing store could not be read or written (config not loaded, write failed). */
    UNAVAILABLE,
    /** Undo was asked for with no undoable change in the history. */
    NOTHING_TO_UNDO,
    /**
     * Not a refusal but a question: the change is dangerous and was sent without confirmation. Nothing was
     * stored; the result's detail key is the warning to show. Resending with confirmation applies it.
     */
    CONFIRMATION_REQUIRED;

    public String translationKey() {
        return "admin.wizards_and_beasts.rejection." + name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable AdminRejection byName(String name) {
        for (AdminRejection rejection : values()) {
            if (rejection.name().equals(name)) {
                return rejection;
            }
        }
        return null;
    }
}
