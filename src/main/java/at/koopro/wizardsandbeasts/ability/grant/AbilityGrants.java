package at.koopro.wizardsandbeasts.ability.grant;

import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * An immutable snapshot of every ability a player holds, with the source(s) that grant each. Grants are
 * <b>derived</b>, never stored: this is always the product of {@link #of} over the player's persistent
 * inputs (heritage variant tags, declared vocation flags, allocated skill-node grants). Because it is
 * recomputed, a source losing an input (vocation cleared, allocation refunded) simply drops that
 * source's grants on the next recompute — the revoke path is subtraction by omission, no bookkeeping.
 *
 * <p>The pure {@link #of} builder takes plain string collections so the model is testable without a
 * Minecraft bootstrap or a live player.
 */
@NullMarked
public final class AbilityGrants {

    /**
     * Where a grant comes from. Enum with room to grow (e.g. ITEM, RITUAL) without touching callers.
     * {@code DEBUG} is the command-driven override source ({@code /wandb ability grant|revoke}); its grants
     * are flagged so debug tooling can distinguish them from earned grants. {@code STATUS} covers abilities
     * earned as player state (Apparition licence, Animagus ritual) — see
     * {@link PlayerStatusAbilityGrantSource}.
     *
     * <p>{@code HERITAGE} has no built-in source any more: the heritage-tag path never matched an ability id
     * and was removed. The constant is retained so ordinals stay stable for the serialized/synced buckets and
     * so a future heritage-declared ability list has a home.
     */
    public enum Source { HERITAGE, VOCATION, SKILL_NODE, DEBUG, STATUS }

    public static final AbilityGrants EMPTY = new AbilityGrants(Map.of());

    /** key → the set of sources granting it. Insertion-ordered for deterministic sync/serialization. */
    private final Map<AbilityKey, EnumSet<Source>> grants;

    private AbilityGrants(Map<AbilityKey, EnumSet<Source>> grants) {
        this.grants = grants;
    }

    /**
     * Builds a grant snapshot from each source's raw ability strings. Strings are normalized into
     * {@link AbilityKey}s; the same key from two sources merges into one entry with both sources.
     */
    public static AbilityGrants of(Collection<String> heritageTags,
                                   Collection<String> vocationFlags,
                                   Collection<String> skillNodeAbilities) {
        Map<Source, Collection<String>> bySource = new java.util.EnumMap<>(Source.class);
        bySource.put(Source.HERITAGE, heritageTags);
        bySource.put(Source.VOCATION, vocationFlags);
        bySource.put(Source.SKILL_NODE, skillNodeAbilities);
        return ofSources(bySource);
    }

    /**
     * Generic builder over an arbitrary set of grant sources — the extensible entry point used by
     * {@link AbilityGrantService} once sources are pluggable. Iterates {@link Source#values()} for a
     * deterministic merge/serialization order regardless of map insertion order.
     */
    public static AbilityGrants ofSources(Map<Source, ? extends Collection<String>> bySource) {
        Map<AbilityKey, EnumSet<Source>> map = new LinkedHashMap<>();
        for (Source source : Source.values()) {
            Collection<String> raws = bySource.get(source);
            if (raws != null) {
                add(map, raws, source);
            }
        }
        return map.isEmpty() ? EMPTY : new AbilityGrants(map);
    }

    private static void add(Map<AbilityKey, EnumSet<Source>> map, Collection<String> raws, Source source) {
        for (String raw : raws) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            map.computeIfAbsent(AbilityKey.of(raw), k -> EnumSet.noneOf(Source.class)).add(source);
        }
    }

    public boolean has(AbilityKey key) {
        return grants.containsKey(key);
    }

    public boolean hasFrom(AbilityKey key, Source source) {
        EnumSet<Source> sources = grants.get(key);
        return sources != null && sources.contains(source);
    }

    /** The sources granting {@code key}, or an empty set if the player doesn't hold it. */
    public Set<Source> sourcesOf(AbilityKey key) {
        EnumSet<Source> sources = grants.get(key);
        return sources == null ? Set.of() : Collections.unmodifiableSet(sources);
    }

    /** Every held ability key. */
    public Set<AbilityKey> keys() {
        return Collections.unmodifiableSet(grants.keySet());
    }

    /** The keys granted by a specific source — the unit the sync payload transmits (one list per source). */
    public java.util.List<String> keysFrom(Source source) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Map.Entry<AbilityKey, EnumSet<Source>> entry : grants.entrySet()) {
            if (entry.getValue().contains(source)) {
                out.add(entry.getKey().id());
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return grants.isEmpty();
    }
}
