package at.koopro.wizardsandbeasts.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * One player's trade at every market: how much of each good they bought this in-game week (for weekly limits) and
 * their lifetime totals (for the admin readout). Keyed like {@link MarketBook}. Written only by {@link MarketTrade}.
 *
 * @param lines per (location, side, good)
 */
@NullMarked
public record PlayerMarketRecord(Map<String, Line> lines) {

    public static final PlayerMarketRecord EMPTY = new PlayerMarketRecord(Map.of());

    /**
     * @param week       the in-game week {@code boughtWeek} counts
     * @param boughtWeek units bought in {@code week}
     * @param units      lifetime units traded
     * @param knuts      lifetime Knuts paid or earned
     */
    public record Line(long week, int boughtWeek, long units, long knuts) {
        public static final Line NONE = new Line(0L, 0, 0L, 0L);

        static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("week", 0L).forGetter(Line::week),
                Codec.INT.optionalFieldOf("bought_week", 0).forGetter(Line::boughtWeek),
                Codec.LONG.optionalFieldOf("units", 0L).forGetter(Line::units),
                Codec.LONG.optionalFieldOf("knuts", 0L).forGetter(Line::knuts)
        ).apply(i, Line::new));

        /** Units bought in {@code currentWeek}; a line from an older week counts nothing. */
        public int boughtIn(long currentWeek) {
            return week == currentWeek ? boughtWeek : 0;
        }
    }

    public static final Codec<PlayerMarketRecord> CODEC =
            Codec.unboundedMap(Codec.STRING, Line.CODEC).xmap(PlayerMarketRecord::new, PlayerMarketRecord::lines);

    public PlayerMarketRecord {
        lines = Map.copyOf(lines);
    }

    public Line line(String key) {
        return lines.getOrDefault(key, Line.NONE);
    }

    /** This record after a trade of {@code units} for {@code knuts}; {@code bought} counts toward the week. */
    public PlayerMarketRecord traded(String key, int units, long knuts, boolean bought, long currentWeek) {
        Line old = line(key);
        int week = (bought ? old.boughtIn(currentWeek) + units : old.boughtIn(currentWeek));
        Line next = new Line(currentWeek, week, old.units() + units, old.knuts() + knuts);
        Map<String, Line> copy = new HashMap<>(lines);
        copy.put(key, next);
        return new PlayerMarketRecord(copy);
    }
}
