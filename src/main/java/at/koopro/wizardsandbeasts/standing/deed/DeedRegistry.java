package at.koopro.wizardsandbeasts.standing.deed;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The loaded {@link Deed}s, bucketed by trigger.
 *
 * <p>Volatile-swapped immutable index, the same shape as {@code HeritageAppearanceRegistry} and
 * {@code BestiaryEntryRegistry}: a reader mid-reload sees either the whole old set or the whole new
 * one, never a half-filled map. That matters more here than for most registries, because the readers
 * are the spell cast path and the Ministry's report method — both hot, both reachable from a
 * different thread than the reload.
 *
 * <p>Server-side only. Deeds are never sent to clients: nothing on a client evaluates one, and the
 * standing they produce arrives as a value rather than as a rule.
 */
@NullMarked
public final class DeedRegistry {

    private static volatile Index INDEX = Index.EMPTY;

    private DeedRegistry() {}

    public static void replaceAll(Map<Identifier, Deed> deeds) {
        INDEX = Index.of(deeds);
    }

    /** Deeds listening for {@code trigger}, in load order. Empty — never null — when none are authored. */
    public static List<Map.Entry<Identifier, Deed>> forTrigger(DeedTrigger trigger) {
        return INDEX.byTrigger.getOrDefault(trigger, List.of());
    }

    public static Map<Identifier, Deed> all() {
        return INDEX.byId;
    }

    public static int count() {
        return INDEX.byId.size();
    }

    /** True when no deed anywhere listens for {@code trigger} — lets a hot hook return before it works. */
    public static boolean isEmpty(DeedTrigger trigger) {
        return forTrigger(trigger).isEmpty();
    }

    private record Index(Map<Identifier, Deed> byId,
                         Map<DeedTrigger, List<Map.Entry<Identifier, Deed>>> byTrigger) {

        static final Index EMPTY = new Index(Map.of(), Map.of());

        static Index of(Map<Identifier, Deed> deeds) {
            Map<Identifier, Deed> byId = Collections.unmodifiableMap(new LinkedHashMap<>(deeds));
            Map<DeedTrigger, List<Map.Entry<Identifier, Deed>>> buckets = new EnumMap<>(DeedTrigger.class);
            for (Map.Entry<Identifier, Deed> entry : byId.entrySet()) {
                buckets.computeIfAbsent(entry.getValue().trigger(), t -> new ArrayList<>()).add(entry);
            }
            buckets.replaceAll((trigger, list) -> Collections.unmodifiableList(list));
            return new Index(byId, Collections.unmodifiableMap(buckets));
        }
    }
}
