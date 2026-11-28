package at.koopro.wizardsandbeasts.loot;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a spell can be found in the world: the {@code place} of every spellbook loot modifier whose pool holds it.
 *
 * <p>The modifiers themselves are held by NeoForge's loot-modifier manager, which mods cannot reach, so each
 * {@link SpellbookLootModifier} that names a {@code place} records its pool here as it is loaded. A reload replaces
 * each place's pool as its modifier is read again; a place a datapack drops entirely stays until restart, which at
 * worst points the Mirror of Erised at shelves that no longer hold that spell.
 */
@NullMarked
public final class SpellSites {

    private static final Map<String, List<String>> POOLS = new ConcurrentHashMap<>();

    private SpellSites() {}

    /** Marks a pool entry that is a whole textbook ({@code spell_books/} id) rather than one spell. */
    static final String BOOK = "book:";

    static void record(String place, List<String> spells) {
        POOLS.put(place, List.copyOf(spells));
    }

    /**
     * A pool of loose spells and whole textbooks. A textbook's chapters are read when asked ({@link #placesOf}), not
     * now: the book table may load after the loot modifiers.
     */
    static void record(String place, List<String> spells, List<String> books) {
        List<String> all = new ArrayList<>(spells);
        books.forEach(b -> all.add(BOOK + b));
        POOLS.put(place, List.copyOf(all));
    }

    /**
     * The places whose pools hold {@code spellId}, sorted. Ids are compared without their namespace: pools list
     * spells bare ({@code "stupefy"}) while JSON spells register namespaced, and one spell has one path either way.
     */
    public static List<String> placesOf(String spellId) {
        String wanted = path(spellId);
        List<String> out = new ArrayList<>();
        POOLS.forEach((place, spells) -> {
            if (spells.stream().anyMatch(s -> holds(s, wanted))) {
                out.add(place);
            }
        });
        out.sort(null);
        return out;
    }

    private static boolean holds(String entry, String wantedPath) {
        if (!entry.startsWith(BOOK)) {
            return path(entry).equals(wantedPath);
        }
        var book = at.koopro.wizardsandbeasts.spell.learning.book.SpellBooks.get(entry.substring(BOOK.length()));
        return book != null && book.chapters().stream().anyMatch(c -> path(c).equals(wantedPath));
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /** Test seam: replace every pool. */
    public static void replaceAll(Map<String, List<String>> pools) {
        POOLS.clear();
        pools.forEach(SpellSites::record);
    }
}
