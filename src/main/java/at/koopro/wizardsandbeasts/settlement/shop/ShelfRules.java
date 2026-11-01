package at.koopro.wizardsandbeasts.settlement.shop;

import at.koopro.wizardsandbeasts.settlement.SettlementData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * What a settlement's shop has on its shelves, as a rule. Pure over a site's seed and its stored shelf, so stock can be
 * tested without a world.
 *
 * <p>Prices are global ({@code MarketBook}); shelves are a site's own. The seed makes two hamlets carry different
 * goods in different amounts, and the same hamlet the same goods forever. Restocking is computed on read from the
 * stored tick, as market pressure decays: nothing ticks, and unloading a chunk or reconnecting changes nothing, because
 * the only clock is game time.
 */
@NullMarked
public final class ShelfRules {

    private static final long DAY = 24000;
    /** A site's full shelf is this share of the role's stock, plus up to the same again. */
    private static final double LEAST_SHARE = 0.75;
    private static final double SHARE_SPREAD = 0.5;

    private ShelfRules() {}

    private static Random roll(long seed, String location, String good) {
        return new Random(seed ^ ((long) (location + "|" + good).hashCode() << 21) ^ good.hashCode());
    }

    /** Whether this site's shop stocks {@code good} at all. */
    public static boolean carries(long seed, String location, String good, float chance) {
        return chance >= 1.0f || roll(seed, location, good).nextFloat() < chance;
    }

    /** How many a full shelf of {@code good} holds at this site. */
    public static int capacity(long seed, String location, String good, int stock) {
        Random random = roll(seed, location, good);
        random.nextFloat();
        return Math.max(1, (int) Math.round(stock * (LEAST_SHARE + SHARE_SPREAD * random.nextDouble())));
    }

    /** Ticks for one unit to come back, so that an empty shelf fills in {@code restockDays}. */
    static long ticksPerUnit(int capacity, int restockDays) {
        return Math.max(1L, restockDays * DAY / capacity);
    }

    /** Units on the shelf at {@code now}. No record means it has never been bought from: full. */
    public static int left(SettlementData.@Nullable Shelf shelf, int capacity, int restockDays, long now) {
        if (shelf == null) {
            return capacity;
        }
        long back = Math.max(0L, now - shelf.at()) / ticksPerUnit(capacity, restockDays);
        return (int) Math.min(capacity, shelf.left() + back);
    }

    /** The shelf after {@code taken} units left it. A unit part-way back keeps its progress. */
    public static SettlementData.Shelf after(SettlementData.@Nullable Shelf shelf, int capacity, int restockDays, long now,
                                             int taken) {
        int before = left(shelf, capacity, restockDays, now);
        long step = ticksPerUnit(capacity, restockDays);
        long at = now;
        if (shelf != null && before < capacity) {
            at = shelf.at() + (before - shelf.left()) * step;
        }
        return new SettlementData.Shelf(Math.max(0, before - taken), at);
    }
}
