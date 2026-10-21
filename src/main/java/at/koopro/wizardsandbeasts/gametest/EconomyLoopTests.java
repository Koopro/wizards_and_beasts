package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.currency.billing.GringottsLedger;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.billing.MoneyJournal;
import at.koopro.wizardsandbeasts.currency.dragot.DragotPurse;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.LocationView;
import at.koopro.wizardsandbeasts.location.ollivanders.WandCommission;
import at.koopro.wizardsandbeasts.location.ollivanders.WandCommissionOrder;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.world.InteractionHand;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.market.DealClass;
import at.koopro.wizardsandbeasts.market.IllicitDealing;
import at.koopro.wizardsandbeasts.market.MarketBook;
import at.koopro.wizardsandbeasts.market.MarketDefinition;
import at.koopro.wizardsandbeasts.market.MarketLine;
import at.koopro.wizardsandbeasts.market.MarketTrade;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.network.currency.VaultActionC2SPayload;
import at.koopro.wizardsandbeasts.owl.career.track.CareerGoal;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.owl.career.track.CareerTrack;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.TrackRecord;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The economy loops end to end (documentation/ECONOMY.md): traders that buy and sell at prices moved by shared
 * pressure, the law at the counter, a fence whose deals reach the Ministry, the finder's fee, certification, the
 * counter's circulation and the money journal — every one driven through the same server entry points the board's
 * packets call ({@link MarketTrade#sell}, {@link MarketTrade#buy}, {@link LocationProgression#useService}).
 *
 * <p>Market scenarios run alone: a trader's pressure is server-wide by design, so two scenarios selling to one
 * apothecary in the same batch would move each other's prices.
 */
@NullMarked
final class EconomyLoopTests {

    private static final Identifier APOTHECARY = id("apothecary");
    private static final Identifier OLLIVANDERS = id("ollivanders");
    private static final Identifier FENCE = id("knockturn_dealer");
    private static final Identifier BROOM_SHOP = id("broom_shop");

    private EconomyLoopTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("economy_market_sell_is_server_priced_and_atomic",
                "economy: selling to the apothecary takes exactly the goods, pays exactly the quoted batch, books "
                        + "MARKET_SALE, journals it and raises the good's pressure",
                EconomyLoopTests::sellAtomic);
        tests.addAlone("economy_market_cannot_sell_twice",
                "economy: two sell-all intents in one tick sell the stack once; bad counts and unknown goods do nothing",
                EconomyLoopTests::noDoubleSale);
        tests.addAlone("economy_market_vault_ceiling_keeps_goods",
                "economy: a sale the vault could not hold is refused before a single item leaves the inventory",
                EconomyLoopTests::ceilingKeepsGoods);
        tests.addAlone("economy_market_buy_all_or_nothing",
                "economy: a short vault buys nothing and loses nothing; a paid purchase hands over the goods; "
                        + "the weekly limit holds",
                EconomyLoopTests::buyAllOrNothing);
        tests.addAlone("economy_market_needs_presence",
                "economy: away from the counter nothing is bought or sold",
                EconomyLoopTests::needsPresence);
        tests.addAlone("economy_market_pressure_is_shared",
                "economy: one player's sales lower the next player's price; it recovers with time",
                EconomyLoopTests::sharedPressure);
        tests.addAlone("economy_market_law_at_the_counter",
                "economy: restricted goods need papers at a lawful counter (Ministry on); contraband is refused",
                EconomyLoopTests::lawAtCounter);
        tests.addAlone("economy_fence_deals_reach_the_ministry",
                "economy: a fence buys what a counter will not; a reported deal files the trade offence and fine",
                EconomyLoopTests::fenceReported);
        tests.addAlone("economy_market_price_scale_setting",
                "economy: the server price scale moves every quote; detection scale 0 never reports",
                EconomyLoopTests::scaleSettings);
        tests.add("economy_exploration_fee_once_per_species",
                "economy: the first field record of a species pays the finder's fee once, never again",
                EconomyLoopTests::explorationOnce);
        tests.add("economy_certification_fee_on_promotion",
                "economy: a rank's certification fee is charged once on promotion; a short vault keeps every coin",
                EconomyLoopTests::certification);
        tests.add("economy_counter_books_circulation",
                "economy: counter deposits and withdrawals are booked as circulation in the ledger",
                EconomyLoopTests::counterCirculation);
        tests.addAlone("economy_services_broom_and_fence",
                "economy: broom servicing repairs and bills by tier and wear; a memory charm halves heat for its fee",
                EconomyLoopTests::services);
        tests.addAlone("economy_dragots_at_the_till",
                "economy: a keeper who takes Dragots sells for pocket Dragots at the board's price and leaves the "
                        + "vault alone; a British counter refuses them; a short purse buys nothing",
                EconomyLoopTests::dragotsAtTheTill);
        tests.add("economy_wand_commission",
                "economy: a commission takes blank, core and fee once; collects nothing early; hands over one fitted "
                        + "wand of that wood and core when ready; refuses Elder wood and charges nothing for it",
                EconomyLoopTests::wandCommission);
        tests.addAlone("economy_broom_shop",
                "economy: a broommaker at a broom rack keeps the broom shop; a bought broom flies as its own model; "
                        + "tier and weekly limit hold; the shop buys nothing",
                EconomyLoopTests::broomShop);
    }

    // -- helpers ---------------------------------------------------------------------------------------

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static ServerPlayer customer(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, UUID.randomUUID(), GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        return player;
    }

    private static Villager keeper(GameTestHelper helper, ResourceKey<VillagerProfession> profession) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setNoAi(true);
        villager.setNoGravity(true);
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), profession));
        return villager;
    }

    private static Villager apothecary(GameTestHelper helper) {
        return keeper(helper, ModVillager.APOTHECARY.getKey());
    }

    private static PlayerVaultData vault(ServerPlayer player) {
        return player.getData(ModAttachments.VAULT_DATA.get());
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getValue(id(path));
    }

    private static int carried(ServerPlayer player, Item item) {
        return CareerProgression.carried(player, item);
    }

    private static void resetMarkets(GameTestHelper helper) {
        MarketBook.get(helper.getLevel().getServer()).reset("");
    }

    private static long ledger(GameTestHelper helper, MoneyFlow flow) {
        return GringottsLedger.get(helper.getLevel().getServer()).totals(flow).knuts();
    }

    private static MarketLine line(ServerPlayer player, Identifier location, boolean buying, String good) {
        LocationView view = LocationProgression.view(player, location);
        if (view == null) {
            throw new IllegalStateException(location + " is not open");
        }
        for (MarketLine line : view.market()) {
            if (line.buying() == buying && line.good().equals(good)) {
                return line;
            }
        }
        throw new IllegalStateException(location + " has no " + good);
    }

    private static void tier(ServerPlayer player, Identifier location, int rank) {
        TrackRecord record = LocationProgression.recordOf(player, location).withRank(rank);
        player.setData(ModAttachments.LOCATION_STANDING.get(),
                player.getData(ModAttachments.LOCATION_STANDING.get()).with(location.toString(), record));
    }

    // -- scenarios -------------------------------------------------------------------------------------

    private static void sellAtomic(GameTestHelper helper) {
        resetMarkets(helper);
        ServerPlayer player = customer(helper, "Seller");
        try {
            check(helper, Locations.open(APOTHECARY) != null, () -> "the apothecary location did not load");
            Villager keeper = apothecary(helper);
            check(helper, LocationProgression.openAtHost(player, keeper), () -> "the apothecary did not open its board");
            Item dittany = item("dittany");
            player.getInventory().add(new ItemStack(dittany, 10));
            long quoted = line(player, APOTHECARY, true, "dittany").unitKnuts();
            long before = vault(player).getTotalInKnuts();
            long booked = ledger(helper, MoneyFlow.MARKET_SALE);

            MarketTrade.Result result = MarketTrade.sell(player, APOTHECARY, "dittany", 10);
            long paid = vault(player).getTotalInKnuts() - before;
            check(helper, result == MarketTrade.Result.DONE, () -> "sale refused: " + result);
            check(helper, carried(player, dittany) == 0, () -> "dittany left behind: " + carried(player, dittany));
            check(helper, paid > 0 && paid <= quoted * 10 && paid >= quoted * 5,
                    () -> "paid " + paid + " for 10 at a first quote of " + quoted);
            check(helper, ledger(helper, MoneyFlow.MARKET_SALE) - booked == paid, () -> "ledger missed the sale");
            List<MoneyJournal.Entry> journal = player.getData(ModAttachments.MONEY_JOURNAL.get()).entries();
            check(helper, !journal.isEmpty() && journal.getLast().flow().equals("market_sale")
                    && journal.getLast().knuts() == paid, () -> "journal: " + journal);
            long after = line(player, APOTHECARY, true, "dittany").unitKnuts();
            check(helper, after < quoted, () -> "selling ten did not lower the price: " + quoted + " -> " + after);
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void noDoubleSale(GameTestHelper helper) {
        resetMarkets(helper);
        ServerPlayer player = customer(helper, "DoubleSeller");
        try {
            LocationProgression.openAtHost(player, apothecary(helper));
            Item mandrake = item("mandrake");
            player.getInventory().add(new ItemStack(mandrake, 6));
            long before = vault(player).getTotalInKnuts();
            MarketTrade.Result first = MarketTrade.sell(player, APOTHECARY, "mandrake", MarketTrade.MAX_UNITS);
            long afterFirst = vault(player).getTotalInKnuts();
            MarketTrade.Result second = MarketTrade.sell(player, APOTHECARY, "mandrake", MarketTrade.MAX_UNITS);
            check(helper, first == MarketTrade.Result.DONE, () -> "first sale: " + first);
            check(helper, second == MarketTrade.Result.NOTHING_TO_SELL, () -> "second sale: " + second);
            check(helper, vault(player).getTotalInKnuts() == afterFirst && afterFirst > before,
                    () -> "paid twice for one stack");

            player.getInventory().add(new ItemStack(mandrake, 2));
            long held = vault(player).getTotalInKnuts();
            check(helper, MarketTrade.sell(player, APOTHECARY, "mandrake", 0) == MarketTrade.Result.BAD_COUNT,
                    () -> "a zero count was accepted");
            check(helper, MarketTrade.sell(player, APOTHECARY, "mandrake", -5) == MarketTrade.Result.BAD_COUNT,
                    () -> "a negative count was accepted");
            check(helper, MarketTrade.sell(player, APOTHECARY, "no_such_good", 1) == MarketTrade.Result.UNKNOWN,
                    () -> "an unknown good was traded");
            check(helper, MarketTrade.buy(player, APOTHECARY, "dittany", Integer.MAX_VALUE) == MarketTrade.Result.BAD_COUNT,
                    () -> "an absurd purchase count was accepted");
            check(helper, MarketTrade.sell(player, id("gringotts"), "mandrake", 1) == MarketTrade.Result.UNKNOWN,
                    () -> "a place with no market traded");
            check(helper, carried(player, mandrake) == 2 && vault(player).getTotalInKnuts() == held,
                    () -> "a refused deal moved goods or money");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void ceilingKeepsGoods(GameTestHelper helper) {
        resetMarkets(helper);
        ServerPlayer player = customer(helper, "FullVault");
        try {
            LocationProgression.openAtHost(player, apothecary(helper));
            PlayerVaultData vault = vault(player);
            vault.depositKnuts(CurrencyHelper.MAX_VAULT_KNUTS - 1);
            Item bezoar = item("bezoar");
            player.getInventory().add(new ItemStack(bezoar, 3));
            MarketTrade.Result result = MarketTrade.sell(player, APOTHECARY, "bezoar", 3);
            check(helper, result == MarketTrade.Result.VAULT_FULL, () -> "a full vault sold: " + result);
            check(helper, carried(player, bezoar) == 3, () -> "goods left a refused sale");
            check(helper, vault.getTotalInKnuts() == CurrencyHelper.MAX_VAULT_KNUTS - 1, () -> "vault moved");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void buyAllOrNothing(GameTestHelper helper) {
        resetMarkets(helper);
        ServerPlayer player = customer(helper, "Buyer");
        try {
            LocationProgression.openAtHost(player, apothecary(helper));
            Item bezoar = item("bezoar");
            check(helper, MarketTrade.buy(player, APOTHECARY, "bezoar", 1) == MarketTrade.Result.CANNOT_AFFORD,
                    () -> "an empty vault bought a bezoar");
            check(helper, carried(player, bezoar) == 0, () -> "goods given unpaid");

            vault(player).depositGalleons(20);
            vault(player).depositSickles(3);
            long before = vault(player).getTotalInKnuts();
            long sickles = vault(player).getSickles();
            long quoted = line(player, APOTHECARY, false, "bezoar").unitKnuts();
            check(helper, MarketTrade.buy(player, APOTHECARY, "bezoar", 1) == MarketTrade.Result.DONE,
                    () -> "a funded purchase was refused");
            check(helper, carried(player, bezoar) == 1, () -> "the bezoar never arrived");
            check(helper, before - vault(player).getTotalInKnuts() == quoted,
                    () -> "charged " + (before - vault(player).getTotalInKnuts()) + " for a quote of " + quoted);
            check(helper, vault(player).getSickles() >= 0 && vault(player).getKnuts() >= 0
                    && vault(player).getGalleons() >= 0, () -> "a denomination went negative");
            // Weekly limit 3: two more succeed, the fourth is refused, and nothing is charged for it.
            MarketTrade.buy(player, APOTHECARY, "bezoar", 1);
            MarketTrade.buy(player, APOTHECARY, "bezoar", 1);
            long held = vault(player).getTotalInKnuts();
            check(helper, MarketTrade.buy(player, APOTHECARY, "bezoar", 1) == MarketTrade.Result.WEEKLY_LIMIT,
                    () -> "the weekly limit did not hold");
            check(helper, carried(player, bezoar) == 3 && vault(player).getTotalInKnuts() == held,
                    () -> "the refused purchase moved goods or money");
            check(helper, ledger(helper, MoneyFlow.MARKET_PURCHASE) > 0, () -> "purchases not booked as a sink");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void needsPresence(GameTestHelper helper) {
        resetMarkets(helper);
        ServerPlayer player = customer(helper, "AbsentTrader");
        try {
            apothecary(helper);
            player.getInventory().add(new ItemStack(item("dittany"), 4));
            vault(player).depositGalleons(5);
            check(helper, MarketTrade.sell(player, APOTHECARY, "dittany", 4) == MarketTrade.Result.NOT_THERE,
                    () -> "sold without being admitted");
            check(helper, MarketTrade.buy(player, APOTHECARY, "dittany", 1) == MarketTrade.Result.NOT_THERE,
                    () -> "bought without being admitted");
            check(helper, carried(player, item("dittany")) == 4, () -> "goods moved");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void sharedPressure(GameTestHelper helper) {
        resetMarkets(helper);
        ServerPlayer first = customer(helper, "FirstSeller");
        ServerPlayer second = customer(helper, "SecondSeller");
        try {
            Villager keeper = apothecary(helper);
            LocationProgression.openAtHost(first, keeper);
            LocationProgression.openAtHost(second, keeper);
            long fresh = line(second, APOTHECARY, true, "gillyweed").unitKnuts();
            first.getInventory().add(new ItemStack(item("gillyweed"), 24));
            MarketTrade.sell(first, APOTHECARY, "gillyweed", 24);
            long glutted = line(second, APOTHECARY, true, "gillyweed").unitKnuts();
            check(helper, glutted < fresh, () -> "another player's sale did not move the price: " + fresh + " -> " + glutted);

            // Two half-lives later the market has largely recovered (pressure is decayed on read from the stored tick).
            MarketBook book = MarketBook.get(helper.getLevel().getServer());
            String key = MarketBook.key(APOTHECARY.toString(), MarketDefinition.Side.BUYS, "gillyweed");
            long now = helper.getLevel().getGameTime();
            double pressure = book.pressure(key, now, 2.0);
            book.set(key, pressure, now - 4L * 24_000L);
            long recovered = line(second, APOTHECARY, true, "gillyweed").unitKnuts();
            check(helper, recovered > glutted, () -> "the price did not recover: " + glutted + " -> " + recovered);
            helper.succeed();
        } finally {
            retire(helper, first);
            retire(helper, second);
        }
    }

    private static void lawAtCounter(GameTestHelper helper) {
        resetMarkets(helper);
        Runnable ministry = WizardTestSupport.leaseModule(Module.MINISTRY);
        ServerPlayer player = customer(helper, "Unlicensed");
        try {
            LocationProgression.openAtHost(player, apothecary(helper));
            tier(player, APOTHECARY, 1);
            player.getInventory().add(new ItemStack(item("unicorn_hair"), 2));
            long before = vault(player).getTotalInKnuts();
            MarketTrade.Result result = MarketTrade.sell(player, APOTHECARY, "unicorn_hair", 2);
            check(helper, result == MarketTrade.Result.NO_PAPERS, () -> "restricted goods sold without papers: " + result);
            check(helper, carried(player, item("unicorn_hair")) == 2 && vault(player).getTotalInKnuts() == before,
                    () -> "the refusal moved goods or money");
            check(helper, line(player, APOTHECARY, true, "unicorn_hair").deal() == DealClass.RESTRICTED,
                    () -> "the board does not mark unicorn hair as restricted");
            helper.succeed();
        } finally {
            retire(helper, player);
            ministry.run();
        }
    }

    private static void fenceReported(GameTestHelper helper) {
        resetMarkets(helper);
        Runnable ministry = WizardTestSupport.leaseModule(Module.MINISTRY);
        ServerPlayer player = customer(helper, "Smuggler");
        try {
            check(helper, Locations.open(FENCE) != null, () -> "the fence did not open with the Ministry on");
            WanderingTrader fence = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(1, 1, 1));
            fence.setNoAi(true);
            fence.setNoGravity(true);
            check(helper, FENCE.equals(LocationPresence.hostedBy(fence)), () -> "a wandering trader keeps no fence");
            LocationProgression.openAtHost(player, fence);

            // Unreported: no licence asked, paid as illicit income, nothing on file.
            IllicitDealing.setTestOutcome(false);
            player.getInventory().add(new ItemStack(item("unicorn_hair"), 1));
            long illicit = ledger(helper, MoneyFlow.ILLICIT_SALE);
            check(helper, MarketTrade.sell(player, FENCE, "unicorn_hair", 1) == MarketTrade.Result.DONE,
                    () -> "the fence refused restricted goods");
            check(helper, ledger(helper, MoneyFlow.ILLICIT_SALE) > illicit, () -> "not booked as an illicit sale");
            check(helper, MinistryRecords.get(player).offenceCount(MagicalOffence.TRAFFICKING_RESTRICTED) == 0,
                    () -> "an unreported deal was filed");

            // Reported: the trade offence lands on the file through the Ministry's own door.
            IllicitDealing.setTestOutcome(true);
            player.getInventory().add(new ItemStack(item("basilisk_fang"), 1));
            tier(player, FENCE, 1);
            check(helper, MarketTrade.sell(player, FENCE, "basilisk_fang", 1) == MarketTrade.Result.DONE,
                    () -> "the fence refused contraband");
            check(helper, MinistryRecords.get(player).offenceCount(MagicalOffence.DEALING_CONTRABAND) == 1,
                    () -> "a reported contraband deal was not filed");
            check(helper, MinistryRecords.get(player).notoriety() > 0, () -> "a filed deal raised no heat");

            // A lawful counter refuses the same contraband outright.
            LocationProgression.openAtHost(player, apothecary(helper));
            player.getInventory().add(new ItemStack(item("basilisk_fang"), 1));
            check(helper, MarketTrade.sell(player, APOTHECARY, "basilisk_fang", 1) == MarketTrade.Result.UNKNOWN,
                    () -> "the apothecary lists contraband");
            helper.succeed();
        } finally {
            IllicitDealing.setTestOutcome(null);
            retire(helper, player);
            ministry.run();
        }
    }

    private static void dragotsAtTheTill(GameTestHelper helper) {
        resetMarkets(helper);
        Runnable ministry = WizardTestSupport.leaseModule(Module.MINISTRY);
        ServerPlayer player = customer(helper, "Traveller");
        try {
            IllicitDealing.setTestOutcome(false);
            WanderingTrader fence = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(1, 1, 1));
            fence.setNoAi(true);
            fence.setNoGravity(true);
            LocationProgression.openAtHost(player, fence);
            Item hair = item("unicorn_hair");
            vault(player).depositGalleons(5);
            long vaultBefore = vault(player).getTotalInKnuts();

            // Short purse first: nothing moves.
            int asked = line(player, FENCE, false, "unicorn_hair").dragots();
            check(helper, asked > 0, () -> "the fence (dragot/accepts) quotes no Dragot price");
            DragotPurse.give(player, asked - 1);
            check(helper, MarketTrade.buy(player, FENCE, "unicorn_hair", 1, MarketTrade.Tender.DRAGOTS)
                    == MarketTrade.Result.CANNOT_AFFORD, () -> "a short purse bought hair");
            check(helper, carried(player, hair) == 0 && DragotPurse.total(player) == asked - 1,
                    () -> "the refused purchase moved goods or coin");

            DragotPurse.give(player, 1 + 10);
            long tendered = ledger(helper, MoneyFlow.DRAGOT_TENDER);
            check(helper, MarketTrade.buy(player, FENCE, "unicorn_hair", 1, MarketTrade.Tender.DRAGOTS)
                    == MarketTrade.Result.DONE, () -> "a full purse was refused");
            check(helper, carried(player, hair) == 1, () -> "the hair never arrived");
            check(helper, DragotPurse.total(player) == 10,
                    () -> "took " + (asked + 10 - DragotPurse.total(player)) + " Dragots for a price of " + asked);
            check(helper, vault(player).getTotalInKnuts() == vaultBefore, () -> "a Dragot purchase touched the vault");
            check(helper, ledger(helper, MoneyFlow.DRAGOT_TENDER) > tendered, () -> "the tender was not booked");

            // A British counter has never seen a Dragot.
            LocationProgression.openAtHost(player, apothecary(helper));
            check(helper, line(player, APOTHECARY, false, "bezoar").dragots() == 0,
                    () -> "the apothecary quotes a Dragot price");
            check(helper, MarketTrade.buy(player, APOTHECARY, "bezoar", 1, MarketTrade.Tender.DRAGOTS)
                    == MarketTrade.Result.NO_DRAGOTS, () -> "the apothecary took Dragots");
            check(helper, DragotPurse.total(player) == 10 && carried(player, item("bezoar")) == 0,
                    () -> "the refusal moved goods or coin");
            helper.succeed();
        } finally {
            IllicitDealing.setTestOutcome(null);
            retire(helper, player);
            ministry.run();
        }
    }

    private static ItemStack blank(String wood) {
        ItemStack blank = new ItemStack(WandItemRegistry.WAND_BLANK.get());
        blank.set(WandComponents.WAND_WOOD.get(), id(wood));
        return blank;
    }

    private static void wandCommission(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Commissioner");
        try {
            Villager wandmaker = keeper(helper, ModVillager.WANDMAKER.getKey());
            LocationProgression.openAtHost(player, wandmaker);
            Identifier service = id("wand_commission");
            vault(player).depositGalleons(20);

            // Below the offer's tier: refused, nothing taken.
            player.setItemInHand(InteractionHand.MAIN_HAND, blank("holly"));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(WandItemRegistry.UNICORN_HAIR.get()));
            long start = vault(player).getTotalInKnuts();
            check(helper, LocationProgression.useService(player, OLLIVANDERS, service)
                    != LocationProgression.Result.DONE, () -> "a patron placed a commission meant for regulars");
            tier(player, OLLIVANDERS, 1);

            // Elder wood is never made to order, and costs nothing to be told so.
            player.setItemInHand(InteractionHand.MAIN_HAND, blank("elder"));
            check(helper, LocationProgression.useService(player, OLLIVANDERS, service)
                    == LocationProgression.Result.REFUSED, () -> "an Elder wand was taken to order");
            check(helper, vault(player).getTotalInKnuts() == start && !player.getMainHandItem().isEmpty(),
                    () -> "the refused commission moved money or materials");

            // Placed: the fee once, both materials gone, an order on file.
            player.setItemInHand(InteractionHand.MAIN_HAND, blank("holly"));
            long commissions = ledger(helper, MoneyFlow.WAND_COMMISSION);
            check(helper, LocationProgression.useService(player, OLLIVANDERS, service)
                    == LocationProgression.Result.DONE, () -> "a funded commission was refused");
            long paid = start - vault(player).getTotalInKnuts();
            check(helper, paid == WandCommission.priceOf(1479, "uncommon"),
                    () -> "a holly commission cost " + paid + " K");
            check(helper, player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(),
                    () -> "the materials stayed in hand");
            check(helper, ledger(helper, MoneyFlow.WAND_COMMISSION) - commissions == paid,
                    () -> "the commission was not booked");
            WandCommissionOrder.Order order = WandCommission.orderOf(player).order().orElse(null);
            check(helper, order != null && order.wood().equals(id("holly")), () -> "no order on file");

            // Too early: nothing handed over, nothing charged; a second placement cannot double-charge.
            long held = vault(player).getTotalInKnuts();
            check(helper, LocationProgression.useService(player, OLLIVANDERS, service)
                    != LocationProgression.Result.DONE, () -> "the wand was collected before it was made");
            check(helper, vault(player).getTotalInKnuts() == held, () -> "waiting cost money");

            // Ready: one wand of that wood and core, order closed, collecting again does nothing.
            player.setData(ModAttachments.WAND_COMMISSION.get(), WandCommissionOrder.of(
                    new WandCommissionOrder.Order(order.wood(), order.core(), 0L, order.paidKnuts())));
            check(helper, LocationProgression.useService(player, OLLIVANDERS, service)
                    == LocationProgression.Result.DONE, () -> "a ready wand could not be collected");
            int wands = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.getItem() instanceof WandItem) {
                    wands += stack.getCount();
                    check(helper, id("holly").equals(WandComponents.getWood(stack))
                            && id("unicorn_hair").equals(WandComponents.getCore(stack))
                            && WandComponents.getFlexibility(stack) != null, () -> "the wrong wand was made");
                }
            }
            int made = wands;
            check(helper, made == 1, () -> made + " wands handed over");
            check(helper, WandCommission.orderOf(player).order().isEmpty(), () -> "the order stayed open");
            check(helper, LocationProgression.useService(player, OLLIVANDERS, service)
                    != LocationProgression.Result.DONE, () -> "a closed order was collected twice");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void broomShop(GameTestHelper helper) {
        resetMarkets(helper);
        ServerPlayer player = customer(helper, "Flyer");
        try {
            Villager broommaker = keeper(helper, ModVillager.BROOMMAKER.getKey());
            check(helper, BROOM_SHOP.equals(LocationPresence.hostedBy(broommaker)),
                    () -> "a broommaker keeps no broom shop");
            LocationProgression.openAtHost(player, broommaker);
            vault(player).depositGalleons(100);
            check(helper, MarketTrade.buy(player, BROOM_SHOP, "nimbus_2000", 1) == MarketTrade.Result.TIER_TOO_LOW,
                    () -> "a new customer bought a Nimbus");
            long before = vault(player).getTotalInKnuts();
            long quoted = line(player, BROOM_SHOP, false, "cleansweep_seven").unitKnuts();
            check(helper, MarketTrade.buy(player, BROOM_SHOP, "cleansweep_seven", 1) == MarketTrade.Result.DONE,
                    () -> "a funded broom purchase was refused");
            check(helper, before - vault(player).getTotalInKnuts() == quoted, () -> "charged off the quote");
            ItemStack bought = ItemStack.EMPTY;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (player.getInventory().getItem(slot).is(BroomItemRegistry.CLEANSWEEP_SEVEN.get())) {
                    bought = player.getInventory().getItem(slot);
                }
            }
            ItemStack broom = bought;
            check(helper, !broom.isEmpty() && id("cleansweep_seven").equals(
                    broom.get(at.koopro.wizardsandbeasts.registry.ModDataComponents.BROOM_DEFINITION.get())),
                    () -> "the broom arrived without its own definition");
            tier(player, BROOM_SHOP, 1);
            check(helper, MarketTrade.buy(player, BROOM_SHOP, "nimbus_2000", 1) == MarketTrade.Result.DONE,
                    () -> "a regular could not buy a Nimbus");
            long held = vault(player).getTotalInKnuts();
            check(helper, MarketTrade.buy(player, BROOM_SHOP, "nimbus_2000", 1) == MarketTrade.Result.WEEKLY_LIMIT
                    && vault(player).getTotalInKnuts() == held, () -> "the weekly Nimbus limit did not hold");
            player.getInventory().add(new ItemStack(BroomItemRegistry.CLEANSWEEP_SEVEN.get()));
            check(helper, MarketTrade.sell(player, BROOM_SHOP, "cleansweep_seven", 1) == MarketTrade.Result.UNKNOWN,
                    () -> "the broom shop bought a broom back");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void scaleSettings(GameTestHelper helper) {
        resetMarkets(helper);
        int priceBefore = Config.marketPriceScalePercent;
        int detectBefore = Config.illicitDetectionScalePercent;
        Runnable ministry = WizardTestSupport.leaseModule(Module.MINISTRY);
        ServerPlayer player = customer(helper, "ScaleWatcher");
        try {
            LocationProgression.openAtHost(player, apothecary(helper));
            Config.marketPriceScalePercent = 100;
            long base = line(player, APOTHECARY, false, "floo_powder").unitKnuts();
            Config.marketPriceScalePercent = 200;
            long doubled = line(player, APOTHECARY, false, "floo_powder").unitKnuts();
            check(helper, doubled >= base * 2 - 1 && doubled <= base * 2 + 1, () -> base + " -> " + doubled);

            Config.illicitDetectionScalePercent = 0;
            check(helper, IllicitDealing.chance(player, Locations.open(FENCE), DealClass.CONTRABAND, 64) == 0.0,
                    () -> "detection scale 0 still reports");
            Config.illicitDetectionScalePercent = 100;
            check(helper, IllicitDealing.chance(player, Locations.open(FENCE), DealClass.CONTRABAND, 1) > 0.0,
                    () -> "a fence deal carries no risk at the shipped scale");
            helper.succeed();
        } finally {
            Config.marketPriceScalePercent = priceBefore;
            Config.illicitDetectionScalePercent = detectBefore;
            retire(helper, player);
            ministry.run();
        }
    }

    private static void explorationOnce(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Explorer");
        try {
            Identifier niffler = id("niffler");
            long before = vault(player).getTotalInKnuts();
            BestiaryDataHelper.setTier(player, niffler, DiscoveryTier.ENCOUNTERED);
            check(helper, vault(player).getTotalInKnuts() == before, () -> "a glimpse paid the fee");
            BestiaryDataHelper.setTier(player, niffler, DiscoveryTier.OBSERVED);
            long fee = vault(player).getTotalInKnuts() - before;
            check(helper, fee > 0, () -> "the first record paid nothing");
            BestiaryDataHelper.setTier(player, niffler, DiscoveryTier.STUDIED);
            BestiaryDataHelper.forceSetTier(player, niffler, DiscoveryTier.ENCOUNTERED);
            BestiaryDataHelper.setTier(player, niffler, DiscoveryTier.OBSERVED);
            check(helper, vault(player).getTotalInKnuts() - before == fee,
                    () -> "the fee was paid again: " + (vault(player).getTotalInKnuts() - before) + " vs " + fee);
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void certification(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Candidate");
        try {
            CareerTrack.Rank rank = new CareerTrack.Rank("certified", 10, List.of(new CareerGoal.Fee(986)), 1.0f, List.of());
            vault(player).depositSickles(10);
            long poor = vault(player).getTotalInKnuts();
            long sickles = vault(player).getSickles();
            check(helper, !CareerProgression.payCertification(player, rank), () -> "a short vault was certified");
            check(helper, vault(player).getTotalInKnuts() == poor && vault(player).getSickles() == sickles,
                    () -> "a refused fee moved coins");
            vault(player).depositGalleons(3);
            long rich = vault(player).getTotalInKnuts();
            check(helper, CareerProgression.payCertification(player, rank), () -> "a funded fee was refused");
            check(helper, rich - vault(player).getTotalInKnuts() == 986, () -> "the fee charged was wrong");
            check(helper, ledger(helper, MoneyFlow.CERTIFICATION) >= 986, () -> "the fee was not booked");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void counterCirculation(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Depositor");
        try {
            at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity teller = helper.spawn(
                    at.koopro.wizardsandbeasts.registry.ModEntities.GOBLIN_TELLER.get(), new BlockPos(1, 1, 1));
            teller.setNoAi(true);
            at.koopro.wizardsandbeasts.currency.vault.GringottsCounter.admit(player, teller);
            player.getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), 5));
            long deposits = ledger(helper, MoneyFlow.COUNTER_DEPOSIT);
            VaultActionC2SPayload.perform(player, new VaultActionC2SPayload(
                    VaultActionC2SPayload.Action.DEPOSIT_SICKLE.ordinal(), 5));
            check(helper, ledger(helper, MoneyFlow.COUNTER_DEPOSIT) - deposits == 5L * CurrencyHelper.KNUTS_PER_SICKLE,
                    () -> "a deposit was not booked as circulation");
            long withdrawals = ledger(helper, MoneyFlow.COUNTER_WITHDRAWAL);
            VaultActionC2SPayload.perform(player, new VaultActionC2SPayload(
                    VaultActionC2SPayload.Action.WITHDRAW_KNUT.ordinal(), 10));
            check(helper, ledger(helper, MoneyFlow.COUNTER_WITHDRAWAL) - withdrawals == 10,
                    () -> "a withdrawal was not booked as circulation");

            // A nearly full pocket: what does not fit is dropped at the counter, never destroyed after the vault paid.
            for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
                player.getInventory().setItem(slot, new ItemStack(net.minecraft.world.item.Items.STONE, 64));
            }
            player.getInventory().setItem(0, new ItemStack(CurrencyItemRegistry.KNUT.get(), 60));
            long vaultBefore = vault(player).getTotalInKnuts();
            VaultActionC2SPayload.perform(player, new VaultActionC2SPayload(
                    VaultActionC2SPayload.Action.WITHDRAW_KNUT.ordinal(), 10));
            int pocket = CareerProgression.carried(player, CurrencyItemRegistry.KNUT.get());
            int dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            player.getBoundingBox().inflate(4.0), e -> e.getItem().is(CurrencyItemRegistry.KNUT.get()))
                    .stream().mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, vaultBefore - vault(player).getTotalInKnuts() == 10, () -> "the vault paid out the wrong sum");
            check(helper, pocket - 60 + dropped == 10,
                    () -> "withdrew 10 Knuts into a full pocket: " + (pocket - 60) + " in hand, " + dropped + " dropped");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void services(GameTestHelper helper) {
        resetMarkets(helper);
        Runnable ministry = WizardTestSupport.leaseModule(Module.MINISTRY);
        ServerPlayer player = customer(helper, "Patron");
        try {
            // Broom servicing at the broom shop: full repair, billed by tier and wear.
            Villager broommaker = keeper(helper, ModVillager.BROOMMAKER.getKey());
            LocationProgression.openAtHost(player, broommaker);
            ItemStack broom = new ItemStack(BroomItemRegistry.NIMBUS_2000.get());
            broom.setDamageValue(broom.getMaxDamage() / 2);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, broom);
            vault(player).depositGalleons(10);
            long before = vault(player).getTotalInKnuts();
            check(helper, LocationProgression.useService(player, BROOM_SHOP, id("broom_servicing"))
                    == LocationProgression.Result.DONE, () -> "broom servicing refused");
            check(helper, player.getMainHandItem().getDamageValue() == 0, () -> "the broom was not repaired");
            long paid = before - vault(player).getTotalInKnuts();
            check(helper, paid > 0 && paid % CurrencyHelper.KNUTS_PER_SICKLE == 0, () -> "broom service billed " + paid);
            check(helper, LocationProgression.useService(player, BROOM_SHOP, id("broom_servicing"))
                    == LocationProgression.Result.REFUSED, () -> "a sound broom was serviced again");

            // A memory charm at the fence: half the heat gone, billed as an illicit purchase.
            WanderingTrader fence = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(2, 1, 1));
            fence.setNoAi(true);
            LocationProgression.openAtHost(player, fence);
            tier(player, FENCE, 1);
            MinistryRecords.mutate(player, record -> record.withNotoriety(30.0f));
            IllicitDealing.setTestOutcome(false);
            long illicit = ledger(helper, MoneyFlow.ILLICIT_PURCHASE);
            check(helper, LocationProgression.useService(player, FENCE, id("memory_charm"))
                    == LocationProgression.Result.DONE, () -> "the memory charm was refused");
            float heat = MinistryRecords.get(player).notoriety();
            check(helper, heat > 14.0f && heat < 16.0f, () -> "heat after the charm: " + heat);
            check(helper, ledger(helper, MoneyFlow.ILLICIT_PURCHASE) - illicit >= 30L * 29L,
                    () -> "the charm was not booked as an illicit purchase");
            helper.succeed();
        } finally {
            IllicitDealing.setTestOutcome(null);
            retire(helper, player);
            ministry.run();
        }
    }
}
