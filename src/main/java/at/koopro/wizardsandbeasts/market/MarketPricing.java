package at.koopro.wizardsandbeasts.market;

import org.jspecify.annotations.NullMarked;

/**
 * The arithmetic of a trader's counter, with no game attached (no {@code Config}, no level).
 *
 * <h2>Pressure</h2>
 * Every good a trader deals in carries a <em>pressure</em>: the units recently traded across the counter, by
 * everyone on the server. It rises by one per unit and falls back towards zero with a half-life
 * ({@link #decay}). A trader that has just been sold forty unicorn hairs offers less for the forty-first; one that
 * has just sold its last three Bezoars asks more for the fourth. Nobody owns the pressure — two players selling the
 * same hair to the same apothecary share it, which is what makes it supply and demand rather than a per-player cap.
 *
 * <h2>Prices</h2>
 * <pre>
 *   a trader buys  (player sells):  unit = base · depth / (depth + pressure)
 *   a trader sells (player buys):   unit = base · (depth + pressure) / depth
 * </pre>
 * {@code depth} is how many units it takes to halve (or double) the price: a big, liquid market for dittany, a
 * shallow one for phoenix feathers.
 *
 * <h2>Why this cannot inflate</h2>
 * With exponential recovery at rate {@code k = ln2 / halfLife}, selling a good as fast as anyone likes holds its
 * pressure at {@code r/k} for a rate {@code r} units a day, and earns {@code r · base · depth / (depth + r/k)} a day.
 * That rises with {@code r} but never past {@code base · depth · k} ({@link #dailyIncomeCeiling}): the most a good
 * can ever pay a whole server per day, however hard it is farmed, is fixed by its data. A farm does not print
 * money; it saturates a market.
 */
@NullMarked
public final class MarketPricing {

    /** Ticks in an in-game day. */
    public static final long TICKS_PER_DAY = 24_000L;

    /** Under this many Knuts a unit is not worth the trader's time: the counter refuses it. */
    public static final long MIN_UNIT_KNUTS = 1L;

    /** A cursed artefact is worth this share of a clean one to a buyer who will have to deal with the curse. */
    public static final double CURSED_SHARE = 0.5;

    private MarketPricing() {}

    /** Pressure {@code elapsedTicks} after it stood at {@code pressure}, halving every {@code halfLifeDays}. */
    public static double decay(double pressure, long elapsedTicks, double halfLifeDays) {
        if (pressure <= 0.0 || elapsedTicks <= 0) {
            return Math.max(0.0, pressure);
        }
        if (halfLifeDays <= 0.0) {
            return 0.0;
        }
        double halfLives = elapsedTicks / (halfLifeDays * TICKS_PER_DAY);
        return pressure * Math.pow(0.5, halfLives);
    }

    /** What a trader pays for one unit at {@code pressure}: before rounding, so batches round once per unit. */
    public static double sellUnit(double base, int depth, double pressure) {
        return base * depth / (depth + Math.max(0.0, pressure));
    }

    /** What a trader asks for one unit at {@code pressure}. */
    public static double buyUnit(double base, int depth, double pressure) {
        return base * (depth + Math.max(0.0, pressure)) / depth;
    }

    /**
     * A sale of up to {@code units} to a trader starting at {@code pressure}: each unit is paid (floored to a Knut)
     * at the pressure the units before it left. Stops at the first unit the trader would pay less than
     * {@link #MIN_UNIT_KNUTS} for.
     *
     * @param base       the good's price per unit before pressure (Knuts, already scaled)
     * @param multiplier today's demand and the server's price scale
     */
    public static Batch sell(double base, int depth, double pressure, int units, double multiplier) {
        long total = 0L;
        int taken = 0;
        double p = Math.max(0.0, pressure);
        for (int i = 0; i < units; i++) {
            long unit = (long) Math.floor(sellUnit(base, depth, p) * multiplier);
            if (unit < MIN_UNIT_KNUTS) {
                break;
            }
            total = Math.addExact(total, unit);
            taken++;
            p += 1.0;
        }
        return new Batch(taken, total, p);
    }

    /** A purchase of {@code units} from a trader starting at {@code pressure}; each unit priced up (ceiling). */
    public static Batch buy(double base, int depth, double pressure, int units, double multiplier) {
        long total = 0L;
        double p = Math.max(0.0, pressure);
        for (int i = 0; i < units; i++) {
            total = Math.addExact(total, Math.max(MIN_UNIT_KNUTS, (long) Math.ceil(buyUnit(base, depth, p) * multiplier)));
            p += 1.0;
        }
        return new Batch(units, total, p);
    }

    /**
     * The most selling this good can pay per day, server-wide, at any volume: {@code base · depth · ln2 / halfLife}.
     * The supremum of {@code r · base · depth / (depth + r · halfLife / ln2)} over {@code r}.
     */
    public static double dailyIncomeCeiling(double base, int depth, double halfLifeDays) {
        return base * depth * Math.log(2.0) / halfLifeDays;
    }

    /**
     * Steady-state daily income when {@code unitsPerDay} are sold every day, for the documentation tables and the
     * tests that pin them.
     */
    public static double steadyDailyIncome(double base, int depth, double halfLifeDays, double unitsPerDay) {
        double steadyPressure = unitsPerDay * halfLifeDays / Math.log(2.0);
        return unitsPerDay * base * depth / (depth + steadyPressure);
    }

    /**
     * The good the trader is short of today: one index of {@code size}, the same for every customer and every visit
     * that day, different from day to day. -1 when the trader buys nothing.
     */
    public static int inDemandToday(int size, long day, long marketSeed) {
        if (size <= 0) {
            return -1;
        }
        long h = day * 0x9E3779B97F4A7C15L ^ marketSeed;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (int) Math.floorMod(h, (long) size);
    }

    /**
     * The result of pricing a batch.
     *
     * @param units         units the trader will take or give (a sale can stop early)
     * @param knuts         total Knuts
     * @param pressureAfter the pressure once the batch is done
     */
    public record Batch(int units, long knuts, double pressureAfter) {}
}
