package at.koopro.wizardsandbeasts.bestiary.data;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one player has learned about the world's creatures: how far each page has got, how long they have spent
 * watching each creature, and when they last took a rare material from each.
 *
 * <p>These live together because they are the same subject and the same lifetime: all of it must survive death
 * and a relog, and a second attachment would be a second thing to sync, copy on death and disagree with.
 *
 * <h2>Save format</h2>
 * Tiers are written by <b>index</b> under {@code tier_levels}. Saves from before the 2026-09-17 rename stored tier
 * <em>names</em> under {@code tiers}; those are still read, through {@link DiscoveryTier#fromLegacySave}, and never
 * written again. When both are present the index map wins.
 *
 * @param tiers        entry id → how much of it the player has earned
 * @param lastHarvests entry id → game time of the last successful rare harvest. Absent means never. Persisted, not
 *                     held in memory: a lockout a player could clear by relogging is not a lockout.
 * @param observation  entry id → ticks spent watching that creature calmly, toward {@link DiscoveryTier#OBSERVED}
 *                     and the observation route to {@link DiscoveryTier#STUDIED}. Persisted for the same reason.
 * @param fieldNotes   entry id → the ecology capabilities this player has seen the creature show (its herd regroup,
 *                     its flight from a predator, its day's rest). Written by {@code EcologyFieldNotes}, synced, and
 *                     printed on the page: what a player learns by watching, rather than by reading. Optional in
 *                     saves, so older saves load with none.
 * @param history      entry id → this player's history with the creature ({@link EntryHistory}): first sighting, the
 *                     day of each depth, study acts, research completion. Optional in saves.
 */
public record PlayerBestiaryData(Map<Identifier, DiscoveryTier> tiers,
                                 Map<Identifier, Long> lastHarvests,
                                 Map<Identifier, Integer> observation,
                                 Map<Identifier, Set<String>> fieldNotes,
                                 Map<Identifier, EntryHistory> history) {

    public static final Codec<PlayerBestiaryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, DiscoveryTier.INDEX_CODEC)
                    .optionalFieldOf("tier_levels", Map.of())
                    .forGetter(PlayerBestiaryData::tiers),
            // Read-only legacy names. Written as empty so a re-saved player carries only the index map.
            Codec.unboundedMap(Identifier.CODEC, Codec.STRING)
                    .optionalFieldOf("tiers", Map.of())
                    .forGetter(data -> Map.of()),
            // Optional so a save written before harvest existed loads with its discoveries intact.
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG)
                    .optionalFieldOf("lastHarvests", Map.of())
                    .forGetter(PlayerBestiaryData::lastHarvests),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                    .optionalFieldOf("observation", Map.of())
                    .forGetter(PlayerBestiaryData::observation),
            Codec.unboundedMap(Identifier.CODEC, Codec.STRING.listOf())
                    .optionalFieldOf("field_notes", Map.of())
                    .forGetter(data -> notesAsLists(data.fieldNotes())),
            Codec.unboundedMap(Identifier.CODEC, EntryHistory.CODEC)
                    .optionalFieldOf("history", Map.of())
                    .forGetter(PlayerBestiaryData::history)
    ).apply(instance, (levels, legacy, harvests, watched, notes, history) -> {
        Map<Identifier, DiscoveryTier> tiers = new HashMap<>();
        legacy.forEach((id, name) -> {
            DiscoveryTier tier = DiscoveryTier.fromLegacySave(name);
            if (tier != null && tier != DiscoveryTier.UNKNOWN) {
                tiers.put(id, tier);
            }
        });
        tiers.putAll(levels);
        return new PlayerBestiaryData(tiers, new HashMap<>(harvests), new HashMap<>(watched), notesAsSets(notes),
                new HashMap<>(history));
    }));

    /** The record without history: callers from before discovery history existed. */
    public PlayerBestiaryData(Map<Identifier, DiscoveryTier> tiers, Map<Identifier, Long> lastHarvests,
                              Map<Identifier, Integer> observation, Map<Identifier, Set<String>> fieldNotes) {
        this(tiers, lastHarvests, observation, fieldNotes, new HashMap<>());
    }

    /** This player's history with {@code entryId}; {@link EntryHistory#NONE} when there is none. */
    public EntryHistory historyOf(Identifier entryId) {
        return history.getOrDefault(entryId, EntryHistory.NONE);
    }

    /** The record without field notes: every caller from before field notes existed. */
    public PlayerBestiaryData(Map<Identifier, DiscoveryTier> tiers, Map<Identifier, Long> lastHarvests,
                              Map<Identifier, Integer> observation) {
        this(tiers, lastHarvests, observation, new HashMap<>(), new HashMap<>());
    }

    private static Map<Identifier, List<String>> notesAsLists(Map<Identifier, Set<String>> notes) {
        Map<Identifier, List<String>> out = new HashMap<>();
        notes.forEach((id, set) -> out.put(id, List.copyOf(set)));
        return out;
    }

    public static Map<Identifier, Set<String>> notesAsSets(Map<Identifier, List<String>> notes) {
        Map<Identifier, Set<String>> out = new HashMap<>();
        notes.forEach((id, list) -> out.put(id, new LinkedHashSet<>(list)));
        return out;
    }

    /** The field notes taken on {@code entryId}, in the order they were taken. */
    public Set<String> notesOn(Identifier entryId) {
        return fieldNotes.getOrDefault(entryId, Set.of());
    }

    /** An empty record, for the attachment default. */
    public PlayerBestiaryData() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /** Discoveries only — the shape the client cache holds. */
    public PlayerBestiaryData(Map<Identifier, DiscoveryTier> tiers) {
        this(tiers, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /** A copy with fresh mutable maps, for the helper's copy-and-replace writes. */
    public PlayerBestiaryData copy() {
        Map<Identifier, Set<String>> notes = new HashMap<>();
        fieldNotes.forEach((id, set) -> notes.put(id, new LinkedHashSet<>(set)));
        return new PlayerBestiaryData(new HashMap<>(tiers), new HashMap<>(lastHarvests), new HashMap<>(observation),
                notes, new HashMap<>(history));
    }

    /** Ticks spent watching {@code entryId}. */
    public int observedTicks(Identifier entryId) {
        return observation.getOrDefault(entryId, 0);
    }
}
