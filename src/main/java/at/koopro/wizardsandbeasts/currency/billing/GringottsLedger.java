package at.koopro.wizardsandbeasts.currency.billing;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Gringotts' book of the world's money: how many times each {@link MoneyFlow} happened and how many Knuts it moved.
 *
 * <p>Totals only — no per-player rows, no history to grow without bound. It answers one question an administrator
 * cannot otherwise answer: is money being made faster than it is spent ({@code /wandb admin economy ledger})?
 * Stored by flow id, so a flow removed in a later version is dropped on load instead of failing it.
 */
@NullMarked
public final class GringottsLedger extends SavedData {

    /** One flow's running totals. */
    public record Totals(long count, long knuts) {

        public static final Totals NONE = new Totals(0L, 0L);

        static final Codec<Totals> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("count", 0L).forGetter(Totals::count),
                Codec.LONG.optionalFieldOf("knuts", 0L).forGetter(Totals::knuts)
        ).apply(i, Totals::new));

        Totals plus(long times, long amount) {
            return new Totals(Math.max(0L, count + times), Math.max(0L, saturatedAdd(knuts, amount)));
        }
    }

    public static final SavedDataType<GringottsLedger> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_gringotts_ledger",
            GringottsLedger::new,
            Codec.unboundedMap(Codec.STRING, Totals.CODEC).xmap(GringottsLedger::new, GringottsLedger::byId));

    private final Map<MoneyFlow, Totals> totals = new EnumMap<>(MoneyFlow.class);

    GringottsLedger() {
    }

    private GringottsLedger(Map<String, Totals> stored) {
        stored.forEach((id, value) -> {
            MoneyFlow flow = MoneyFlow.byId(id);
            if (flow != null) {
                totals.put(flow, value);
            }
        });
    }

    public static GringottsLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** One more {@code flow} of {@code knuts}. */
    public void record(MoneyFlow flow, long knuts) {
        add(flow, 1L, knuts);
    }

    /** Takes back a recorded {@code flow} whose service then failed and was refunded. */
    public void reverse(MoneyFlow flow, long knuts) {
        add(flow, -1L, -knuts);
    }

    private void add(MoneyFlow flow, long times, long knuts) {
        totals.put(flow, totals(flow).plus(times, knuts));
        setDirty();
    }

    public Totals totals(MoneyFlow flow) {
        return totals.getOrDefault(flow, Totals.NONE);
    }

    /** Knuts moved by every flow of {@code kind}. */
    public long knutsOf(MoneyFlow.Kind kind) {
        long sum = 0L;
        for (MoneyFlow flow : MoneyFlow.values()) {
            if (flow.kind() == kind) {
                sum = saturatedAdd(sum, totals(flow).knuts());
            }
        }
        return sum;
    }

    private Map<String, Totals> byId() {
        Map<String, Totals> out = new HashMap<>();
        totals.forEach((flow, value) -> out.put(flow.id(), value));
        return out;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        // Overflow only when both operands share a sign the result does not.
        if (((a ^ sum) & (b ^ sum)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return sum;
    }
}
