package at.koopro.wizardsandbeasts.bestiary;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class BestiaryEntryRegistry {
    private static final Map<Identifier, BestiaryEntry> ENTRIES = new HashMap<>();

    private BestiaryEntryRegistry() {}

    public static void replaceAll(Map<Identifier, BestiaryEntry> entries) {
        ENTRIES.clear();
        ENTRIES.putAll(entries);
    }

    public static @Nullable BestiaryEntry get(Identifier id) { return ENTRIES.get(id); }
    public static Collection<BestiaryEntry> getAll() { return Collections.unmodifiableCollection(ENTRIES.values()); }

    public static List<BestiaryEntry> getByCategory(BestiaryCategory category) {
        return ENTRIES.values().stream()
                .filter(e -> e.category() == category)
                .sorted(Comparator.comparingInt(BestiaryEntry::sortOrder).thenComparing(e -> e.id().toString()))
                .toList();
    }
}
