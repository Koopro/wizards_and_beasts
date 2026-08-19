package at.koopro.wizardsandbeasts.feedback;

import org.jspecify.annotations.NullMarked;

/**
 * What kind of thing a notice is telling the player, independent of how it gets shown.
 *
 * <p>Deliberately holds no colour. This enum crosses the wire and is read by server-side code, so it
 * must not reach into {@code client.gui.WizardsPalette}; the accent is chosen where the toast is
 * actually drawn.
 */
@NullMarked
public enum NoticeKind {
    /** Progression earned: a skill node, a vocation, a profession. */
    UNLOCK,
    /** Something learned about the world: a bestiary entry, a wand's resonance. */
    DISCOVERY,
    /** An action the player asked for completed. */
    SUCCESS,
    /** An action the player asked for was refused, with a reason. */
    FAIL,
    /** State the player should know about but did not ask for: corruption, allegiance loss. */
    WARN;

    /** Wire form. Ordinals are not used on the wire — a reorder must not change what clients decode. */
    public String serializedName() {
        return name();
    }

    public static NoticeKind byName(String name) {
        for (NoticeKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return SUCCESS;
    }
}
