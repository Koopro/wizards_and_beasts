package at.koopro.wizardsandbeasts.trunk.template;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Runtime store of loaded {@link PocketTemplate}s, keyed by {@code templateId}.
 * Not every {@code TrunkRecord.templateId} resolves here: trunk compartments use
 * synthetic ids ({@code trunk_lock_N}, {@code trunk_decoy}) with no template file —
 * callers must treat a null lookup as "no template semantics", not an error.
 */
public final class PocketTemplateRegistry {

    /** Volatile-swapped immutable map: readers never observe a mid-reload empty/partial registry. */
    private static volatile Map<String, PocketTemplate> TEMPLATES = Map.of();

    private PocketTemplateRegistry() {}

    public static void replaceAll(Map<String, PocketTemplate> loaded) {
        TEMPLATES = Map.copyOf(loaded);
    }

    public static @Nullable PocketTemplate get(String templateId) {
        return TEMPLATES.get(templateId);
    }

    public static Collection<PocketTemplate> getAll() {
        return TEMPLATES.values();
    }
}
