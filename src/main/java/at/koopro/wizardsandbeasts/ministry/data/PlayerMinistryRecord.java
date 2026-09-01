package at.koopro.wizardsandbeasts.ministry.data;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.WantedLevel;
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
 */
@NullMarked
public record PlayerMinistryRecord(
        float notoriety,
        Map<MagicalOffence, Integer> offences,
        int sentenceTicks,
        boolean fugitive,
        MinistryRank rank,
        long outstandingFineKnuts) {

    public static final float MAX_NOTORIETY = 100.0f;

    public static final PlayerMinistryRecord DEFAULT =
            new PlayerMinistryRecord(0.0f, Map.of(), 0, false, MinistryRank.NONE, 0L);

    public PlayerMinistryRecord {
        notoriety = Math.max(0.0f, Math.min(MAX_NOTORIETY, notoriety));
        sentenceTicks = Math.max(0, sentenceTicks);
        offences = Map.copyOf(offences);
        outstandingFineKnuts = Math.max(0L, outstandingFineKnuts);
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
                    .forGetter(PlayerMinistryRecord::outstandingFineKnuts)
    ).apply(instance, PlayerMinistryRecord::new));

    // ── derived ──

    public WantedLevel wantedLevel() {
        return WantedLevel.forNotoriety(notoriety);
    }

    public boolean isServingSentence() {
        return sentenceTicks > 0;
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
        return new PlayerMinistryRecord(value, offences, sentenceTicks, fugitive, rank, outstandingFineKnuts);
    }

    /** Adds heat and files the offence. The two move together on every conviction. */
    public PlayerMinistryRecord withOffence(MagicalOffence offence, float notorietyGain) {
        Map<MagicalOffence, Integer> next = new EnumMap<>(MagicalOffence.class);
        next.putAll(offences);
        next.merge(offence, 1, Integer::sum);
        return new PlayerMinistryRecord(notoriety + notorietyGain, next, sentenceTicks, fugitive, rank,
                outstandingFineKnuts);
    }

    public PlayerMinistryRecord withSentenceTicks(int value) {
        return new PlayerMinistryRecord(notoriety, offences, value, fugitive, rank, outstandingFineKnuts);
    }

    public PlayerMinistryRecord withFugitive(boolean value) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, value, rank, outstandingFineKnuts);
    }

    public PlayerMinistryRecord withRank(MinistryRank value) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, value, outstandingFineKnuts);
    }

    /** Sets the outstanding debt outright. Negative values clamp to zero in the compact constructor. */
    public PlayerMinistryRecord withOutstandingFine(long knuts) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, rank, knuts);
    }

    /** Adds to the debt; {@code knuts} may be negative to settle part of it. */
    public PlayerMinistryRecord withFineAdjusted(long knuts) {
        return withOutstandingFine(outstandingFineKnuts + knuts);
    }

    /**
     * A pardon clears the heat, the fugitive status and the debt. The file stays — the Ministry does not
     * forget, but it does stop collecting: a pardon that left the bill outstanding would keep the player
     * uncoolable forever, which is the opposite of what a pardon is for.
     */
    public PlayerMinistryRecord pardoned() {
        return new PlayerMinistryRecord(0.0f, offences, 0, false, rank, 0L);
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
