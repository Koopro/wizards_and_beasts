package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.currency.vault.GringottsCounter;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.LocationView;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.market.MarketBook;
import at.koopro.wizardsandbeasts.market.MarketLine;
import at.koopro.wizardsandbeasts.market.MarketTrade;
import at.koopro.wizardsandbeasts.ministry.trace.CastSurvey;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.network.currency.VaultActionC2SPayload;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.settlement.PostRef;
import at.koopro.wizardsandbeasts.settlement.SettlementData;
import at.koopro.wizardsandbeasts.settlement.SettlementDiscovery;
import at.koopro.wizardsandbeasts.settlement.npc.NpcTalk;
import at.koopro.wizardsandbeasts.settlement.npc.NpcRole;
import at.koopro.wizardsandbeasts.settlement.npc.NpcRoles;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import at.koopro.wizardsandbeasts.settlement.post.PostRules;
import at.koopro.wizardsandbeasts.settlement.post.SettlementPostBlockEntity;
import at.koopro.wizardsandbeasts.settlement.shop.SettlementShops;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * A settlement's people and shops, through the entry points play uses: the post's look, the board's actions, the
 * market's trades, a real lethal blow. Natural generation is {@code SettlementWorldProbe}'s job, on real terrain.
 */
public final class SettlementTests {

    private static final Identifier APOTHECARY = id("apothecary");
    private static final Identifier OLLIVANDERS = id("ollivanders");
    private static final Identifier HAMLET = id("hamlet");
    private static final BlockPos POST = new BlockPos(2, 1, 2);

    private SettlementTests() {}

    public static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("settlement_post_fills_itself_once",
                "settlement: a post brings its person once, named and with a role; further looks bring nobody else",
                SettlementTests::postFillsOnce);
        tests.addAlone("settlement_lethal_blow_apparates_them_away",
                "settlement: a lethal blow drops nothing and leaves no body; they stay away until their time, and the "
                        + "same person returns",
                SettlementTests::lethalBlow);
        tests.addAlone("settlement_stale_copy_is_refused",
                "settlement: a copy of someone the record does not name cannot join the world, so a post never has two",
                SettlementTests::staleCopy);
        tests.addAlone("settlement_lost_person_is_replaced_only_after_many_looks",
                "settlement: someone vanishing from the world is not replaced at once, and is after long enough, once",
                SettlementTests::lostPerson);
        tests.addAlone("settlement_module_off_brings_nobody",
                "settlement: with the module off a post brings nobody",
                SettlementTests::moduleOff);
        tests.addAlone("settlement_keeper_opens_board_and_talks",
                "settlement: speaking to a keeper opens their board with a greeting and topics; a topic is answered; "
                        + "one the player may not ask, or asked from too far, gets nothing",
                SettlementTests::keeperTalks);
        tests.addAlone("settlement_two_players_and_the_last_unit",
                "settlement: two players at one shelf cannot both have the last unit; the second is refused and not "
                        + "charged, and the shelf is the same for both",
                SettlementTests::lastUnit);
        tests.addAlone("settlement_shops_share_prices_not_shelves",
                "settlement: stock taken at one site leaves another's full; price pressure is the whole market's",
                SettlementTests::sharedPrices);
        tests.addAlone("settlement_teller_takes_and_returns_pocket_coin",
                "settlement: a post's goblin teller keeps the bank: pocket coins go in, then buy at a shop, and vault "
                        + "money comes back out as coins",
                SettlementTests::tellerRoundTrip);
        tests.addAlone("settlement_wandmaker_runs_the_real_trial",
                "settlement: a settlement wandmaker keeps Ollivanders and opens the same trial; a heritage the Code "
                        + "forbids is refused it, but still has the shop",
                SettlementTests::wandmaker);
        tests.addAlone("settlement_arrival_is_recorded_once_per_player",
                "settlement: arriving records the place for that player once, and it survives a reconnect",
                SettlementTests::arrival);
        tests.add("settlement_staff_are_no_muggles",
                "settlement: a cast in a hamlet has no Muggle witness among its people",
                SettlementTests::noMuggles);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static ServerPlayer customer(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, UUID.randomUUID(), GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        return player;
    }

    /** A post for {@code role} at the plot's post position, on a site wiped clean for this scenario. */
    private static SettlementPostBlockEntity post(GameTestHelper helper, String role) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(POST);
        SettlementData.get(level).forget(SettlementData.siteKey(level, new ChunkPos(pos)));
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, ModBlocks.SETTLEMENT_POST.get().defaultBlockState());
        SettlementPostBlockEntity post = (SettlementPostBlockEntity) level.getBlockEntity(pos);
        post.setRole(id(role).toString());
        return post;
    }

    private static PostRef ref(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(POST);
        return new PostRef(SettlementData.siteKey(helper.getLevel(), new ChunkPos(pos)), pos.asLong());
    }

    private static SettlementData.Post record(GameTestHelper helper) {
        return SettlementData.get(helper.getLevel()).post(ref(helper));
    }

    private static Entity person(GameTestHelper helper) {
        return record(helper).npc().map(helper.getLevel()::getEntity).orElse(null);
    }

    /** One look, and the person it brought, kept still. */
    private static Mob staffed(GameTestHelper helper, SettlementPostBlockEntity post) {
        post.lookLoaded(helper.getLevel(), helper.absolutePos(POST));
        Mob mob = (Mob) person(helper);
        check(helper, mob != null, () -> "the post brought nobody");
        mob.setNoAi(true);
        mob.setNoGravity(true);
        return mob;
    }

    private static List<Entity> around(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(POST);
        return helper.getLevel().getEntities((Entity) null, new AABB(pos).inflate(6.0),
                entity -> entity.hasData(ModAttachments.SETTLEMENT_POST.get()));
    }

    private static PlayerVaultData vault(ServerPlayer player) {
        return player.getData(ModAttachments.VAULT_DATA.get());
    }

    private static MarketLine line(ServerPlayer player, Identifier location, String good) {
        LocationView view = LocationProgression.view(player, location);
        return view.market().stream().filter(l -> !l.buying() && l.good().equals(good)).findFirst().orElse(null);
    }

    // -- scenarios -------------------------------------------------------------------------------------

    private static void postFillsOnce(GameTestHelper helper) {
        SettlementPostBlockEntity post = post(helper, "keeper");
        Mob first = staffed(helper, post);
        check(helper, first instanceof SettlementNpcEntity npc && npc.roleId().equals(id("keeper").toString()),
                () -> "brought " + first);
        check(helper, first.hasCustomName(), () -> "the keeper has no name");
        check(helper, first.isPersistenceRequired(), () -> "the keeper could despawn");
        check(helper, first.hasHome(), () -> "the keeper has no home to stay near");
        post.lookLoaded(helper.getLevel(), helper.absolutePos(POST));
        post.lookLoaded(helper.getLevel(), helper.absolutePos(POST));
        check(helper, around(helper).size() == 1, () -> "the post holds " + around(helper).size());
        check(helper, record(helper).status() == PostRules.Status.PRESENT, () -> "record says " + record(helper).status());
        first.discard();
        helper.succeed();
    }

    private static void lethalBlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SettlementPostBlockEntity post = post(helper, "keeper");
        Mob keeper = staffed(helper, post);
        String name = keeper.getCustomName().getString();
        keeper.hurtServer(level, level.damageSources().magic(), 1000f);
        check(helper, keeper.isRemoved(), () -> "they stayed after a lethal blow");
        check(helper, level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POST)).inflate(6.0)).isEmpty(),
                () -> "something dropped");
        SettlementData.Post away = record(helper);
        check(helper, away.status() == PostRules.Status.AWAY && away.backAt() > level.getGameTime(),
                () -> "record after the blow: " + away);
        post.lookLoaded(level, helper.absolutePos(POST));
        check(helper, around(helper).isEmpty(), () -> "back before their time");
        SettlementData.get(level).setPost(ref(helper), away.away(level.getGameTime()));
        Mob back = staffed(helper, post);
        check(helper, back.getCustomName().getString().equals(name), () -> "came back as someone else");
        check(helper, !back.getUUID().equals(keeper.getUUID()), () -> "the old body came back");
        back.discard();
        helper.succeed();
    }

    private static void staleCopy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SettlementPostBlockEntity post = post(helper, "keeper");
        Mob keeper = staffed(helper, post);
        SettlementNpcEntity copy = at.koopro.wizardsandbeasts.registry.ModEntities.SETTLEMENT_NPC.get()
                .create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        copy.snapTo(keeper.getX(), keeper.getY(), keeper.getZ(), 0f, 0f);
        copy.setData(ModAttachments.SETTLEMENT_POST.get(), keeper.getData(ModAttachments.SETTLEMENT_POST.get()));
        level.addFreshEntity(copy);
        check(helper, around(helper).size() == 1, () -> "a stale copy joined: " + around(helper).size());
        keeper.discard();
        helper.succeed();
    }

    private static void lostPerson(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SettlementPostBlockEntity post = post(helper, "keeper");
        Mob keeper = staffed(helper, post);
        UUID was = keeper.getUUID();
        keeper.discard();
        BlockPos pos = helper.absolutePos(POST);
        for (int look = 1; look < PostRules.LOST_AFTER_LOOKS; look++) {
            post.lookLoaded(level, pos);
        }
        check(helper, record(helper).npc().filter(was::equals).isPresent(), () -> "replaced after one missed look");
        post.lookLoaded(level, pos);
        check(helper, !record(helper).npc().orElse(was).equals(was), () -> "never replaced");
        check(helper, around(helper).size() == 1, () -> "the post holds " + around(helper).size());
        person(helper).discard();
        helper.succeed();
    }

    private static void moduleOff(GameTestHelper helper) {
        SettlementPostBlockEntity post = post(helper, "keeper");
        ModuleState was = ModuleManager.state(Module.SETTLEMENTS);
        ModuleManager.overrideCached(Module.SETTLEMENTS, ModuleState.DISABLED);
        try {
            post.look(helper.getLevel(), helper.absolutePos(POST));
            check(helper, around(helper).isEmpty(), () -> "a post worked with the module off");
        } finally {
            ModuleManager.overrideCached(Module.SETTLEMENTS, was);
        }
        helper.succeed();
    }

    private static void keeperTalks(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Visitor");
        try {
            Mob keeper = staffed(helper, post(helper, "keeper"));
            check(helper, LocationProgression.openAtHost(player, keeper), () -> "no board");
            LocationView view = LocationProgression.view(player, HAMLET);
            check(helper, view != null && view.talk().present(), () -> "nobody spoke");
            check(helper, !view.talk().greeting().getString().isEmpty(), () -> "no greeting");
            check(helper, !view.talk().topics().isEmpty(), () -> "nothing to ask");
            check(helper, view.talk().reply().getString().isEmpty(), () -> "answered before being asked");
            check(helper, view.offers().size() == 3, () -> "board offers " + view.offers().size());

            NpcTalk.ask(player, HAMLET, "place");
            check(helper, !LocationProgression.view(player, HAMLET).talk().reply().getString().isEmpty(), () -> "no answer");
            NpcTalk.ask(player, HAMLET, "no_such_topic");
            check(helper, LocationProgression.view(player, HAMLET).talk().reply().getString().isEmpty(),
                    () -> "answered a topic that does not exist");

            player.teleportTo(player.getX() + 40, player.getY(), player.getZ());
            NpcTalk.ask(player, HAMLET, "place");
            LocationDefinition def = Locations.open(HAMLET);
            check(helper, !LocationPresence.isAt(player, def), () -> "still at the counter from 40 blocks off");
            check(helper, !LocationProgression.view(player, HAMLET).talk().present(), () -> "talked across a field");
            keeper.discard();
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void lastUnit(GameTestHelper helper) {
        ServerPlayer first = customer(helper, "FirstInLine");
        ServerPlayer second = customer(helper, "SecondInLine");
        try {
            MarketBook.get(helper.getLevel().getServer()).reset(APOTHECARY.toString());
            Mob apothecary = staffed(helper, post(helper, "apothecary"));
            vault(first).depositGalleons(40);
            vault(second).depositGalleons(40);
            LocationProgression.openAtHost(first, apothecary);
            LocationProgression.openAtHost(second, apothecary);
            int shelf = line(first, APOTHECARY, "dittany").count();
            check(helper, shelf > 0, () -> "a shelf that is always carried is empty");
            check(helper, line(second, APOTHECARY, "dittany").count() == shelf, () -> "two shelves for one shop");
            if (shelf > 1) {
                check(helper, MarketTrade.buy(first, APOTHECARY, "dittany", shelf - 1) == MarketTrade.Result.DONE,
                        () -> "could not buy down the shelf");
            }
            check(helper, line(second, APOTHECARY, "dittany").count() == 1, () -> "a shelf not shared by its customers");
            check(helper, MarketTrade.buy(first, APOTHECARY, "dittany", 1) == MarketTrade.Result.DONE,
                    () -> "the last unit was refused");
            long before = vault(second).getTotalInKnuts();
            check(helper, MarketTrade.buy(second, APOTHECARY, "dittany", 1) == MarketTrade.Result.SOLD_OUT,
                    () -> "the shelf sold a unit it did not have");
            check(helper, vault(second).getTotalInKnuts() == before, () -> "charged for nothing");
            MarketLine sold = line(second, APOTHECARY, "dittany");
            check(helper, sold.count() == 0 && !sold.available(), () -> "the board still offers it");
            apothecary.discard();
            helper.succeed();
        } finally {
            retire(helper, first);
            retire(helper, second);
        }
    }

    private static void sharedPrices(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "PriceWatcher");
        try {
            ServerLevel level = helper.getLevel();
            MarketBook.get(level.getServer()).reset(APOTHECARY.toString());
            Mob apothecary = staffed(helper, post(helper, "apothecary"));
            vault(player).depositGalleons(40);
            LocationProgression.openAtHost(player, apothecary);
            String here = apothecary.getData(ModAttachments.SETTLEMENT_POST.get()).site();
            SettlementData data = SettlementData.get(level);
            NpcRole.Shop shop = NpcRoles.get(id("apothecary").toString()).shop().orElseThrow();
            data.site("test@elsewhere", 12345L);
            SettlementShops.Counter elsewhere = new SettlementShops.Counter(level, "test@elsewhere", 12345L, APOTHECARY, shop);
            int fullElsewhere = elsewhere.left("dittany");
            long priceBefore = line(player, APOTHECARY, "dittany").unitKnuts();
            int shelfBefore = line(player, APOTHECARY, "dittany").count();
            check(helper, MarketTrade.buy(player, APOTHECARY, "dittany", 4) == MarketTrade.Result.DONE, () -> "refused");
            check(helper, line(player, APOTHECARY, "dittany").count() == shelfBefore - 4, () -> "this shelf did not fall");
            check(helper, elsewhere.left("dittany") == fullElsewhere, () -> "another hamlet's shelf fell too");
            check(helper, line(player, APOTHECARY, "dittany").unitKnuts() > priceBefore, () -> "the shared price did not move");
            check(helper, data.site(here) != null, () -> "site lost");
            data.forget("test@elsewhere");
            apothecary.discard();
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void tellerRoundTrip(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "PathWalker");
        try {
            MarketBook.get(helper.getLevel().getServer()).reset(APOTHECARY.toString());
            Mob teller = staffed(helper, post(helper, "teller"));
            check(helper, teller instanceof GoblinTellerEntity, () -> "the bank is kept by " + teller);
            check(helper, LocationPresence.hostedBy(teller) != null, () -> "a post's teller keeps no location");
            player.getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), 7));
            GringottsCounter.admit(player, (GoblinTellerEntity) teller);
            VaultActionC2SPayload.perform(player, new VaultActionC2SPayload(VaultActionC2SPayload.Action.DEPOSIT_SICKLE.ordinal(), 7));
            check(helper, CareerProgression.carried(player, CurrencyItemRegistry.SICKLE.get()) == 0, () -> "coins not taken");
            check(helper, vault(player).getTotalInKnuts() == 7 * 29, () -> "vault holds " + vault(player).getTotalInKnuts());
            teller.discard();

            Mob apothecary = staffed(helper, post(helper, "apothecary"));
            LocationProgression.openAtHost(player, apothecary);
            check(helper, MarketTrade.buy(player, APOTHECARY, "dittany", 1) == MarketTrade.Result.DONE,
                    () -> "Path coin could not buy at a shop after the counter");
            apothecary.discard();

            Mob again = staffed(helper, post(helper, "teller"));
            GringottsCounter.admit(player, (GoblinTellerEntity) again);
            long held = vault(player).getTotalInKnuts();
            VaultActionC2SPayload.perform(player, new VaultActionC2SPayload(VaultActionC2SPayload.Action.WITHDRAW_SICKLE.ordinal(), 1));
            check(helper, CareerProgression.carried(player, CurrencyItemRegistry.SICKLE.get()) == 1, () -> "no coin came back");
            check(helper, vault(player).getTotalInKnuts() == held - 29, () -> "withdrawal did not debit the vault");
            again.discard();
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void wandmaker(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Hopeful");
        ServerPlayer goblin = customer(helper, "GoblinCustomer");
        try {
            Mob wandmaker = staffed(helper, post(helper, "wandmaker"));
            check(helper, OLLIVANDERS.equals(LocationPresence.hostedBy(wandmaker)), () -> "keeps " + LocationPresence.hostedBy(wandmaker));
            LocationProgression.openAtHost(player, wandmaker);
            check(helper, LocationProgression.useService(player, OLLIVANDERS, id("wand_trial")) == LocationProgression.Result.DONE,
                    () -> "the trial was refused");
            check(helper, player.containerMenu instanceof OllivanderTrialMenu, () -> "open: " + player.containerMenu);

            HeritageAPI.commit(goblin, Heritage.GOBLIN, HeritageVariant.HALF_BLOOD);
            check(helper, LocationProgression.openAtHost(goblin, wandmaker), () -> "a goblin was shown no shop at all");
            check(helper, !(goblin.containerMenu instanceof OllivanderTrialMenu), () -> "a forbidden trial opened");
            LocationView view = LocationProgression.view(goblin, OLLIVANDERS);
            check(helper, view.services().stream().anyMatch(s -> s.id().equals(id("wand_trial")) && !s.available()),
                    () -> "the board offered a goblin the trial");
            check(helper, view.services().stream().anyMatch(s -> s.available()), () -> "a goblin was refused every service");
            wandmaker.discard();
            helper.succeed();
        } finally {
            retire(helper, player);
            retire(helper, goblin);
        }
    }

    private static void arrival(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Wanderer", id, GameType.SURVIVAL);
        ServerPlayer other = customer(helper, "SomeoneElse");
        try {
            SettlementData.get(helper.getLevel()).site("test@arrival", 7L);
            SettlementDiscovery.arrive(player, "test@arrival");
            SettlementDiscovery.arrive(player, "test@arrival");
            check(helper, player.getData(ModAttachments.KNOWN_PLACES.get()).sites().size() == 1, () -> "recorded twice");
            check(helper, other.getData(ModAttachments.KNOWN_PLACES.get()).sites().isEmpty(), () -> "shared between players");
            helper.getLevel().getServer().getPlayerList().saveAll();
            retire(helper, player);
            ServerPlayer back = WizardTestSupport.placeMockPlayer(helper, "Wanderer", id, GameType.SURVIVAL);
            check(helper, back.getData(ModAttachments.KNOWN_PLACES.get()).has("test@arrival"), () -> "lost on reconnect");
            SettlementData.get(helper.getLevel()).forget("test@arrival");
            retire(helper, back);
            helper.succeed();
        } finally {
            retire(helper, other);
        }
    }

    private static void noMuggles(GameTestHelper helper) {
        SettlementNpcEntity npc = helper.spawn(at.koopro.wizardsandbeasts.registry.ModEntities.SETTLEMENT_NPC.get(), new BlockPos(1, 1, 1));
        check(helper, !CastSurvey.isMuggle(npc), () -> "a hamlet's resident counts as a Muggle");
        npc.discard();
        helper.succeed();
    }
}
