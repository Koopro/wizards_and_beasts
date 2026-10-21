package at.koopro.wizardsandbeasts.owl.career.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A player's standing in every career track they have worked, keyed by profession name.
 *
 * <p>Per profession rather than one record, so a wizard who resits and changes career does not lose the
 * years spent in the first: going back to it resumes the rank they left. Held server-side as an attachment
 * with {@code copyOnDeath}; the client sees only the view the server builds from it.
 */
@NullMarked
public record PlayerCareerProgress(Map<String, TrackRecord> tracks) {

    public static final Codec<PlayerCareerProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, TrackRecord.CODEC).optionalFieldOf("tracks", Map.of())
                    .forGetter(PlayerCareerProgress::tracks)
    ).apply(i, PlayerCareerProgress::new));

    public static final PlayerCareerProgress EMPTY = new PlayerCareerProgress(Map.of());

    public PlayerCareerProgress {
        tracks = Map.copyOf(tracks);
    }

    public TrackRecord track(String profession) {
        return tracks.getOrDefault(profession, TrackRecord.FRESH);
    }

    public PlayerCareerProgress with(String profession, TrackRecord record) {
        Map<String, TrackRecord> next = new HashMap<>(tracks);
        next.put(profession, record);
        return new PlayerCareerProgress(next);
    }

    /**
     * One career's record.
     *
     * @param xp                 career experience earned
     * @param rank               index into the track's ranks
     * @param counters           lifetime activity counts, keyed by {@link CareerActivity#counterKey()}
     * @param xpDay              the in-game day {@code xpToday} belongs to
     * @param xpToday            experience paid today per rule index, for the daily caps
     * @param active             contracts in hand
     * @param lastCompleted      contract id → day last completed (re-offer cooldown)
     * @param lastAbandoned      contract id → day last abandoned (not re-offered the same day)
     * @param contractsCompleted contracts finished in this career
     */
    public record TrackRecord(int xp, int rank, Map<String, Integer> counters, long xpDay, Map<String, Integer> xpToday,
                              List<ActiveContract> active, Map<String, Long> lastCompleted,
                              Map<String, Long> lastAbandoned, int contractsCompleted) {

        public static final Codec<TrackRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("xp", 0).forGetter(TrackRecord::xp),
                Codec.INT.optionalFieldOf("rank", 0).forGetter(TrackRecord::rank),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of())
                        .forGetter(TrackRecord::counters),
                Codec.LONG.optionalFieldOf("xp_day", -1L).forGetter(TrackRecord::xpDay),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("xp_today", Map.of())
                        .forGetter(TrackRecord::xpToday),
                ActiveContract.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(TrackRecord::active),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("last_completed", Map.of())
                        .forGetter(TrackRecord::lastCompleted),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("last_abandoned", Map.of())
                        .forGetter(TrackRecord::lastAbandoned),
                Codec.INT.optionalFieldOf("contracts_completed", 0).forGetter(TrackRecord::contractsCompleted)
        ).apply(i, TrackRecord::new));

        public static final TrackRecord FRESH =
                new TrackRecord(0, 0, Map.of(), -1L, Map.of(), List.of(), Map.of(), Map.of(), 0);

        public TrackRecord {
            counters = Map.copyOf(counters);
            xpToday = Map.copyOf(xpToday);
            active = List.copyOf(active);
            lastCompleted = Map.copyOf(lastCompleted);
            lastAbandoned = Map.copyOf(lastAbandoned);
        }

        public TrackRecord withXp(int newXp) {
            return new TrackRecord(newXp, rank, counters, xpDay, xpToday, active, lastCompleted, lastAbandoned,
                    contractsCompleted);
        }

        public TrackRecord withRank(int newRank) {
            return new TrackRecord(xp, newRank, counters, xpDay, xpToday, active, lastCompleted, lastAbandoned,
                    contractsCompleted);
        }

        public TrackRecord withCounters(Map<String, Integer> next) {
            return new TrackRecord(xp, rank, next, xpDay, xpToday, active, lastCompleted, lastAbandoned,
                    contractsCompleted);
        }

        public TrackRecord withDailyXp(long day, Map<String, Integer> today) {
            return new TrackRecord(xp, rank, counters, day, today, active, lastCompleted, lastAbandoned,
                    contractsCompleted);
        }

        public TrackRecord withActive(List<ActiveContract> next) {
            return new TrackRecord(xp, rank, counters, xpDay, xpToday, next, lastCompleted, lastAbandoned,
                    contractsCompleted);
        }

        public TrackRecord completed(String contractId, long day) {
            Map<String, Long> done = new HashMap<>(lastCompleted);
            done.put(contractId, day);
            return new TrackRecord(xp, rank, counters, xpDay, xpToday, active, done, lastAbandoned,
                    contractsCompleted + 1);
        }

        public TrackRecord abandoned(String contractId, long day) {
            Map<String, Long> gone = new HashMap<>(lastAbandoned);
            gone.put(contractId, day);
            return new TrackRecord(xp, rank, counters, xpDay, xpToday, active, lastCompleted, gone,
                    contractsCompleted);
        }

        public @Nullable ActiveContract activeContract(String id) {
            for (ActiveContract contract : active) {
                if (contract.id().equals(id)) {
                    return contract;
                }
            }
            return null;
        }
    }

    /**
     * A contract in hand.
     *
     * @param id          the track's contract id
     * @param acceptedDay the in-game day it was taken
     * @param progress    activity counted toward it (unused for deliveries)
     * @param seen        creatures already counted, for distinct objectives
     */
    public record ActiveContract(String id, long acceptedDay, int progress, List<String> seen) {
        public static final Codec<ActiveContract> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(ActiveContract::id),
                Codec.LONG.fieldOf("accepted_day").forGetter(ActiveContract::acceptedDay),
                Codec.INT.optionalFieldOf("progress", 0).forGetter(ActiveContract::progress),
                Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(ActiveContract::seen)
        ).apply(i, ActiveContract::new));

        public ActiveContract {
            seen = List.copyOf(seen);
        }

        public static ActiveContract taken(String id, long day) {
            return new ActiveContract(id, day, 0, List.of());
        }
    }
}
