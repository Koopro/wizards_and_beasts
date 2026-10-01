package at.koopro.wizardsandbeasts.creature.rules;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The conditions each creature's placement predicate checks, as lang keys, recorded where the predicate is
 * registered ({@code BeastSpawnHandler}, {@code NifflerSpawnHandler}).
 *
 * <p>A placement predicate is a lambda: nothing can read back what it tests. So the code that writes the predicate
 * also writes down, in the same place, what it tests — one line beside each registration — and the Creature Lab
 * shows that. Keys are {@code admin.wizards_and_beasts.spawn_condition.<key>}.
 */
@NullMarked
public final class SpawnConditionNotes {

    private static final Map<String, Set<String>> NOTES = new ConcurrentHashMap<>();

    private SpawnConditionNotes() {}

    public static void note(String creatureId, String... conditionKeys) {
        Set<String> notes = NOTES.computeIfAbsent(creatureId, id -> java.util.Collections.synchronizedSet(new LinkedHashSet<>()));
        notes.addAll(List.of(conditionKeys));
    }

    /** The recorded conditions, in registration order; empty when the creature registered no placement predicate. */
    public static List<String> of(String creatureId) {
        Set<String> notes = NOTES.get(creatureId);
        if (notes == null) {
            return List.of();
        }
        synchronized (notes) {
            return List.copyOf(new ArrayList<>(notes));
        }
    }
}
