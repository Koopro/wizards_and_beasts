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

    static void record(String place, List<String> spells) {
        POOLS.put(place, List.copyOf(spells));
    }

    /**
     * The places whose pools hold {@code spellId}, sorted. Ids are compared without their namespace: pools list
     * spells bare ({@code "stupefy"}) while JSON spells register namespaced, and one spell has one path either way.
     */
    public static List<String> placesOf(String spellId) {
        String wanted = path(spellId);
        List<String> out = new ArrayList<>();
        POOLS.forEach((place, spells) -> {
            if (spells.stream().anyMatch(s -> path(s).equals(wanted))) {
                out.add(place);
            }
        });
        out.sort(null);
        return out;
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
