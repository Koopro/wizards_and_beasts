package at.koopro.wizardsandbeasts.bestiary.data;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

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
 */
public record PlayerBestiaryData(Map<Identifier, DiscoveryTier> tiers,
                                 Map<Identifier, Long> lastHarvests,
                                 Map<Identifier, Integer> observation) {

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
                    .forGetter(PlayerBestiaryData::observation)
    ).apply(instance, (levels, legacy, harvests, watched) -> {
        Map<Identifier, DiscoveryTier> tiers = new HashMap<>();
        legacy.forEach((id, name) -> {
            DiscoveryTier tier = DiscoveryTier.fromLegacySave(name);
            if (tier != null && tier != DiscoveryTier.UNKNOWN) {
                tiers.put(id, tier);
            }
        });
        tiers.putAll(levels);
        return new PlayerBestiaryData(tiers, new HashMap<>(harvests), new HashMap<>(watched));
    }));

    /** An empty record, for the attachment default. */
    public PlayerBestiaryData() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /** Discoveries only — the shape the client cache holds. */
    public PlayerBestiaryData(Map<Identifier, DiscoveryTier> tiers) {
        this(tiers, new HashMap<>(), new HashMap<>());
    }

    /** A copy with fresh mutable maps, for the helper's copy-and-replace writes. */
    public PlayerBestiaryData copy() {
        return new PlayerBestiaryData(new HashMap<>(tiers), new HashMap<>(lastHarvests), new HashMap<>(observation));
    }

    /** Ticks spent watching {@code entryId}. */
    public int observedTicks(Identifier entryId) {
        return observation.getOrDefault(entryId, 0);
    }
}
