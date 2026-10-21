package at.koopro.wizardsandbeasts.owl.career.track;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.ActiveContract;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.TrackRecord;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * The arithmetic of a career track: experience and its daily caps, contract progress, the daily board,
 * promotion. Pure — no player, no level, no registry — so every rule is unit-tested and the server wrapper
 * ({@link CareerProgression}) only reads state, calls these and writes the result back.
 */
@NullMarked
public final class CareerRules {

    private CareerRules() {}

    // -- experience ----------------------------------------------------------------------------------

    /** Experience paid for one activity, and the record with today's cap spent. */
    public record XpAward(int xp, TrackRecord record) {}

    /**
     * Pays the first matching rule, within its daily cap.
     *
     * <p>Capped per day for the same reason the wage and spell practice are: a career is advanced by doing
     * its work across days, not by repeating one act until the meter fills.
     */
    public static XpAward awardXp(CareerTrack track, TrackRecord record, CareerActivity done, long day) {
        Map<String, Integer> today = day == record.xpDay() ? new HashMap<>(record.xpToday()) : new HashMap<>();
        for (int i = 0; i < track.xpRules().size(); i++) {
            CareerTrack.XpRule rule = track.xpRules().get(i);
            if (!rule.when().matches(done)) {
                continue;
            }
            String key = Integer.toString(i);
            int paid = today.getOrDefault(key, 0);
            int allowed = rule.dailyCap() <= 0 ? rule.xp() : Math.min(rule.xp(), rule.dailyCap() - paid);
            if (allowed <= 0) {
                return new XpAward(0, record);
            }
            today.put(key, paid + allowed);
            return new XpAward(allowed, record.withDailyXp(day, today).withXp(record.xp() + allowed));
        }
        return new XpAward(0, record);
    }

    /** Adds one to the activity's lifetime counter. */
    public static TrackRecord count(TrackRecord record, CareerActivity done) {
        Map<String, Integer> next = new HashMap<>(record.counters());
        next.merge(done.counterKey(), 1, Integer::sum);
        return record.withCounters(next);
    }

    /**
     * Lifetime matching activity: a sum, or the number of different creatures when {@code distinct}.
     *
     * @param rating the Ministry classification of a creature (0 when unclassified), for rating filters
     */
    public static int activityTotal(Map<String, Integer> counters, ActivityFilter filter, boolean distinct,
                                    ToIntFunction<Identifier> rating) {
        int sum = 0;
        Set<Identifier> species = new HashSet<>();
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            CareerActivity done = parse(entry.getKey(), rating);
            if (done == null || !filter.matches(done)) {
                continue;
            }
            if (distinct) {
                if (done.species() != null) {
                    species.add(done.species());
                }
            } else {
                sum += entry.getValue();
            }
        }
        return distinct ? species.size() : sum;
    }

    static @Nullable CareerActivity parse(String key, ToIntFunction<Identifier> rating) {
        String[] parts = key.split("\\|", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            ActivityType type = ActivityType.valueOf(parts[0]);
            Identifier species = parts[1].isEmpty() ? null : Identifier.tryParse(parts[1]);
            DiscoveryTier tier = parts[2].isEmpty() ? null : DiscoveryTier.valueOf(parts[2]);
            return new CareerActivity(type, species, tier, species == null ? 0 : rating.applyAsInt(species));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -- contracts -----------------------------------------------------------------------------------

    /** The record after an activity, and the activity contracts it finished (removed from {@code active}). */
    public record ContractAdvance(TrackRecord record, List<ContractDefinition> finished) {}

    /**
     * Counts an activity toward every activity contract in hand that it matches. A contract counts only
     * what happens after it was taken: progress lives on the contract, not in the lifetime counters.
     */
    public static ContractAdvance advanceContracts(CareerTrack track, TrackRecord record, CareerActivity done) {
        List<ActiveContract> kept = new ArrayList<>(record.active().size());
        List<ContractDefinition> finished = new ArrayList<>();
        boolean changed = false;
        for (ActiveContract contract : record.active()) {
            ContractDefinition def = track.contract(contract.id());
            if (def == null || !(def.objective() instanceof ContractDefinition.Activity objective)
                    || !objective.filter().matches(done)) {
                kept.add(contract);
                continue;
            }
            ActiveContract next;
            if (objective.distinct()) {
                if (done.species() == null || contract.seen().contains(done.species().toString())) {
                    kept.add(contract);
                    continue;
                }
                List<String> seen = new ArrayList<>(contract.seen());
                seen.add(done.species().toString());
                next = new ActiveContract(contract.id(), contract.acceptedDay(), contract.progress() + 1, seen);
            } else {
                next = new ActiveContract(contract.id(), contract.acceptedDay(), contract.progress() + 1,
                        contract.seen());
            }
            changed = true;
            if (next.progress() >= objective.count()) {
                finished.add(def);
            } else {
                kept.add(next);
            }
        }
        return new ContractAdvance(changed ? record.withActive(kept) : record, finished);
    }

    /** The last day a contract taken on {@code acceptedDay} may still be finished. */
    public static long dueDay(ContractDefinition def, long acceptedDay) {
        return acceptedDay + def.durationDays() - 1;
    }

    public static boolean expired(ContractDefinition def, ActiveContract contract, long day) {
        return day > dueDay(def, contract.acceptedDay());
    }

    /**
     * Today's board: up to {@code boardSize} contracts drawn by weight from those the rank allows, then any
     * already in hand, recently completed or abandoned today struck off.
     *
     * <p>The draw depends only on the player, the day and the rank, so the board is the same every time it
     * is opened today, and taking one offer does not reshuffle the others.
     */
    public static List<ContractDefinition> offers(CareerTrack track, TrackRecord record, long seed, long day) {
        List<ContractDefinition> pool = new ArrayList<>();
        for (ContractDefinition def : track.contracts()) {
            int min = track.rankIndex(def.minRank());
            if (min >= 0 && min <= record.rank()) {
                pool.add(def);
            }
        }
        Random random = new Random(seed * 31L + day * 1_000_003L + record.rank());
        List<ContractDefinition> drawn = new ArrayList<>();
        while (!pool.isEmpty() && drawn.size() < track.boardSize()) {
            int total = 0;
            for (ContractDefinition def : pool) {
                total += def.weight();
            }
            int roll = random.nextInt(total);
            for (int i = 0; i < pool.size(); i++) {
                roll -= pool.get(i).weight();
                if (roll < 0) {
                    drawn.add(pool.remove(i));
                    break;
                }
            }
        }
        List<ContractDefinition> offered = new ArrayList<>(drawn.size());
        for (ContractDefinition def : drawn) {
            if (record.activeContract(def.id()) != null) {
                continue;
            }
            Long completed = record.lastCompleted().get(def.id());
            if (completed != null && day - completed < def.cooldownDays()) {
                continue;
            }
            Long abandoned = record.lastAbandoned().get(def.id());
            if (abandoned != null && abandoned == day) {
                continue;
            }
            offered.add(def);
        }
        return offered;
    }

    // -- promotion -----------------------------------------------------------------------------------

    /** Whether the holder has earned the next rank: its experience <em>and</em> every milestone it names. */
    public static boolean canPromote(CareerTrack track, TrackRecord record, CareerContext context) {
        int next = record.rank() + 1;
        if (next >= track.ranks().size()) {
            return false;
        }
        CareerTrack.Rank rank = track.ranks().get(next);
        if (record.xp() < rank.xp()) {
            return false;
        }
        for (CareerGoal goal : rank.requires()) {
            if (!goal.met(context)) {
                return false;
            }
        }
        return true;
    }
}
