package at.koopro.wizardsandbeasts.ministry.data;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.WantedLevel;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.trace.WizardingAge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the Ministry has on you.
 *
 * <p>Two different things live here on purpose. {@code notoriety} is <b>current heat</b> — it decays while
 * you are neither wanted nor a fugitive, so lying low cools a search. {@code offences} is your <b>file</b>,
 * and never decays: priors make the next offence bite harder. One bad night is survivable; a career is not
 * quietly erased.
 *
 * <p>{@code outstandingFineKnuts} is the third thing, and it is a <b>debt</b> rather than either of the
 * other two: it is settled by paying, not by waiting, so it neither decays like heat nor accumulates like
 * the file. While it stands, heat cannot cool — see {@code FineSchedule}.
 *
 * @param notoriety            current heat, 0–100
 * @param offences             lifetime count per offence — the permanent file
 * @param sentenceTicks        Azkaban time left to serve; 0 = not serving
 * @param fugitive             walked out of a sentence and was never re-taken
 * @param rank                 Ministry position held, if any
 * @param outstandingFineKnuts unpaid fines owed to the Ministry, in Knuts
 * @param ageYears             age at {@code ageAnchorTick}, or {@link WizardingAge#UNSET} — no birth record, of age
 * @param ageAnchorTick        game tick {@code ageYears} was recorded at
 * @param wandConfiscatedUntil game tick the Ministry returns a confiscated wand; 0 when none is held
 * @param suspensions          licences a hearing suspended, each until a game tick
 * @param conduct              how the wizard has dealt with the Ministry: answering, cooperating and winning
 *                             appeals raise it, ignoring summons and running lower it; {@code ±MAX_CONDUCT}
 */
@NullMarked
public record PlayerMinistryRecord(
        float notoriety,
        Map<MagicalOffence, Integer> offences,
        int sentenceTicks,
        boolean fugitive,
        MinistryRank rank,
        long outstandingFineKnuts,
        int ageYears,
        long ageAnchorTick,
        long wandConfiscatedUntil,
        Map<LicenseType, Long> suspensions,
        int conduct) {

    public static final float MAX_NOTORIETY = 100.0f;
    /** How far conduct can go either way. */
    public static final int MAX_CONDUCT = 100;

    public static final PlayerMinistryRecord DEFAULT =
            new PlayerMinistryRecord(0.0f, Map.of(), 0, false, MinistryRank.NONE, 0L, -1, 0L, 0L, Map.of(), 0);

    public PlayerMinistryRecord {
        notoriety = Math.max(0.0f, Math.min(MAX_NOTORIETY, notoriety));
        sentenceTicks = Math.max(0, sentenceTicks);
        offences = Map.copyOf(offences);
        outstandingFineKnuts = Math.max(0L, outstandingFineKnuts);
        ageYears = Math.max(-1, ageYears);
        wandConfiscatedUntil = Math.max(0L, wandConfiscatedUntil);
        suspensions = Map.copyOf(suspensions);
        conduct = Math.max(-MAX_CONDUCT, Math.min(MAX_CONDUCT, conduct));
    }

    /** A record as it was before suspensions and conduct. */
    public PlayerMinistryRecord(float notoriety, Map<MagicalOffence, Integer> offences, int sentenceTicks,
                                boolean fugitive, MinistryRank rank, long outstandingFineKnuts, int ageYears,
                                long ageAnchorTick, long wandConfiscatedUntil) {
        this(notoriety, offences, sentenceTicks, fugitive, rank, outstandingFineKnuts, ageYears, ageAnchorTick,
                wandConfiscatedUntil, Map.of(), 0);
    }

    public static final Codec<PlayerMinistryRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("notoriety", 0.0f).forGetter(PlayerMinistryRecord::notoriety),
            Codec.unboundedMap(MagicalOffence.CODEC, Codec.INT).optionalFieldOf("offences", Map.of())
                    .forGetter(PlayerMinistryRecord::offences),
            Codec.INT.optionalFieldOf("sentenceTicks", 0).forGetter(PlayerMinistryRecord::sentenceTicks),
            Codec.BOOL.optionalFieldOf("fugitive", false).forGetter(PlayerMinistryRecord::fugitive),
            MinistryRank.CODEC.optionalFieldOf("rank", MinistryRank.NONE).forGetter(PlayerMinistryRecord::rank),
            // Optional so a save written before fines existed loads as owing nothing rather than failing
            // to parse — the whole record would otherwise reset to DEFAULT and wipe the criminal file.
            Codec.LONG.optionalFieldOf("outstandingFineKnuts", 0L)
                    .forGetter(PlayerMinistryRecord::outstandingFineKnuts),
            // Absent in every save written before ages existed, and absent means of age: nobody's childhood
            // is policed because the mod updated.
            Codec.INT.optionalFieldOf("ageYears", -1).forGetter(PlayerMinistryRecord::ageYears),
            Codec.LONG.optionalFieldOf("ageAnchorTick", 0L).forGetter(PlayerMinistryRecord::ageAnchorTick),
            Codec.LONG.optionalFieldOf("wandConfiscatedUntil", 0L)
                    .forGetter(PlayerMinistryRecord::wandConfiscatedUntil),
            Codec.unboundedMap(LicenseType.CODEC, Codec.LONG).optionalFieldOf("suspensions", Map.of())
                    .forGetter(PlayerMinistryRecord::suspensions),
            Codec.INT.optionalFieldOf("conduct", 0).forGetter(PlayerMinistryRecord::conduct)
    ).apply(instance, PlayerMinistryRecord::new));

    // ── derived ──

    public WantedLevel wantedLevel() {
        return WantedLevel.forNotoriety(notoriety);
    }

    public boolean isServingSentence() {
        return sentenceTicks > 0;
    }

    /** Years old at {@code now}, or {@link WizardingAge#UNSET}. */
    public int ageAt(long now, long ticksPerYear) {
        return WizardingAge.yearsAt(ageYears, ageAnchorTick, now, ticksPerYear);
    }

    /** True while the Ministry holds this wizard's wand. */
    public boolean wandConfiscatedAt(long now) {
        return now < wandConfiscatedUntil;
    }

    /** True while money is owed to the Ministry. */
    public boolean owesFine() {
        return outstandingFineKnuts > 0L;
    }

    public int offenceCount(MagicalOffence offence) {
        return offences.getOrDefault(offence, 0);
    }

    /** Lifetime total across every offence — the size of the file. */
    public int totalOffences() {
        int total = 0;
        for (int count : offences.values()) {
            total += count;
        }
        return total;
    }

    /**
     * Repeat offenders escalate. Each prior conviction of the <i>same</i> offence adds 15%, capped at
     * double, so a second Crucio stings and a tenth is not ten times worse.
     */
    public float repeatMultiplier(MagicalOffence offence) {
        return Math.min(2.0f, 1.0f + 0.15f * offenceCount(offence));
    }

    // ── withers ──

    public PlayerMinistryRecord withNotoriety(float value) {
        return new PlayerMinistryRecord(value, offences, sentenceTicks, fugitive, rank,
                outstandingFineKnuts, ageYears, ageAnchorTick, wandConfiscatedUntil, suspensions, conduct);
    }

    /** Adds heat and files the offence. The two move together on every conviction. */
    public PlayerMinistryRecord withOffence(MagicalOffence offence, float notorietyGain) {
        Map<MagicalOffence, Integer> next = new EnumMap<>(MagicalOffence.class);
        next.putAll(offences);
        next.merge(offence, 1, Integer::sum);
        return new PlayerMinistryRecord(notoriety + notorietyGain, next, sentenceTicks, fugitive, rank,
                outstandingFineKnuts, ageYears, ageAnchorTick, wandConfiscatedUntil, suspensions, conduct);
    }

    public PlayerMinistryRecord withSentenceTicks(int value) {
        return new PlayerMinistryRecord(notoriety, offences, value, fugitive, rank,
                outstandingFineKnuts, ageYears, ageAnchorTick, wandConfiscatedUntil, suspensions, conduct);
    }

    public PlayerMinistryRecord withFugitive(boolean value) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, value, rank,
                outstandingFineKnuts, ageYears, ageAnchorTick, wandConfiscatedUntil, suspensions, conduct);
    }

    public PlayerMinistryRecord withRank(MinistryRank value) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, value,
                outstandingFineKnuts, ageYears, ageAnchorTick, wandConfiscatedUntil, suspensions, conduct);
    }

    /** Sets the outstanding debt outright. Negative values clamp to zero in the compact constructor. */
    public PlayerMinistryRecord withOutstandingFine(long knuts) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, rank, knuts,
                ageYears, ageAnchorTick, wandConfiscatedUntil, suspensions, conduct);
    }

    /** Adds to the debt; {@code knuts} may be negative to settle part of it. */
    public PlayerMinistryRecord withFineAdjusted(long knuts) {
        return withOutstandingFine(outstandingFineKnuts + knuts);
    }

    /** Records an age as of {@code now}; {@link WizardingAge#UNSET} clears the birth record. */
    public PlayerMinistryRecord withAge(int years, long now) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, rank, outstandingFineKnuts,
                years, now, wandConfiscatedUntil, suspensions, conduct);
    }

    /** Holds the wand until {@code tick}; 0 returns it. */
    public PlayerMinistryRecord withWandConfiscatedUntil(long tick) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, rank, outstandingFineKnuts,
                ageYears, ageAnchorTick, tick, suspensions, conduct);
    }

    /**
     * A pardon clears the heat, the fugitive status and the debt. The file stays — the Ministry does not
     * forget, but it does stop collecting: a pardon that left the bill outstanding would keep the player
     * uncoolable forever, which is the opposite of what a pardon is for.
     */
    public PlayerMinistryRecord pardoned() {
        return new PlayerMinistryRecord(0.0f, offences, 0, false, rank, 0L, ageYears, ageAnchorTick,
                wandConfiscatedUntil, suspensions, conduct);
    }

    /** Whether a hearing has this licence suspended at {@code now}. */
    public boolean suspendedAt(LicenseType type, long now) {
        Long until = suspensions.get(type);
        return until != null && now < until;
    }

    /** Suspends {@code type} until {@code until}, never shortening a longer suspension already running. */
    public PlayerMinistryRecord withSuspension(LicenseType type, long until) {
        Map<LicenseType, Long> next = new EnumMap<>(LicenseType.class);
        next.putAll(suspensions);
        next.merge(type, until, Math::max);
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, rank, outstandingFineKnuts,
                ageYears, ageAnchorTick, wandConfiscatedUntil, next, conduct);
    }

    /** Lifts a suspension — an appeal won, or an operator's decision. */
    public PlayerMinistryRecord withoutSuspension(LicenseType type) {
        Map<LicenseType, Long> next = new EnumMap<>(LicenseType.class);
        next.putAll(suspensions);
        next.remove(type);
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, rank, outstandingFineKnuts,
                ageYears, ageAnchorTick, wandConfiscatedUntil, next, conduct);
    }

    /** Drops every suspension that has run out by {@code now}, so the file does not grow forever. */
    public PlayerMinistryRecord withoutLapsedSuspensions(long now) {
        if (suspensions.values().stream().allMatch(until -> now < until)) {
            return this;
        }
        Map<LicenseType, Long> next = new EnumMap<>(LicenseType.class);
        suspensions.forEach((type, until) -> {
            if (now < until) {
                next.put(type, until);
            }
        });
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, rank, outstandingFineKnuts,
                ageYears, ageAnchorTick, wandConfiscatedUntil, next, conduct);
    }

    /** Moves conduct by {@code delta}, clamped. */
    public PlayerMinistryRecord withConductAdjusted(int delta) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, rank, outstandingFineKnuts,
                ageYears, ageAnchorTick, wandConfiscatedUntil, suspensions, conduct + delta);
    }

    /**
     * Takes one conviction back off the file — an appeal overturned it. The count drops by one and the heat it
     * added comes off again; nothing else on the file moves.
     */
    public PlayerMinistryRecord withOffenceWithdrawn(MagicalOffence offence, float notorietyAdded) {
        Map<MagicalOffence, Integer> next = new EnumMap<>(MagicalOffence.class);
        next.putAll(offences);
        Integer count = next.get(offence);
        if (count == null) {
            return this;
        }
        if (count <= 1) {
            next.remove(offence);
        } else {
            next.put(offence, count - 1);
        }
        return new PlayerMinistryRecord(notoriety - Math.max(0.0f, notorietyAdded), next, sentenceTicks, fugitive,
                rank, outstandingFineKnuts, ageYears, ageAnchorTick, wandConfiscatedUntil, suspensions, conduct);
    }

    /** Insertion-ordered view for display, heaviest offence first. */
    public Map<MagicalOffence, Integer> offencesByWeight() {
        Map<MagicalOffence, Integer> sorted = new LinkedHashMap<>();
        offences.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getKey().notoriety(), a.getKey().notoriety()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }
}
