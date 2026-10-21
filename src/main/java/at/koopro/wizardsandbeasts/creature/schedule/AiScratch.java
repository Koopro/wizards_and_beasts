package at.koopro.wizardsandbeasts.creature.schedule;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * A reusable list for a sensing query's results, so a once-a-second look around does not allocate a fresh
 * {@code ArrayList} per creature per look.
 *
 * <p>Borrow, fill (vanilla's {@code Level.getEntities(EntityTypeTest, AABB, Predicate, List, int)} appends into a
 * caller's list), read, release — in a {@code try/finally}. One list per thread: the server thread does all creature
 * sensing. A borrow while the list is out (a sensing call nested in another) gets a fresh list, never the busy one.
 * Released lists are cleared but keep their capacity; a list that grew past {@link #KEEP} is dropped so one crowded
 * look does not pin a large array for the rest of the session.
 */
@NullMarked
public final class AiScratch {

    /** Largest list kept between borrows. */
    public static final int KEEP = 256;

    private static final ThreadLocal<Slot> SLOT = ThreadLocal.withInitial(Slot::new);

    private static final class Slot {
        ArrayList<Entity> list = new ArrayList<>(64);
        boolean busy;
    }

    private AiScratch() {}

    /** An empty list to fill. Pair with {@link #release}. */
    public static List<Entity> borrow() {
        Slot slot = SLOT.get();
        if (slot.busy) {
            return new ArrayList<>();
        }
        slot.busy = true;
        return slot.list;
    }

    /** Hands a borrowed list back. A list that was not the shared one is simply dropped. */
    public static void release(List<Entity> list) {
        Slot slot = SLOT.get();
        if (list != slot.list) {
            return;
        }
        if (slot.list.size() > KEEP) {
            slot.list = new ArrayList<>(64);
        } else {
            slot.list.clear();
        }
        slot.busy = false;
    }
}
