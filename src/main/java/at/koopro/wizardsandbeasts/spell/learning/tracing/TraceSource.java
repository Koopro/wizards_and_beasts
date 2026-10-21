package at.koopro.wizardsandbeasts.spell.learning.tracing;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NullMarked;

/**
 * What opened a challenge, and therefore what has to still be there when it is passed.
 *
 * <p>Never taken from the client. The server records it when it opens the challenge and re-checks it
 * after the trace is judged, so a book dropped, swapped or read to pieces in the meantime teaches
 * nothing, and a node refunded mid-trace owes nothing.
 */
@NullMarked
public sealed interface TraceSource {

    /** A {@code SpellSourceItem} in the player's hand. */
    record Book(InteractionHand hand, Item item, String spellId, boolean spentOnRead) implements TraceSource {
    }

    /** A pending lesson from an allocated skill-web node. */
    record Lesson(String nodeId) implements TraceSource {
    }

    /** Whether passing uses this source up. Shown on the challenge screen before the player starts. */
    default boolean spendsSource() {
        return this instanceof Book book && book.spentOnRead();
    }
}
