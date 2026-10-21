package at.koopro.wizardsandbeasts.market;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.dragot.DragotAcceptance;
import at.koopro.wizardsandbeasts.currency.dragot.DragotExchange;
import at.koopro.wizardsandbeasts.currency.dragot.DragotPurse;
import at.koopro.wizardsandbeasts.currency.dragot.DragotRates;
import at.koopro.wizardsandbeasts.currency.dragot.DragotVendorEvents;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.LocationText;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicenceGate;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Every deal across a trader's counter, done on the server and nowhere else.
 *
 * <p>The client sends an intent — "sell all my unicorn hair here", "buy one Bezoar" — and this class re-derives
 * everything: that the location is open and the player is at it, the tier, what the player actually carries, what
 * the law makes of it, the price from the shared {@link MarketBook}, and the vault through {@link GringottsBilling}.
 * A deal is all or nothing:
 *
 * <ul>
 *   <li><b>selling</b>: priced first; refused before anything moves if the vault could not take the money; then the
 *       goods leave the inventory and the money arrives in the same tick. If the credit is somehow refused after
 *       all, the goods are put back — a coin is never paid for nothing and goods are never taken unpaid;</li>
 *   <li><b>buying</b>: the vault is charged all or nothing first; only a paid charge hands over goods.</li>
 * </ul>
 *
 * Two quick packets cannot sell the same stack twice: the first deal removes it before the second is read, on the
 * one server thread, and the board's packet is rate-limited besides.
 */
@NullMarked
public final class MarketTrade {

    /** The most units one deal moves: a full inventory of one stackable good, and then some. */
    public static final int MAX_UNITS = 2304;
    /** The most units one purchase hands over: a stack. The board offers 1 or 8; more is a crafted packet. */
    public static final int MAX_BUY = 64;

    /** Outcome of a deal, for callers and tests. */
    public enum Result {
        DONE, UNKNOWN, CLOSED, NOT_THERE, TIER_TOO_LOW, BAD_COUNT, NOTHING_TO_SELL, NOT_WANTED, REFUSED_GOODS,
        NO_PAPERS, CANNOT_AFFORD, VAULT_FULL, WEEKLY_LIMIT, NO_DRAGOTS, CAUGHT
    }

    /** How a purchase is paid: drawn on the vault, or in Dragots across the till where the keeper takes them. */
    public enum Tender { VAULT, DRAGOTS }

    private MarketTrade() {}

    // -- shared checks -----------------------------------------------------------------------------------

    private record Counter(Identifier location, LocationDefinition def, MarketDefinition market) {
        boolean outsideTheLaw() {
            return def.risk().isPresent();
        }
    }

    private static @Nullable Counter counter(Identifier location) {
        LocationDefinition def = Locations.open(location);
        if (def == null || def.market().isEmpty()) {
            return null;
        }
        return new Counter(location, def, def.market().get());
    }

    /** Whether trade is possible at all: it is money, and money is Gringotts'. */
    public static boolean open() {
        return ModuleManager.isEnabled(Module.GRINGOTTS);
    }

    private static @Nullable Result admit(ServerPlayer player, Counter counter, MarketGood good) {
        if (!open()) {
            PlayerFeedback.actionBar(player, Component.translatable("market.wizards_and_beasts.closed")
                    .withStyle(ChatFormatting.RED));
            return Result.CLOSED;
        }
        if (!LocationPresence.isAt(player, counter.def())) {
            PlayerFeedback.actionBar(player, Component.translatable("location.wizards_and_beasts.not_there",
                    LocationText.name(counter.location())).withStyle(ChatFormatting.RED));
            return Result.NOT_THERE;
        }
        if (!good.minTier().isEmpty() && LocationProgression.tier(player, counter.location())
                < counter.def().standing().rankIndex(good.minTier())) {
            PlayerFeedback.refuse(player, Component.translatable("market.wizards_and_beasts.refused"),
                    Component.translatable("location.wizards_and_beasts.tier_required",
                            LocationText.tier(counter.location(), good.minTier())));
            return Result.TIER_TOO_LOW;
        }
        return null;
    }

    /** A lawful counter's rule for a class of goods: contraband and stolen goods never, restricted with papers. */
    private static @Nullable Result lawful(ServerPlayer player, Counter counter, DealClass deal) {
        if (counter.outsideTheLaw()) {
            return null;
        }
        if (!deal.lawful()) {
            PlayerFeedback.refuse(player, Component.translatable("market.wizards_and_beasts.refused"),
                    Component.translatable("market.wizards_and_beasts.refused.unlawful"));
            return Result.REFUSED_GOODS;
        }
        if (deal == DealClass.RESTRICTED && !MinistryLicenceGate.admit(player, LicenseType.RESTRICTED_SUBSTANCES)) {
            return Result.NO_PAPERS;
        }
        return null;
    }

    static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }

    static long day(ServerPlayer player) {
        return Math.floorDiv(now(player), MarketPricing.TICKS_PER_DAY);
    }

    static long week(ServerPlayer player) {
        return Math.floorDiv(day(player), 7L);
    }

    /** The configured half-life for a market, after the server's recovery scale. */
    static double halfLife(MarketDefinition market) {
        return market.halfLifeDays() * 100.0 / Math.max(1, Config.marketRecoveryScalePercent);
    }

    static double scale() {
        return Config.marketPriceScalePercent / 100.0;
    }

    /** Whether {@code good} is the one this trader is short of today (buys only). */
    static boolean inDemand(Identifier location, MarketDefinition market, MarketGood good, long day) {
        int index = MarketPricing.inDemandToday(market.buys().size(), day, location.toString().hashCode());
        return index >= 0 && market.buys().get(index).id().equals(good.id());
    }

    // -- selling -------------------------------------------------------------------------------------------

    /** One slot's worth of goods offered: which slot, how many units, and each unit's value before pressure. */
    private record Lot(int slot, int units, double unitValue, DealClass deal) {}

    /** Sells up to {@code count} of {@code goodId} to the trader at {@code location}. */
    public static Result sell(ServerPlayer player, Identifier location, String goodId, int count) {
        Counter counter = counter(location);
        MarketGood good = counter == null ? null : counter.market().good(MarketDefinition.Side.BUYS, goodId);
        if (counter == null || good == null) {
            return Result.UNKNOWN;
        }
        Result refused = admit(player, counter, good);
        if (refused != null) {
            return refused;
        }
        if (count <= 0) {
            return Result.BAD_COUNT;
        }
        int wanted = Math.min(count, MAX_UNITS);

        // What the player actually carries, read from the inventory now.
        Inventory inventory = player.getInventory();
        List<Lot> lots = new ArrayList<>();
        int found = 0;
        DealClass deal = DealClass.LEGAL;
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size() && found < wanted; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!MarketGoods.matches(good, stack, player.getUUID())) {
                continue;
            }
            int units = Math.min(stack.getCount(), wanted - found);
            DealClass stackDeal = MarketGoods.classify(good, stack, counter.outsideTheLaw());
            lots.add(new Lot(slot, units, MarketGoods.baseValue(good, stack, player.registryAccess()), stackDeal));
            deal = deal.worse(stackDeal);
            found += units;
        }
        if (found == 0) {
            PlayerFeedback.actionBar(player, Component.translatable("market.wizards_and_beasts.nothing_to_sell")
                    .withStyle(ChatFormatting.RED));
            return Result.NOTHING_TO_SELL;
        }
        refused = lawful(player, counter, deal);
        if (refused != null) {
            return refused;
        }

        // Price each unit at the pressure the units before it left.
        MarketBook book = MarketBook.get(player.level().getServer());
        String key = MarketBook.key(location.toString(), MarketDefinition.Side.BUYS, good.id());
        long now = now(player);
        double pressure = book.pressure(key, now, halfLife(counter.market()));
        double multiplier = scale() * (inDemand(location, counter.market(), good, day(player))
                ? 1.0 + counter.market().demandBonus() : 1.0);
        long total = 0L;
        List<int[]> taking = new ArrayList<>(); // {slot, units}
        int sold = 0;
        outer:
        for (Lot lot : lots) {
            int fromLot = 0;
            for (int u = 0; u < lot.units(); u++) {
                long unit = (long) Math.floor(MarketPricing.sellUnit(lot.unitValue(), good.depth(), pressure) * multiplier);
                if (unit < MarketPricing.MIN_UNIT_KNUTS) {
                    if (fromLot > 0) {
                        taking.add(new int[] {lot.slot(), fromLot});
                    }
                    break outer;
                }
                total = Math.addExact(total, unit);
                pressure += 1.0;
                fromLot++;
                sold++;
            }
            taking.add(new int[] {lot.slot(), fromLot});
        }
        if (sold == 0) {
            PlayerFeedback.actionBar(player, Component.translatable("market.wizards_and_beasts.not_wanted")
                    .withStyle(ChatFormatting.RED));
            return Result.NOT_WANTED;
        }
        if (!player.getData(ModAttachments.VAULT_DATA.get()).canCredit(total)) {
            PlayerFeedback.actionBar(player, Component.translatable("gringotts.wizards_and_beasts.reject.full")
                    .withStyle(ChatFormatting.RED));
            return Result.VAULT_FULL;
        }
        // A lawful counter buys only so much a week from a seller without a Magical Commerce licence.
        boolean underAllowance = !counter.outsideTheLaw()
                && at.koopro.wizardsandbeasts.ministry.licence.CommerceAllowance.applies(player);
        if (underAllowance) {
            long left = at.koopro.wizardsandbeasts.ministry.licence.CommerceAllowance.leftFor(player, now);
            if (total > left) {
                PlayerFeedback.actionBar(player, Component.translatable(
                        "market.wizards_and_beasts.commerce_allowance",
                        at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper.formatFromKnuts(left))
                        .withStyle(ChatFormatting.RED));
                return Result.WEEKLY_LIMIT;
            }
        }

        // Goods out, money in — the same tick, and the goods come back if the money cannot go in.
        List<ItemStack> removed = new ArrayList<>();
        List<Integer> removedSlots = new ArrayList<>();
        for (int[] take : taking) {
            if (take[1] <= 0) {
                continue;
            }
            ItemStack stack = inventory.getItem(take[0]);
            removed.add(stack.split(take[1]));
            removedSlots.add(take[0]);
        }
        MoneyFlow flow = counter.outsideTheLaw() ? MoneyFlow.ILLICIT_SALE : MoneyFlow.MARKET_SALE;
        if (!GringottsBilling.credit(player, flow, total)) {
            for (int i = 0; i < removed.size(); i++) {
                ItemStack back = removed.get(i);
                ItemStack there = inventory.getItem(removedSlots.get(i));
                if (there.isEmpty()) {
                    inventory.setItem(removedSlots.get(i), back);
                } else if (ItemStack.isSameItemSameComponents(there, back)) {
                    there.grow(back.getCount());
                } else {
                    at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, back);
                }
            }
            player.containerMenu.broadcastChanges();
            return Result.VAULT_FULL;
        }
        player.containerMenu.broadcastChanges();
        book.set(key, pressure, now);
        player.setData(ModAttachments.MARKET_RECORD.get(), player.getData(ModAttachments.MARKET_RECORD.get())
                .traded(key, sold, total, false, week(player)));
        if (underAllowance) {
            at.koopro.wizardsandbeasts.ministry.licence.CommerceAllowance.get(player.level().getServer())
                    .record(player.getUUID(), now, total);
        }

        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("market.wizards_and_beasts.sold", sold, goodName(good, player)),
                Component.translatable("market.wizards_and_beasts.paid", CurrencyHelper.formatFromKnuts(total)));
        CareerProgression.record(player, ActivityType.GOODS_TRADED, location);
        IllicitDealing.roll(player, counter.def(), deal, sold);
        LocationProgression.sync(player, location, false);
        return Result.DONE;
    }

    // -- buying --------------------------------------------------------------------------------------------

    /** Buys {@code count} of {@code goodId} from the trader at {@code location}, drawn on the vault. */
    public static Result buy(ServerPlayer player, Identifier location, String goodId, int count) {
        return buy(player, location, goodId, count, Tender.VAULT);
    }

    /**
     * Buys {@code count} of {@code goodId}, paid as {@code tender} says. Dragots are taken only by a keeper whose
     * type {@link DragotAcceptance} admits, at {@link DragotRates#dragotsToPay} (the bottom of the teller's spread,
     * so bought Dragots never beat the vault), worst coins first; a keeper who takes only Dragots refuses the vault.
     */
    public static Result buy(ServerPlayer player, Identifier location, String goodId, int count, Tender tender) {
        Counter counter = counter(location);
        MarketGood good = counter == null ? null : counter.market().good(MarketDefinition.Side.SELLS, goodId);
        if (counter == null || good == null || good.item().isEmpty()) {
            return Result.UNKNOWN;
        }
        Result refused = admit(player, counter, good);
        if (refused != null) {
            return refused;
        }
        if (count <= 0 || count > MAX_BUY) {
            return Result.BAD_COUNT;
        }
        String key = MarketBook.key(location.toString(), MarketDefinition.Side.SELLS, good.id());
        PlayerMarketRecord record = player.getData(ModAttachments.MARKET_RECORD.get());
        long week = week(player);
        if (good.weeklyLimit() > 0) {
            int left = good.weeklyLimit() - record.line(key).boughtIn(week);
            if (left <= 0) {
                PlayerFeedback.refuse(player, Component.translatable("market.wizards_and_beasts.refused"),
                        Component.translatable("market.wizards_and_beasts.weekly_limit", good.weeklyLimit()));
                return Result.WEEKLY_LIMIT;
            }
            count = Math.min(count, left);
        }
        if (!BuiltInRegistries.ITEM.containsKey(good.item().get())) {
            return Result.UNKNOWN; // a datapack naming no real item: never charge for nothing
        }
        Item item = BuiltInRegistries.ITEM.getValue(good.item().get());
        DealClass deal = MarketGoods.classify(good.item().get());
        refused = lawful(player, counter, deal);
        if (refused != null) {
            return refused;
        }

        MarketBook book = MarketBook.get(player.level().getServer());
        long now = now(player);
        double pressure = book.pressure(key, now, halfLife(counter.market()));
        MarketPricing.Batch batch = MarketPricing.buy(good.priceKnuts(), good.depth(), pressure, count, scale());
        DragotAcceptance policy = dragotPolicy(player, counter.def());
        Component paid;
        if (tender == Tender.DRAGOTS) {
            if (policy == DragotAcceptance.REFUSED) {
                PlayerFeedback.refuse(player, Component.translatable("market.wizards_and_beasts.refused"),
                        policy.refusalFor(true));
                return Result.NO_DRAGOTS;
            }
            int dragots = DragotRates.dragotsToPay(policy.adjust(batch.knuts()), Config.dragotGalleonRate);
            if (DragotPurse.total(player) < dragots) {
                PlayerFeedback.refuse(player, Component.translatable("location.wizards_and_beasts.cannot_afford"),
                        Component.translatable("market.wizards_and_beasts.dragots_short", dragots));
                return Result.CANNOT_AFFORD;
            }
            Entity host = LocationPresence.host(player, counter.def());
            if (DragotExchange.noticedByVendor(player)) {
                // A shaved coin in the purse: the sale is off and the keeper remembers. Nothing changes hands.
                if (host != null) {
                    DragotVendorEvents.refuseAndRemember(player, host);
                }
                return Result.CAUGHT;
            }
            if (DragotPurse.take(player, dragots) < 0) {
                return Result.CANNOT_AFFORD;
            }
            GringottsBilling.recordCirculation(player, MoneyFlow.DRAGOT_TENDER, batch.knuts());
            paid = Component.translatable("market.wizards_and_beasts.cost_dragots", dragots);
        } else {
            if (policy == DragotAcceptance.ONLY) {
                PlayerFeedback.refuse(player, Component.translatable("market.wizards_and_beasts.refused"),
                        policy.refusalFor(false));
                return Result.NO_DRAGOTS;
            }
            MoneyFlow flow = counter.outsideTheLaw() ? MoneyFlow.ILLICIT_PURCHASE : MoneyFlow.MARKET_PURCHASE;
            if (!GringottsBilling.charge(player, flow, batch.knuts())) {
                PlayerFeedback.refuse(player, Component.translatable("location.wizards_and_beasts.cannot_afford"),
                        Component.translatable("location.wizards_and_beasts.price",
                                CurrencyHelper.formatFromKnuts(batch.knuts())));
                return Result.CANNOT_AFFORD;
            }
            paid = Component.translatable("market.wizards_and_beasts.cost", CurrencyHelper.formatFromKnuts(batch.knuts()));
        }
        int left = count;
        int perStack = Math.max(1, item.getDefaultMaxStackSize());
        while (left > 0) {
            // The item's default instance, so goods with default components (a broom's model) arrive whole.
            ItemStack stack = item.getDefaultInstance().copyWithCount(Math.min(left, perStack));
            good.brew().ifPresent(brew -> stack.set(ModDataComponents.BREW_ID.get(), brew));
            left -= stack.getCount();
            at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, stack);
        }
        player.containerMenu.broadcastChanges();
        book.set(key, batch.pressureAfter(), now);
        player.setData(ModAttachments.MARKET_RECORD.get(), record.traded(key, count, batch.knuts(), true, week));

        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("market.wizards_and_beasts.bought", count, goodName(good, player)), paid);
        CareerProgression.record(player, ActivityType.GOODS_TRADED, location);
        IllicitDealing.roll(player, counter.def(), deal, count);
        LocationProgression.sync(player, location, false);
        return Result.DONE;
    }

    /** How the keeper the player stands at feels about Dragots; refused when they are not at a host at all. */
    static DragotAcceptance dragotPolicy(ServerPlayer player, LocationDefinition def) {
        Entity host = LocationPresence.host(player, def);
        return host == null ? DragotAcceptance.REFUSED : DragotAcceptance.policyFor(host);
    }

    // -- the board -----------------------------------------------------------------------------------------

    /** The name a good goes by on the board. */
    public static Component goodName(MarketGood good, ServerPlayer player) {
        return switch (good.kind()) {
            case ITEM -> {
                Item item = BuiltInRegistries.ITEM.getValue(good.item().orElseThrow());
                ItemStack stack = new ItemStack(item);
                good.brew().ifPresent(brew -> stack.set(ModDataComponents.BREW_ID.get(), brew));
                yield stack.getHoverName();
            }
            case TREASURE -> Component.translatable("market.wizards_and_beasts.good.treasure."
                    + good.treasureCondition());
            case WAND -> Component.translatable("market.wizards_and_beasts.good.wand." + good.condition().orElse(""));
        };
    }

    /** The board's lines for a location's market: what it buys and sells, at today's prices, for this player. */
    public static List<MarketLine> lines(ServerPlayer player, Identifier location, LocationDefinition def) {
        List<MarketLine> out = new ArrayList<>();
        MarketDefinition market = def.market().orElse(null);
        if (market == null || player.level().getServer() == null) {
            return out;
        }
        MarketBook book = MarketBook.get(player.level().getServer());
        long now = now(player);
        long day = day(player);
        long week = week(player);
        int tier = LocationProgression.tier(player, location);
        boolean outside = def.risk().isPresent();
        PlayerMarketRecord record = player.getData(ModAttachments.MARKET_RECORD.get());
        DragotAcceptance policy = dragotPolicy(player, def);
        for (MarketGood good : market.buys()) {
            String key = MarketBook.key(location.toString(), MarketDefinition.Side.BUYS, good.id());
            double pressure = book.pressure(key, now, halfLife(market));
            boolean demand = inDemand(location, market, good, day);
            double multiplier = scale() * (demand ? 1.0 + market.demandBonus() : 1.0);
            int held = 0;
            double value = good.kind() == MarketGood.Kind.ITEM ? good.priceKnuts() : 0.0;
            for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (MarketGoods.matches(good, stack, player.getUUID())) {
                    held += stack.getCount();
                    if (value <= 0.0) {
                        value = MarketGoods.baseValue(good, stack, player.registryAccess());
                    }
                }
            }
            long unit = (long) Math.floor(MarketPricing.sellUnit(value, good.depth(), pressure) * multiplier);
            boolean unlocked = good.minTier().isEmpty() || tier >= def.standing().rankIndex(good.minTier());
            DealClass deal = lineClass(good, outside);
            out.add(new MarketLine(good.id(), true, goodName(good, player), unit, held, unlocked && held > 0 && unit > 0,
                    unlocked, demand, deal, saturation(pressure, good.depth()),
                    unlocked ? Component.empty() : Component.translatable("location.wizards_and_beasts.tier_required",
                            LocationText.tier(location, good.minTier())), 0));
        }
        for (MarketGood good : market.sells()) {
            String key = MarketBook.key(location.toString(), MarketDefinition.Side.SELLS, good.id());
            double pressure = book.pressure(key, now, halfLife(market));
            long unit = (long) Math.ceil(MarketPricing.buyUnit(good.priceKnuts(), good.depth(), pressure) * scale());
            boolean unlocked = good.minTier().isEmpty() || tier >= def.standing().rankIndex(good.minTier());
            int left = good.weeklyLimit() > 0 ? Math.max(0, good.weeklyLimit() - record.line(key).boughtIn(week)) : -1;
            Component note = !unlocked
                    ? Component.translatable("location.wizards_and_beasts.tier_required",
                            LocationText.tier(location, good.minTier()))
                    : left >= 0 ? Component.translatable("market.wizards_and_beasts.weekly_left", left, good.weeklyLimit())
                    : Component.empty();
            DealClass deal = good.item().map(MarketGoods::classify).orElse(DealClass.LEGAL);
            int dragots = policy == DragotAcceptance.REFUSED ? 0
                    : DragotRates.dragotsToPay(policy.adjust(unit), Config.dragotGalleonRate);
            out.add(new MarketLine(good.id(), false, goodName(good, player), unit, left, unlocked && left != 0,
                    unlocked, false, deal, saturation(pressure, good.depth()), note, dragots));
        }
        return out;
    }

    /** The class a board line shows for a good, before any particular stack is read. */
    static DealClass lineClass(MarketGood good, boolean outsideTheLaw) {
        return switch (good.kind()) {
            case ITEM -> MarketGoods.classify(good.item().orElseThrow());
            case WAND -> MarketGood.STOLEN.equals(good.condition().orElse("")) ? DealClass.STOLEN : DealClass.LEGAL;
            case TREASURE -> outsideTheLaw ? DealClass.STOLEN : DealClass.LEGAL;
        };
    }

    /** How far pressure has moved a good's price, 0 (untouched) to 100 (all but saturated), for the board. */
    static int saturation(double pressure, int depth) {
        return (int) Math.round(100.0 * pressure / (pressure + depth));
    }
}
