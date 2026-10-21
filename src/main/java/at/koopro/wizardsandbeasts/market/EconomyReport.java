package at.koopro.wizardsandbeasts.market;

import at.koopro.wizardsandbeasts.currency.billing.GringottsLedger;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.Locations;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The economy as an administrator reads it: every money flow's totals, and every open trader's book at this moment
 * (price of the next unit, how saturated, what is in demand, the most the good can pay per day). Read-only; one
 * builder for the command ({@code /wandb admin economy market}) and the Control Center's Markets tab, so the two can
 * never disagree.
 */
@NullMarked
public final class EconomyReport {

    /**
     * @param flow  the flow's id
     * @param kind  its kind's id
     * @param count times it happened
     * @param knuts Knuts it moved
     */
    public record FlowLine(String flow, String kind, long count, long knuts) {}

    /**
     * @param location      the location id
     * @param buying        true: the trader buys it from players
     * @param good          the good's id
     * @param unitKnuts     next unit's price now (base value for treasure and wands: 0)
     * @param saturation    0–100
     * @param inDemand      the trader's short good today
     * @param ceilingPerDay most this good can pay the server per day at any volume (goods bought only)
     * @param deal          what the law makes of it
     */
    public record MarketRow(String location, boolean buying, String good, long unitKnuts, int saturation,
                            boolean inDemand, long ceilingPerDay, DealClass deal) {}

    /** @param net sources less sinks, in Knuts */
    public record Snapshot(List<FlowLine> flows, List<MarketRow> markets, long net) {}

    private EconomyReport() {}

    public static Snapshot build(MinecraftServer server) {
        GringottsLedger ledger = GringottsLedger.get(server);
        List<FlowLine> flows = new ArrayList<>();
        for (MoneyFlow flow : MoneyFlow.values()) {
            GringottsLedger.Totals totals = ledger.totals(flow);
            flows.add(new FlowLine(flow.id(), flow.kind().id(), totals.count(), totals.knuts()));
        }
        long net = ledger.knutsOf(MoneyFlow.Kind.SOURCE) - ledger.knutsOf(MoneyFlow.Kind.SINK);

        MarketBook book = MarketBook.get(server);
        long now = server.overworld().getGameTime();
        long day = Math.floorDiv(now, MarketPricing.TICKS_PER_DAY);
        double scale = MarketTrade.scale();
        List<MarketRow> markets = new ArrayList<>();
        for (Map.Entry<Identifier, LocationDefinition> entry : Locations.all().entrySet()) {
            Identifier location = entry.getKey();
            LocationDefinition def = Locations.open(location);
            if (def == null || def.market().isEmpty()) {
                continue;
            }
            MarketDefinition market = def.market().get();
            double halfLife = MarketTrade.halfLife(market);
            boolean outside = def.risk().isPresent();
            for (MarketDefinition.Side side : MarketDefinition.Side.values()) {
                boolean buying = side == MarketDefinition.Side.BUYS;
                for (MarketGood good : market.side(side)) {
                    double pressure = book.pressure(MarketBook.key(location.toString(), side, good.id()), now, halfLife);
                    boolean demand = buying && MarketTrade.inDemand(location, market, good, day);
                    double multiplier = scale * (demand ? 1.0 + market.demandBonus() : 1.0);
                    long unit = good.kind() != MarketGood.Kind.ITEM ? 0L : buying
                            ? (long) Math.floor(MarketPricing.sellUnit(good.priceKnuts(), good.depth(), pressure) * multiplier)
                            : (long) Math.ceil(MarketPricing.buyUnit(good.priceKnuts(), good.depth(), pressure) * scale);
                    long ceiling = buying && good.kind() == MarketGood.Kind.ITEM
                            ? Math.round(MarketPricing.dailyIncomeCeiling(good.priceKnuts() * scale, good.depth(), halfLife))
                            : 0L;
                    markets.add(new MarketRow(location.toString(), buying, good.id(), unit,
                            MarketTrade.saturation(pressure, good.depth()), demand, ceiling,
                            MarketTrade.lineClass(good, outside)));
                }
            }
        }
        return new Snapshot(flows, markets, net);
    }
}
