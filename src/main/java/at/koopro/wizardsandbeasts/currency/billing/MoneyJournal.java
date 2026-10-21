package at.koopro.wizardsandbeasts.currency.billing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's most recent money movements, newest last — what an administrator reads when a balance looks wrong.
 *
 * <p>The {@link GringottsLedger} answers "is the world inflating" with totals and no names; this answers "where did
 * this player's money come from" with names and no totals. Bounded at {@link #CAPACITY} entries, so it can never
 * grow with play. Written only by {@link GringottsBilling}, so it sees exactly what the ledger sees.
 *
 * @param entries oldest first
 */
@NullMarked
public record MoneyJournal(List<Entry> entries) {

    /** Entries kept per player. */
    public static final int CAPACITY = 32;

    public static final MoneyJournal EMPTY = new MoneyJournal(List.of());

    /**
     * One movement.
     *
     * @param flow     the {@link MoneyFlow} id (stored as text so a retired flow still loads)
     * @param knuts    how much moved; negative for a refund that took a charge back
     * @param gameTime when, in world ticks
     */
    public record Entry(String flow, long knuts, long gameTime) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("flow").forGetter(Entry::flow),
                Codec.LONG.fieldOf("knuts").forGetter(Entry::knuts),
                Codec.LONG.optionalFieldOf("t", 0L).forGetter(Entry::gameTime)
        ).apply(i, Entry::new));
    }

    public static final Codec<MoneyJournal> CODEC = Entry.CODEC.listOf()
            .xmap(MoneyJournal::new, MoneyJournal::entries);

    public MoneyJournal {
        entries = entries.size() > CAPACITY
                ? List.copyOf(entries.subList(entries.size() - CAPACITY, entries.size()))
                : List.copyOf(entries);
    }

    /** This journal with {@code entry} appended, dropping the oldest past {@link #CAPACITY}. */
    public MoneyJournal with(Entry entry) {
        List<Entry> next = new ArrayList<>(entries.size() + 1);
        next.addAll(entries);
        next.add(entry);
        return new MoneyJournal(next);
    }
}
