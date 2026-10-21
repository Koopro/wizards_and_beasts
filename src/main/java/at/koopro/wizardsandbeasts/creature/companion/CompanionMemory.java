package at.koopro.wizardsandbeasts.creature.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a companion remembers beyond the bond number: who it is, what it has been taught, who it trusts,
 * and what has happened to it. Immutable; {@code BondState} holds the current one and saves it under a single
 * key, so a creature from before companions loads as {@link #EMPTY} and nothing else in its save changes.
 *
 * @param traits        its personality, rolled once when first bonded and kept for life
 * @param mode          the standing command; absent = follow, the behaviour every bonded creature had before
 * @param anchor        where it was told to stay or patrol
 * @param training      orders carried out, the measure of {@link CompanionStage#TRAINED}
 * @param trusted       players other than the owner who may feed, ride and calm it
 * @param hostile       players it treats, and lets be treated, as enemies
 * @param history       what has happened to it, newest last, bounded
 * @param generation    0 for a creature won from the wild, parents' generation + 1 for one raised
 * @param parents       its parents' names, for young raised in captivity
 * @param birthDay      in-game day it was born; -1 when won from the wild
 * @param capDay        the day the daily bond caps belong to
 * @param gainedToday   today's cap used per kind of interaction, in base units (see {@link CompanionRules.Gain})
 * @param trainingToday training earned today
 * @param readyAt       ability key → game time it may be used again
 * @param lastStage     the stage last announced, to notice a change
 */
@NullMarked
public record CompanionMemory(List<String> traits, Optional<CompanionCommand> mode, Optional<BlockPos> anchor,
                              int training, Set<UUID> trusted, Set<UUID> hostile, List<HistoryEntry> history,
                              int generation, List<String> parents, long birthDay, long capDay,
                              Map<Interaction, Integer> gainedToday, int trainingToday, Map<String, Long> readyAt,
                              CompanionStage lastStage) {

    /** History entries kept per creature: enough to tell its story, bounded so a save cannot grow. */
    public static final int MAX_HISTORY = 16;

    private static final Codec<Set<UUID>> UUID_SET = UUIDUtil.STRING_CODEC.listOf()
            .xmap(HashSet::new, ArrayList::new);
    private static final Codec<CompanionStage> STAGE = Codec.STRING.xmap(
            s -> {
                try {
                    return CompanionStage.valueOf(s);
                } catch (IllegalArgumentException e) {
                    return CompanionStage.WILD;
                }
            }, CompanionStage::name);

    private static final Codec<CompanionMemory> CORE = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("traits", List.of()).forGetter(CompanionMemory::traits),
            CompanionCommand.CODEC.optionalFieldOf("mode").forGetter(CompanionMemory::mode),
            BlockPos.CODEC.optionalFieldOf("anchor").forGetter(CompanionMemory::anchor),
            Codec.INT.optionalFieldOf("training", 0).forGetter(CompanionMemory::training),
            UUID_SET.optionalFieldOf("trusted", Set.of()).forGetter(CompanionMemory::trusted),
            UUID_SET.optionalFieldOf("hostile", Set.of()).forGetter(CompanionMemory::hostile),
            HistoryEntry.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(CompanionMemory::history),
            Codec.INT.optionalFieldOf("generation", 0).forGetter(CompanionMemory::generation),
            Codec.STRING.listOf().optionalFieldOf("parents", List.of()).forGetter(CompanionMemory::parents),
            Codec.LONG.optionalFieldOf("birth_day", -1L).forGetter(CompanionMemory::birthDay),
            Codec.LONG.optionalFieldOf("cap_day", -1L).forGetter(CompanionMemory::capDay),
            Codec.unboundedMap(Interaction.CODEC, Codec.INT).optionalFieldOf("gained_today", Map.of())
                    .forGetter(CompanionMemory::gainedToday),
            Codec.INT.optionalFieldOf("training_today", 0).forGetter(CompanionMemory::trainingToday),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ready_at", Map.of())
                    .forGetter(CompanionMemory::readyAt),
            STAGE.optionalFieldOf("last_stage", CompanionStage.WILD).forGetter(CompanionMemory::lastStage)
    ).apply(i, CompanionMemory::new));

    public static final Codec<CompanionMemory> CODEC = CORE;

    public static final CompanionMemory EMPTY = new CompanionMemory(List.of(), Optional.empty(), Optional.empty(),
            0, Set.of(), Set.of(), List.of(), 0, List.of(), -1L, -1L, Map.of(), 0, Map.of(), CompanionStage.WILD);

    public CompanionMemory {
        traits = List.copyOf(traits);
        trusted = Set.copyOf(trusted);
        hostile = Set.copyOf(hostile);
        history = List.copyOf(history);
        parents = List.copyOf(parents);
        gainedToday = gainedToday.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(gainedToday));
        readyAt = Map.copyOf(readyAt);
    }

    /** One remembered event: an in-game day, a lang key and an optional argument (a name, a stage). */
    public record HistoryEntry(long day, String key, String arg) {
        public static final Codec<HistoryEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(HistoryEntry::day),
                Codec.STRING.fieldOf("key").forGetter(HistoryEntry::key),
                Codec.STRING.optionalFieldOf("arg", "").forGetter(HistoryEntry::arg)
        ).apply(i, HistoryEntry::new));
    }

    public CompanionCommand modeOrFollow() {
        return mode.orElse(CompanionCommand.FOLLOW);
    }

    public boolean trusts(UUID player) {
        return trusted.contains(player);
    }

    public boolean hates(UUID player) {
        return hostile.contains(player);
    }

    // -- withers -------------------------------------------------------------------------------------------

    public CompanionMemory withTraits(List<String> next) {
        return new CompanionMemory(next, mode, anchor, training, trusted, hostile, history, generation, parents,
                birthDay, capDay, gainedToday, trainingToday, readyAt, lastStage);
    }

    public CompanionMemory withMode(CompanionCommand next, @Nullable BlockPos at) {
        return new CompanionMemory(traits, Optional.of(next), Optional.ofNullable(at), training, trusted, hostile,
                history, generation, parents, birthDay, capDay, gainedToday, trainingToday, readyAt, lastStage);
    }

    public CompanionMemory withTraining(int nextTraining, int nextToday, long day) {
        return new CompanionMemory(traits, mode, anchor, nextTraining, trusted, hostile, history, generation, parents,
                birthDay, day, day == capDay ? gainedToday : Map.of(), nextToday, readyAt, lastStage);
    }

    public CompanionMemory withGain(Interaction kind, int amount, long day) {
        Map<Interaction, Integer> next = new EnumMap<>(Interaction.class);
        if (day == capDay) {
            next.putAll(gainedToday);
        }
        next.merge(kind, amount, Integer::sum);
        return new CompanionMemory(traits, mode, anchor, training, trusted, hostile, history, generation, parents,
                birthDay, day, next, day == capDay ? trainingToday : 0, readyAt, lastStage);
    }

    /** Cap units already used today by {@code kind}. */
    public int gainedToday(Interaction kind, long day) {
        return day == capDay ? gainedToday.getOrDefault(kind, 0) : 0;
    }

    public int trainingToday(long day) {
        return day == capDay ? trainingToday : 0;
    }

    public CompanionMemory withTrust(UUID player, boolean trust) {
        Set<UUID> nextTrusted = new HashSet<>(trusted);
        Set<UUID> nextHostile = new HashSet<>(hostile);
        if (trust) {
            nextTrusted.add(player);
            nextHostile.remove(player);
        } else {
            nextTrusted.remove(player);
        }
        return new CompanionMemory(traits, mode, anchor, training, nextTrusted, nextHostile, history, generation,
                parents, birthDay, capDay, gainedToday, trainingToday, readyAt, lastStage);
    }

    public CompanionMemory withHostile(UUID player, boolean hostileNow) {
        Set<UUID> nextTrusted = new HashSet<>(trusted);
        Set<UUID> nextHostile = new HashSet<>(hostile);
        if (hostileNow) {
            nextHostile.add(player);
            nextTrusted.remove(player);
        } else {
            nextHostile.remove(player);
        }
        return new CompanionMemory(traits, mode, anchor, training, nextTrusted, nextHostile, history, generation,
                parents, birthDay, capDay, gainedToday, trainingToday, readyAt, lastStage);
    }

    public CompanionMemory remember(long day, String key, String arg) {
        List<HistoryEntry> next = new ArrayList<>(history);
        next.add(new HistoryEntry(day, key, arg));
        while (next.size() > MAX_HISTORY) {
            next.removeFirst();
        }
        return new CompanionMemory(traits, mode, anchor, training, trusted, hostile, next, generation, parents,
                birthDay, capDay, gainedToday, trainingToday, readyAt, lastStage);
    }

    public CompanionMemory withLineage(int nextGeneration, List<String> nextParents, long bornDay) {
        return new CompanionMemory(traits, mode, anchor, training, trusted, hostile, history, nextGeneration,
                nextParents, bornDay, capDay, gainedToday, trainingToday, readyAt, lastStage);
    }

    public CompanionMemory withReady(String abilityKey, long gameTime) {
        Map<String, Long> next = new HashMap<>(readyAt);
        next.put(abilityKey, gameTime);
        return new CompanionMemory(traits, mode, anchor, training, trusted, hostile, history, generation, parents,
                birthDay, capDay, gainedToday, trainingToday, next, lastStage);
    }

    public CompanionMemory withStage(CompanionStage stage) {
        return new CompanionMemory(traits, mode, anchor, training, trusted, hostile, history, generation, parents,
                birthDay, capDay, gainedToday, trainingToday, readyAt, stage);
    }

    /** Forgets its person: owner-specific memory goes, personality and lineage stay. */
    public CompanionMemory released(long day) {
        return new CompanionMemory(traits, Optional.empty(), Optional.empty(), 0, Set.of(), Set.of(), history,
                generation, parents, birthDay, capDay, Map.of(), 0, Map.of(), CompanionStage.WILD)
                .remember(day, "companion.wizards_and_beasts.history.released", "");
    }
}
