package at.koopro.wizardsandbeasts.artefact.erised;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

/**
 * Something the player has not yet found and could reach today, as the Mirror would hint at it.
 *
 * <p>Built only from targets that are reachable at the moment of the gaze ({@link ErisedClues}); the selector
 * never has to ask again.
 *
 * @param key  stable identity, {@code "<kind>:<target>"}, so a clue once given is not given twice
 * @param text the line shown under the vision
 */
@NullMarked
public record ClueCandidate(Kind kind, String target, Component text) {

    public enum Kind { CREATURE, SPELL, FLOO, PLACE }

    public String key() {
        return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + target;
    }
}
