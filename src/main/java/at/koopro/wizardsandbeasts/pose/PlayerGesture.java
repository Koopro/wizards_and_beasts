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
    WAND_INSPECT("wand_inspect", 50),
    /** Both hands bring a book to the chest and open it: Bestiary, handbook. */
    OPEN_BOOK("open_book", 24),
    /** Both hands spread a folded sheet wide: the Marauder's Map. */
    UNFOLD("unfold", 28),
    /** One hand holds a small thing up to the face and keeps it there: Two-Way Mirror, Sneakoscope. */
    HOLD_UP("hold_up", 40),
    /** The hand raised overhead and a thumb's click: the Deluminator. */
    CLICK_UP("click_up", 18),
    /** Leaning in to look down into something: the Pensieve, the Mirror of Erised. */
    PEER("peer", 40),
    /** Both hands raise a blade and drive it down: the Sword of Gryffindor into a Horcrux. */
    STAB("stab", 16),
    /** Leaning over a cauldron, the hand going round. */
    STIR("stir", 32),
    /** A pinch dropped in: an ingredient added to a cauldron. */
    TOSS("toss", 12),
    /** A hand put out to a magical fitting: bench, cabinet, desk, hearth. */
    REACH("reach", 12),
    /** Floo Powder thrown into the green fire, then the arms tucked in for the whirl. */
    FLOO_THROW("floo_throw", 40),
    /** Two hands grip low and yank up: a Mandrake pulled. */
    PULL("pull", 16),
    /** A hand stroking a creature's coat: grooming. */
    STROKE("stroke", 30),
    /** A stoop to pick something off the ground: a creature's sign. */
    STOOP("stoop", 14),
    /** Doubled over, arms clutched to the chest, shaking: the werewolf change coming on. */
    HUNCH("hunch", 50);

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
