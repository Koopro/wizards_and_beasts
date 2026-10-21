package at.koopro.wizardsandbeasts.bestiary.data;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One player's history with one creature: where and when they first met it, the day each depth of knowledge came,
 * the hands-on study they have done, and whether the research is complete. Server-written, saved, synced — the
 * journal's "Discovery history" and "Research" sections read it, and the research reward is granted from it once.
 *
 * @param firstSeenDay   in-game day ({@code gameTime / 24000}) of the first sighting; -1 when never
 * @param firstSeenBiome biome id where it was first met, or empty
 * @param firstSeenWorld dimension id where it was first met, or empty
 * @param reachedDay     tier name → day that depth was reached
 * @param acts           study acts performed ({@code feed}, {@code handle}, {@code harvest}, {@code bond})
 * @param complete       research completed and rewarded
 */
@NullMarked
public record EntryHistory(long firstSeenDay, String firstSeenBiome, String firstSeenWorld,
                           Map<String, Long> reachedDay, Set<String> acts, boolean complete) {

    public static final EntryHistory NONE = new EntryHistory(-1L, "", "", Map.of(), Set.of(), false);

    public static final Codec<EntryHistory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("first_seen_day", -1L).forGetter(EntryHistory::firstSeenDay),
            Codec.STRING.optionalFieldOf("first_seen_biome", "").forGetter(EntryHistory::firstSeenBiome),
            Codec.STRING.optionalFieldOf("first_seen_world", "").forGetter(EntryHistory::firstSeenWorld),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("reached_day", Map.of())
                    .forGetter(EntryHistory::reachedDay),
            Codec.STRING.listOf().optionalFieldOf("acts", List.of()).forGetter(h -> List.copyOf(h.acts())),
            Codec.BOOL.optionalFieldOf("complete", false).forGetter(EntryHistory::complete)
    ).apply(instance, (day, biome, world, reached, acts, complete) ->
            new EntryHistory(day, biome, world, Map.copyOf(reached), Set.copyOf(new LinkedHashSet<>(acts)), complete)));

    /** A tier reached on {@code day}; the first depth also records where the creature was met. */
    public EntryHistory reached(DiscoveryTier tier, long day, String biome, String world) {
        Map<String, Long> days = new HashMap<>(reachedDay);
        days.putIfAbsent(tier.name(), day);
        boolean first = firstSeenDay < 0;
        return new EntryHistory(first ? day : firstSeenDay, first ? biome : firstSeenBiome,
                first ? world : firstSeenWorld, Map.copyOf(days), acts, complete);
    }

    public EntryHistory withAct(String act) {
        Set<String> next = new LinkedHashSet<>(acts);
        next.add(act);
        return new EntryHistory(firstSeenDay, firstSeenBiome, firstSeenWorld, reachedDay, Set.copyOf(next), complete);
    }

    public EntryHistory completed() {
        return new EntryHistory(firstSeenDay, firstSeenBiome, firstSeenWorld, reachedDay, acts, true);
    }

    public long dayReached(DiscoveryTier tier) {
        return reachedDay.getOrDefault(tier.name(), -1L);
    }
}
