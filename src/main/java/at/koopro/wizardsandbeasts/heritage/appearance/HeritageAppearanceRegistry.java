package at.koopro.wizardsandbeasts.heritage.appearance;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The loaded {@link HeritageAppearance} entries, indexed for lookup by heritage and variant.
 *
 * <p>Mirrors {@code BestiaryEntryRegistry}: a volatile-swapped immutable map, so a reader mid-reload
 * sees either the old set or the new one and never a half-filled map. The client keeps its own copy,
 * populated from {@code SyncHeritageAppearancePayload} — reading the server-populated map directly
 * works only in a shared single-player JVM and is empty on a dedicated server.
 */
public final class HeritageAppearanceRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Index SERVER = Index.EMPTY;
    private static volatile Index CLIENT = Index.EMPTY;

    /**
     * Identifiers already reported as having no entry.
     *
     * <p>A miss is legal — a heritage with no appearance renders nothing — so it must not throw, and
     * it must not spam: the lookup runs from a render path, and one log line per frame per player is
     * how a debug aid becomes a performance bug. Concurrent because the client cache is read from the
     * render thread while the payload handler writes it.
     */
    private static final Set<String> REPORTED_MISSES = ConcurrentHashMap.newKeySet();

    private HeritageAppearanceRegistry() {}

    // ── server side ──

    public static void replaceAll(Map<Identifier, HeritageAppearance> entries) {
        SERVER = Index.of(entries.values());
        REPORTED_MISSES.clear();
    }

    public static Collection<HeritageAppearance> getAll() {
        return SERVER.byId.values();
    }

    public static @Nullable HeritageAppearance get(Identifier id) {
        return SERVER.byId.get(id);
    }

    // ── client side ──

    /** Replace the client cache with the synced list. Clears the miss log so a reload reports afresh. */
    public static void setClientEntries(List<HeritageAppearance> entries) {
        CLIENT = Index.of(entries);
        REPORTED_MISSES.clear();
    }

    public static Collection<HeritageAppearance> clientGetAll() {
        return CLIENT.byId.values();
    }

    /**
     * The entry that applies to this heritage and variant on the client, or null.
     *
     * <p>A variant entry wins over the heritage-wide entry, which covers every variant that does not
     * declare its own. A miss falls through to the shipped hardcoded registries and is logged once.
     */
    public static @Nullable HeritageAppearance clientResolve(@Nullable Heritage heritage,
                                                             @Nullable HeritageVariant variant) {
        return resolve(CLIENT, heritage, variant);
    }

    /** Server-side counterpart of {@link #clientResolve}, for commands and validation. */
    public static @Nullable HeritageAppearance resolve(@Nullable Heritage heritage,
                                                       @Nullable HeritageVariant variant) {
        return resolve(SERVER, heritage, variant);
    }

    private static @Nullable HeritageAppearance resolve(Index index,
                                                        @Nullable Heritage heritage,
                                                        @Nullable HeritageVariant variant) {
        if (heritage == null) {
            return null;
        }
        if (variant != null) {
            HeritageAppearance byVariant = index.byVariant.get(variant.getId());
            if (byVariant != null) {
                return byVariant;
            }
        }
        HeritageAppearance byHeritage = index.byHeritage.get(heritage.getId());
        if (byHeritage == null) {
            reportMissOnce(heritage, variant);
        }
        return byHeritage;
    }

    private static void reportMissOnce(Heritage heritage, @Nullable HeritageVariant variant) {
        String key = heritage.getId() + "/" + (variant == null ? "-" : variant.getId());
        if (REPORTED_MISSES.add(key)) {
            LOGGER.debug("[W&B] No heritage appearance entry for {}; falling back to the shipped form registry.", key);
        }
    }

    /** Test seam: drop everything, as a reload would. */
    public static void clear() {
        SERVER = Index.EMPTY;
        CLIENT = Index.EMPTY;
        REPORTED_MISSES.clear();
    }

    /** How many distinct misses have been logged. Exists so a test can assert the once-only contract. */
    public static int reportedMissCount() {
        return REPORTED_MISSES.size();
    }

    /**
     * The three lookup views, built once per reload.
     *
     * <p>Built rather than computed per call because the variant view is what a render path asks for,
     * and walking every entry to find one is the sort of thing that looks free until sixty players
     * render at once.
     */
    private record Index(Map<Identifier, HeritageAppearance> byId,
                         Map<String, HeritageAppearance> byHeritage,
                         Map<String, HeritageAppearance> byVariant) {

        static final Index EMPTY = new Index(Map.of(), Map.of(), Map.of());

        static Index of(Collection<HeritageAppearance> entries) {
            Map<Identifier, HeritageAppearance> byId = new HashMap<>();
            Map<String, HeritageAppearance> byHeritage = new HashMap<>();
            Map<String, HeritageAppearance> byVariant = new HashMap<>();

            for (HeritageAppearance entry : entries) {
                byId.put(entry.id(), entry);
                Optional<String> variant = entry.variant();
                if (variant.isPresent()) {
                    HeritageAppearance clash = byVariant.put(variant.get(), entry);
                    if (clash != null) {
                        LOGGER.warn("[W&B] Two heritage appearance entries claim variant '{}': {} and {}. "
                                + "Keeping {}.", variant.get(), clash.id(), entry.id(), entry.id());
                    }
                } else {
                    HeritageAppearance clash = byHeritage.put(entry.heritage(), entry);
                    if (clash != null) {
                        LOGGER.warn("[W&B] Two heritage appearance entries claim heritage '{}': {} and {}. "
                                + "Keeping {}.", entry.heritage(), clash.id(), entry.id(), entry.id());
                    }
                }
            }
            return new Index(Map.copyOf(byId), Map.copyOf(byHeritage), Map.copyOf(byVariant));
        }
    }
}
