package at.koopro.wizardsandbeasts.location.ollivanders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * A wand Ollivanders is making for one player ({@link WandCommission}): the wood and core handed over, the day it
 * can be collected, and what was paid. At most one at a time; empty when nothing is on the bench.
 *
 * @param order the commission in hand, if any
 */
@NullMarked
public record WandCommissionOrder(Optional<Order> order) {

    public static final WandCommissionOrder NONE = new WandCommissionOrder(Optional.empty());

    /**
     * @param wood      the wand wood ({@code wand_woods/<id>})
     * @param core      the wand core ({@code wand_cores/<id>})
     * @param readyDay  the in-game day from which it can be collected
     * @param paidKnuts what the commission cost, for the journal and the admin readout
     */
    public record Order(Identifier wood, Identifier core, long readyDay, long paidKnuts) {
        static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("wood").forGetter(Order::wood),
                Identifier.CODEC.fieldOf("core").forGetter(Order::core),
                Codec.LONG.fieldOf("ready_day").forGetter(Order::readyDay),
                Codec.LONG.optionalFieldOf("paid", 0L).forGetter(Order::paidKnuts)
        ).apply(i, Order::new));

        public boolean ready(long today) {
            return today >= readyDay;
        }

        public long daysLeft(long today) {
            return Math.max(0L, readyDay - today);
        }
    }

    public static final Codec<WandCommissionOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
            Order.CODEC.optionalFieldOf("order").forGetter(WandCommissionOrder::order)
    ).apply(i, WandCommissionOrder::new));

    public static WandCommissionOrder of(Order order) {
        return new WandCommissionOrder(Optional.of(order));
    }
}
