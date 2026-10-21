package at.koopro.wizardsandbeasts.market;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * Every trader's pressure, for the whole world: one number per (location, side, good), and when it was last
 * touched. Server-wide on purpose — supply and demand are shared, so ten players farming one material saturate
 * one market, not ten ({@link MarketPricing}).
 *
 * <p>Decay is computed on read from the stored tick, so nothing ticks and an idle market costs nothing. Keys of
 * goods that leave the datapack linger harmlessly until {@link #reset} or until their pressure is read as zero.
 */
@NullMarked
public final class MarketBook extends SavedData {

    /** One good's pressure as of {@code tick}. */
    public record Entry(double pressure, long tick) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("p", 0.0).forGetter(Entry::pressure),
                Codec.LONG.optionalFieldOf("t", 0L).forGetter(Entry::tick)
        ).apply(i, Entry::new));
    }

    public static final SavedDataType<MarketBook> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_market_book",
            MarketBook::new,
            Codec.unboundedMap(Codec.STRING, Entry.CODEC).xmap(MarketBook::new, book -> book.entries));

    private final Map<String, Entry> entries;

    MarketBook() {
        this.entries = new HashMap<>();
    }

    private MarketBook(Map<String, Entry> stored) {
        this.entries = new HashMap<>(stored);
    }

    public static MarketBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The key of one good on one side of one location's counter. */
    public static String key(String location, MarketDefinition.Side side, String good) {
        return location + "|" + side.name().toLowerCase(java.util.Locale.ROOT) + "|" + good;
    }

    /** The pressure on {@code key} at {@code now}, decayed since it was last set. */
    public double pressure(String key, long now, double halfLifeDays) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return 0.0;
        }
        return MarketPricing.decay(entry.pressure(), now - entry.tick(), halfLifeDays);
    }

    /** Sets the pressure on {@code key} as of {@code now} (after a trade). */
    public void set(String key, double pressure, long now) {
        if (pressure <= 1.0e-6) {
            entries.remove(key);
        } else {
            entries.put(key, new Entry(pressure, now));
        }
        setDirty();
    }

    /** Clears every good of keys starting {@code prefix} (a location id, or "" for every market). Returns how many. */
    public int reset(String prefix) {
        int before = entries.size();
        entries.keySet().removeIf(k -> k.startsWith(prefix));
        if (entries.size() != before) {
            setDirty();
        }
        return before - entries.size();
    }

    /** A copy of the raw entries, for the admin readout. */
    public Map<String, Entry> snapshot() {
        return Map.copyOf(entries);
    }
}
