package at.koopro.wizardsandbeasts.bestiary;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class BestiaryEntryRegistry {
    /** Volatile-swapped immutable map: readers never observe a mid-reload empty/partial registry. */
    private static volatile Map<Identifier, BestiaryEntry> ENTRIES = Map.of();

    /** Client-side cache, populated on {@code SyncBestiaryEntriesPayload} receipt. Read only on the client. */
    private static volatile Map<Identifier, BestiaryEntry> CLIENT_ENTRIES = Map.of();

    private BestiaryEntryRegistry() {}

    public static void replaceAll(Map<Identifier, BestiaryEntry> entries) {
        ENTRIES = Map.copyOf(entries);
    }

    public static @Nullable BestiaryEntry get(Identifier id) { return ENTRIES.get(id); }
    public static Collection<BestiaryEntry> getAll() { return ENTRIES.values(); }

    /** Replace the client cache with the synced entry list. */
    public static void setClientEntries(List<BestiaryEntry> entries) {
        Map<Identifier, BestiaryEntry> map = new HashMap<>();
        for (BestiaryEntry entry : entries) {
            map.put(entry.id(), entry);
        }
        CLIENT_ENTRIES = Map.copyOf(map);
    }

    public static @Nullable BestiaryEntry clientGet(Identifier id) { return CLIENT_ENTRIES.get(id); }
    public static Collection<BestiaryEntry> clientGetAll() { return CLIENT_ENTRIES.values(); }

    public static List<BestiaryEntry> getByCategory(BestiaryCategory category) {
        return ENTRIES.values().stream()
                .filter(e -> e.category() == category)
                .sorted(Comparator.comparingInt(BestiaryEntry::sortOrder).thenComparing(e -> e.id().toString()))
                .toList();
    }
}
