package at.koopro.wizardsandbeasts.pose;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A one-shot body gesture the server starts at a gameplay moment, and every client watching plays.
 *
 * <p>The counterpart of the cast animation for everything that is not a spell: the server knows when a wand is
 * inspected, a ritual begins or a root is pulled, so it says so, and clients never invent the timing. A held-item
 * pose that the render state already describes (a book being read, a potion drunk) is <em>not</em> a gesture — it is
 * derived on the client from vanilla's own synced use flag, with no packet.
 *
 * <p>The id travels on the wire rather than the ordinal, so a reordered enum never plays the wrong gesture.
 */
@NullMarked
public enum PlayerGesture {

    /** The wand brought up before the eyes, turned along its length and lowered again. */
    WAND_INSPECT("wand_inspect", 50);

    private final String id;
    private final int ticks;

    PlayerGesture(String id, int ticks) {
        this.id = id;
        this.ticks = ticks;
    }

    public String id() {
        return id;
    }

    /** Default length; a caller may play it for a different span (a ritual whose delay is configurable). */
    public int ticks() {
        return ticks;
    }

    public static @Nullable PlayerGesture byId(String id) {
        for (PlayerGesture gesture : values()) {
            if (gesture.id.equals(id)) {
                return gesture;
            }
        }
        return null;
    }
}
