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
 * @param notoriety       current heat, 0–100
 * @param offences        lifetime count per offence — the permanent file
 * @param sentenceTicks   Azkaban time left to serve; 0 = not serving
 * @param fugitive        walked out of a sentence and was never re-taken
 * @param rank            Ministry position held, if any
 */
@NullMarked
public record PlayerMinistryRecord(
        float notoriety,
        Map<MagicalOffence, Integer> offences,
        int sentenceTicks,
        boolean fugitive,
        MinistryRank rank) {

    public static final float MAX_NOTORIETY = 100.0f;

    public static final PlayerMinistryRecord DEFAULT =
            new PlayerMinistryRecord(0.0f, Map.of(), 0, false, MinistryRank.NONE);

    public PlayerMinistryRecord {
        notoriety = Math.max(0.0f, Math.min(MAX_NOTORIETY, notoriety));
        sentenceTicks = Math.max(0, sentenceTicks);
        offences = Map.copyOf(offences);
    }

    public static final Codec<PlayerMinistryRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("notoriety", 0.0f).forGetter(PlayerMinistryRecord::notoriety),
            Codec.unboundedMap(MagicalOffence.CODEC, Codec.INT).optionalFieldOf("offences", Map.of())
                    .forGetter(PlayerMinistryRecord::offences),
            Codec.INT.optionalFieldOf("sentenceTicks", 0).forGetter(PlayerMinistryRecord::sentenceTicks),
            Codec.BOOL.optionalFieldOf("fugitive", false).forGetter(PlayerMinistryRecord::fugitive),
            MinistryRank.CODEC.optionalFieldOf("rank", MinistryRank.NONE).forGetter(PlayerMinistryRecord::rank)
    ).apply(instance, PlayerMinistryRecord::new));

    // ── derived ──

    public WantedLevel wantedLevel() {
        return WantedLevel.forNotoriety(notoriety);
    }

    public boolean isServingSentence() {
        return sentenceTicks > 0;
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
        return new PlayerMinistryRecord(value, offences, sentenceTicks, fugitive, rank);
    }

    /** Adds heat and files the offence. The two move together on every conviction. */
    public PlayerMinistryRecord withOffence(MagicalOffence offence, float notorietyGain) {
        Map<MagicalOffence, Integer> next = new EnumMap<>(MagicalOffence.class);
        next.putAll(offences);
        next.merge(offence, 1, Integer::sum);
        return new PlayerMinistryRecord(notoriety + notorietyGain, next, sentenceTicks, fugitive, rank);
    }

    public PlayerMinistryRecord withSentenceTicks(int value) {
        return new PlayerMinistryRecord(notoriety, offences, value, fugitive, rank);
    }

    public PlayerMinistryRecord withFugitive(boolean value) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, value, rank);
    }

    public PlayerMinistryRecord withRank(MinistryRank value) {
        return new PlayerMinistryRecord(notoriety, offences, sentenceTicks, fugitive, value);
    }

    /** A pardon clears the heat and the fugitive status. The file stays — the Ministry does not forget. */
    public PlayerMinistryRecord pardoned() {
        return new PlayerMinistryRecord(0.0f, offences, 0, false, rank);
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
