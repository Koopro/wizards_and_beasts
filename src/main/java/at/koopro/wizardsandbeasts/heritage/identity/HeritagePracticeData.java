package at.koopro.wizardsandbeasts.heritage.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * One player's practice of their people's own arts ({@link HeritagePractice}).
 *
 * @param counts       lifetime uses per {@link PracticeKind} id, for the character sheet
 * @param progress     progress towards the next profession point
 * @param day          the in-game day {@code today} counts
 * @param today        progress counted on {@code day}
 * @param pointsEarned profession points practice has paid, lifetime
 */
@NullMarked
public record HeritagePracticeData(Map<String, Integer> counts, int progress, long day, int today, int pointsEarned) {

    public static final HeritagePracticeData EMPTY = new HeritagePracticeData(Map.of(), 0, 0L, 0, 0);

    public static final Codec<HeritagePracticeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counts", Map.of())
                    .forGetter(HeritagePracticeData::counts),
            Codec.INT.optionalFieldOf("progress", 0).forGetter(HeritagePracticeData::progress),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(HeritagePracticeData::day),
            Codec.INT.optionalFieldOf("today", 0).forGetter(HeritagePracticeData::today),
            Codec.INT.optionalFieldOf("points_earned", 0).forGetter(HeritagePracticeData::pointsEarned)
    ).apply(i, HeritagePracticeData::new));

    public HeritagePracticeData {
        counts = Map.copyOf(counts);
    }

    public int count(PracticeKind kind) {
        return counts.getOrDefault(kind.id(), 0);
    }

    HeritagePracticeData withCount(PracticeKind kind) {
        Map<String, Integer> next = new HashMap<>(counts);
        next.merge(kind.id(), 1, Integer::sum);
        return new HeritagePracticeData(next, progress, day, today, pointsEarned);
    }
}
