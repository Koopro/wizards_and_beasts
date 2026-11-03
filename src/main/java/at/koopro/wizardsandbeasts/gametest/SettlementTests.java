package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
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
import at.koopro.wizardsandbeasts.settlement.HamletCharts;
import at.koopro.wizardsandbeasts.settlement.SettlementData;
import at.koopro.wizardsandbeasts.treasure.SiteSearch;
import at.koopro.wizardsandbeasts.settlement.SettlementDiscovery;
import at.koopro.wizardsandbeasts.settlement.npc.NpcTalk;
import at.koopro.wizardsandbeasts.settlement.npc.NpcRole;
import at.koopro.wizardsandbeasts.settlement.npc.NpcRoles;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import at.koopro.wizardsandbeasts.settlement.post.PostRules;
import at.koopro.wizardsandbeasts.settlement.post.SettlementPostBlockEntity;
import at.koopro.wizardsandbeasts.settlement.shop.SettlementShops;
import at.koopro.wizardsandbeasts.settlement.shop.ShelfRules;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementGround;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementRooms;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementSite;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementStructure;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
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
        tests.addAlone("settlement_chart_is_given_once_however_often_it_is_offered",
                "settlement: two offers during one search, and one after it, end in one chart",
                SettlementTests::chartGivenOnce);
        tests.addAlone("settlement_chart_search_waits_on_the_world_a_cell_a_tick",
                "settlement: while a cell is undecided the search asks once a tick, and the server keeps ticking",
                SettlementTests::chartWaitsOnTheWorld);
        tests.addAlone("settlement_chart_search_ends_with_a_logout",
                "settlement: a search whose player left gives nothing to anyone, and they can be offered it again",
                SettlementTests::chartEndsAtLogout);
        tests.addAlone("settlement_lethal_blow_apparates_them_away",
                "settlement: a lethal blow drops nothing and leaves no body; they stay away until their time, and the "
                        + "same person returns",
                SettlementTests::lethalBlow);
        tests.addAlone("settlement_back_at_dawn_as_the_day_runs",
                "settlement: struck before dawn, they stay away while the day runs and their post brings them back at "
                        + "dawn by itself, long before the record's game time",
                SettlementTests::backAtDawnAsTheDayRuns);
        tests.addAlone("settlement_back_at_the_dawn_slept_to",
                "settlement: struck at night, they are back at the dawn a bed sleep skips to, with almost no game time passed",
                SettlementTests::backAtTheDawnSleptTo);
        tests.addAlone("settlement_two_away_return_once_each",
                "settlement: two people struck together both return at dawn, each as themselves and once, and the shelves "
                        + "stay as they were",
                SettlementTests::twoAwayReturnOnceEach);
        tests.addAlone("settlement_away_while_the_hamlet_is_unloaded",
                "settlement: a post unloaded while its person is away brings nobody meanwhile, and its person once when "
                        + "loaded again after dawn",
                SettlementTests::awayWhileUnloaded);
        tests.addAlone("settlement_blow_and_return_three_times",
                "settlement: three blows and three dawns: one person each time, the same name, a new body, nothing dropped",
                SettlementTests::blowAndReturnThreeTimes);
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
        tests.addAlone("settlement_a_purchase_survives_a_reconnect_whole",
                "settlement: a reconnect after buying keeps the goods and the charge, gives neither twice, and the counter "
                        + "must be spoken to again",
                SettlementTests::purchaseAcrossReconnect);
        tests.addAlone("settlement_teller_pays_only_what_is_held",
                "settlement: depositing more coin than is carried, or withdrawing more than is held, makes no money",
                SettlementTests::noMoneyFromNothing);
        tests.addAlone("settlement_teller_takes_every_click_of_a_burst",
                "settlement: 36 counter clicks run in one tick, as a lagging server runs them, are each handled once and "
                        + "the coin is conserved",
                SettlementTests::tellerBurst);
        tests.addAlone("settlement_teller_refuses_past_its_budget_and_says_so",
                "settlement: clicks past a tick's budget are refused with one message, move nothing, and a retry on the "
                        + "next tick is handled",
                SettlementTests::tellerBudget);
        tests.addAlone("settlement_teller_serves_two_players_at_once",
                "settlement: two players clicking in the same tick at one teller are both served whole, each conserved",
                SettlementTests::tellerTwoAtOnce);
        tests.addAlone("settlement_teller_answers_what_it_cannot_do",
                "settlement: a withdrawal the vault cannot pay and a deposit of coin not carried are answered and move nothing",
                SettlementTests::tellerAnswers);
        tests.addAlone("settlement_teller_across_a_disconnect_and_a_retry",
                "settlement: clicks before a disconnect count once; a retry after reconnecting is refused aloud until the "
                        + "teller is spoken to again",
                SettlementTests::tellerReconnect);
        tests.addAlone("settlement_a_trade_refreshes_every_board_at_the_counter",
                "settlement: a purchase sends a fresh board to everyone else at the counter, each with their own unlocks, "
                        + "and none to a player who is not there",
                SettlementTests::tradeRefreshesBoards);
        tests.addAlone("settlement_board_takes_a_burst_and_answers_the_rest",
                "settlement: board presses in one tick are handled up to the budget and the rest refused with one message",
                SettlementTests::boardBurst);
        tests.addAlone("settlement_unlocks_stay_with_the_player",
                "settlement: one player's standing unlocks a good for them and not for the next at the same shelf",
                SettlementTests::unlocksArePersonal);
        tests.addAlone("settlement_assembly_holds_at_every_turn",
                "settlement: jigsaw's own assembly, at all four turns of the plaza: every role once, connectors meet face "
                        + "to face, pieces never overlap, and the Undercroft is alone in its own piece",
                SettlementTests::assembly);
        tests.addAlone("settlement_gives_way_to_other_structures",
                "settlement: on real terrain, the sites the world probe found under a portal, an outpost, trail ruins or a "
                        + "Chamber are refused, and free sites nearby in the same worlds still stand",
                SettlementTests::givesWay);
        for (Rotation rotation : Rotation.values()) {
            String name = rotation.name().toLowerCase(java.util.Locale.ROOT);
            tests.addAlone("settlement_no_monster_room_breaches_the_undercroft_" + name,
                    "settlement: with no settlement reference a monster room opens the Undercroft's shell; in a settlement's "
                            + "chunks none starts, and every shell block stands",
                    helper -> monsterRoom(helper, rotation));
            tests.addAlone("settlement_floor_is_footed_over_a_gap_" + name,
                    "settlement: a floor over nothing gets its plate and piers, and a gap below stays a gap",
                    helper -> footing(helper, rotation));
            tests.addAlone("settlement_rooms_are_cleared_of_vegetation_" + name,
                    "settlement: a crown that grew into a room is gone once the chunk is full, and a branch over a yard stays",
                    helper -> rooms(helper, rotation));
            for (Terrain terrain : Terrain.values()) {
                tests.addAlone("settlement_stands_on_" + terrain.name().toLowerCase(java.util.Locale.ROOT) + "_" + name,
                        "settlement: the whole hamlet, placed for real, over " + terrain.description,
                        helper -> stands(helper, terrain, rotation));
            }
            tests.addAlone("settlement_an_old_start_dries_its_cellar_" + name,
                    "settlement: a start saved before the liquid fix floods its cellar on wet ground; the pass over each newly "
                            + "generated chunk leaves no template block holding water",
                    helper -> oldStartDries(helper, rotation));
        }
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

    /** A walk of one cell that keeps the world deciding for {@code waits} asks, then finds a site, counting each ask. */
    private static SiteSearch.Walk slowWalk(int waits, int[] asks) {
        return new SiteSearch.Walk(0, 0, 40, 0, (grid, x, z) -> ++asks[0] <= waits ? SiteSearch.PENDING
                : new SiteSearch.Site("wizards_and_beasts:test", 0, 0, 8, 8));
    }

    private static int charts(ServerPlayer player) {
        return player.getInventory().countItem(Items.FILLED_MAP);
    }

    private static void chartGivenOnce(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "ChartedOnce");
        int[] asks = {0};
        int[] later = {0};
        HamletCharts.begin(player, slowWalk(5, asks));
        HamletCharts.begin(player, slowWalk(5, later));
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    check(helper, charts(player) == 1, () -> "charts after the search: " + charts(player));
                    check(helper, player.getData(ModAttachments.KNOWN_PLACES.get()).charted(), () -> "not recorded as charted");
                    check(helper, later[0] == 0, () -> "a second search ran alongside the first");
                    HamletCharts.begin(player, slowWalk(0, later));
                })
                .thenExecuteAfter(5, () -> {
                    check(helper, later[0] == 0, () -> "a charted player was searched for again");
                    check(helper, charts(player) == 1, () -> "charts after a later offer: " + charts(player));
                    retire(helper, player);
                })
                .thenSucceed();
    }

    private static void chartWaitsOnTheWorld(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "ChartedSlowly");
        int[] asks = {0};
        long began = helper.getLevel().getGameTime();
        HamletCharts.begin(player, slowWalk(Integer.MAX_VALUE, asks));
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    long passed = helper.getLevel().getGameTime() - began;
                    check(helper, asks[0] >= 15 && asks[0] <= passed + 1, () -> asks[0] + " asks in " + passed + " ticks");
                    check(helper, charts(player) == 0, () -> "a chart from a search that has found nothing");
                    retire(helper, player);
                })
                .thenSucceed();
    }

    private static void chartEndsAtLogout(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "ChartedAway", id, GameType.SURVIVAL);
        int[] asks = {0};
        ServerPlayer[] back = new ServerPlayer[1];
        HamletCharts.begin(player, slowWalk(5, asks));
        helper.startSequence()
                .thenExecuteAfter(2, () -> retire(helper, player))
                .thenExecuteAfter(10, () -> {
                    int stopped = asks[0];
                    check(helper, stopped <= 3, () -> "the search ran on after the logout: " + stopped + " asks");
                    back[0] = WizardTestSupport.placeMockPlayer(helper, "ChartedAway", id, GameType.SURVIVAL);
                    check(helper, charts(back[0]) == 0, () -> "a chart for a search that was dropped");
                    check(helper, !back[0].getData(ModAttachments.KNOWN_PLACES.get()).charted(), () -> "charted by a dropped search");
                    HamletCharts.begin(back[0], slowWalk(0, new int[1]));
                })
                .thenExecuteAfter(5, () -> {
                    check(helper, charts(back[0]) == 1, () -> "the offer did not stand for the next login");
                    retire(helper, back[0]);
                })
                .thenSucceed();
    }

    private static java.util.List<String> itemsSeen(java.util.List<ItemEntity> items) {
        return items.stream().map(e -> e.getItem() + " age " + e.getAge() + " removed " + e.isRemoved()
                + " at " + e.position()).toList();
    }

    private static void lethalBlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SettlementPostBlockEntity post = post(helper, "keeper");
        Mob keeper = staffed(helper, post);
        String name = keeper.getCustomName().getString();
        // Counted, not assumed empty: a neighbouring scenario's items can lie about.
        AABB around = new AABB(helper.absolutePos(POST)).inflate(6.0);
        java.util.List<ItemEntity> itemsBefore = level.getEntitiesOfClass(ItemEntity.class, around);
        keeper.hurtServer(level, level.damageSources().magic(), 1000f);
        check(helper, keeper.isRemoved(), () -> "they stayed after a lethal blow");
        java.util.List<ItemEntity> itemsAfter = level.getEntitiesOfClass(ItemEntity.class, around);
        check(helper, itemsAfter.size() == itemsBefore.size(), () -> "something dropped: before " + itemsSeen(itemsBefore)
                + " after " + itemsSeen(itemsAfter));
        SettlementData.Post away = record(helper);
        check(helper, away.status() == PostRules.Status.AWAY && away.backAt() > level.getGameTime(),
                () -> "record after the blow: " + away);
        post.lookLoaded(level, helper.absolutePos(POST));
        check(helper, around(helper).isEmpty(), () -> "back before their time");
        SettlementData.get(level).setPost(ref(helper), away.away(level.getGameTime(), away.dawn()));
        Mob back = staffed(helper, post);
        check(helper, back.getCustomName().getString().equals(name), () -> "came back as someone else");
        check(helper, !back.getUUID().equals(keeper.getUUID()), () -> "the old body came back");
        back.discard();
        helper.succeed();
    }

    // -- back at dawn ----------------------------------------------------------------------------------

    private static final long DAY = 24000L;

    /** The next day's {@code dayTime}, so a scenario never sets the clock back past the dawn of a record it made. */
    private static long nextDayAt(ServerLevel level, long dayTime) {
        return (level.getDayTime() / DAY + 1) * DAY + dayTime;
    }

    /**
     * Everyone keeping one of {@code posts}, the first of which they are near: someone a post brought back strolls, so the
     * reach is wide, and posts other scenarios left on this plot staff themselves, so only these posts' people count.
     */
    private static List<Entity> keepers(ServerLevel level, BlockPos... posts) {
        java.util.Set<Long> ours = java.util.Arrays.stream(posts).map(BlockPos::asLong).collect(java.util.stream.Collectors.toSet());
        return level.getEntities((Entity) null, new AABB(posts[0]).inflate(24.0),
                entity -> entity.hasData(ModAttachments.SETTLEMENT_POST.get())
                        && ours.contains(entity.getData(ModAttachments.SETTLEMENT_POST.get()).post()));
    }

    private static SettlementData.Post recordAt(ServerLevel level, BlockPos post) {
        return SettlementData.get(level).post(new PostRef(SettlementData.siteKey(level, new ChunkPos(post)), post.asLong()));
    }

    /** Puts the clock back as it was: day time, its speed, and how many must sleep. */
    private static Runnable clock(ServerLevel level) {
        long day = level.getDayTime();
        float rate = level.getDayTimePerTick();
        int sleepers = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        return () -> {
            level.setDayTimePerTick(rate < 0f ? -1f : rate);
            level.setDayTime(day);
            level.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, sleepers, level.getServer());
        };
    }

    /** Runs {@code step}; if it fails, {@code restore} runs first, so a failed scenario leaves the server's clock alone. */
    private static Runnable guarded(Runnable restore, Runnable step) {
        return () -> {
            try {
                step.run();
            } catch (RuntimeException | Error e) {
                restore.run();
                throw e;
            }
        };
    }

    private static String strike(ServerLevel level, Mob person) {
        String name = person.getCustomName().getString();
        person.hurtServer(level, level.damageSources().magic(), 1000f);
        return name;
    }

    /**
     * The defect of 2026-11-03 had the dawn as a game time. The blow falls at the vanilla speed, so that game time is 3,000
     * ticks off; then the day runs 25 times faster, and dawn comes in 120. Only a return keyed to the day passes.
     */
    private static void backAtDawnAsTheDayRuns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Runnable restore = clock(level);
        // The post looks by itself, as in play, and only with every chunk around it ticking entities.
        BlockPos min = POST.offset(-16, 0, -16);
        BlockPos max = POST.offset(16, 0, 16);
        WizardTestSupport.forceChunks(helper, min, max);
        SettlementPostBlockEntity post = post(helper, "keeper");
        BlockPos at = helper.absolutePos(POST);
        String[] name = new String[1];
        long[] due = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min, max))
                .thenExecute(guarded(restore, () -> {
                    Mob keeper = staffed(helper, post);
                    level.setDayTime(nextDayAt(level, 21_000));
                    name[0] = strike(level, keeper);
                    SettlementData.Post away = record(helper);
                    check(helper, away.status() == PostRules.Status.AWAY && away.dawn() == nextDayAt(level, 0),
                            () -> "record after the blow at day time " + level.getDayTime() + ": " + away);
                    check(helper, away.backAt() - level.getGameTime() >= 3000,
                            () -> "setup: the game-time backstop is only " + (away.backAt() - level.getGameTime()) + " away");
                    due[0] = away.dawn();
                    due[1] = away.backAt();
                    level.setDayTimePerTick(25f);
                }))
                .thenExecuteAfter(100, guarded(restore, () -> {
                    check(helper, level.getDayTime() < due[0], () -> "setup: dawn came early, day time " + level.getDayTime());
                    check(helper, keepers(level, at).isEmpty(), () -> "back before dawn, at day time " + level.getDayTime() % DAY);
                    check(helper, record(helper).status() == PostRules.Status.AWAY, () -> "record before dawn: " + record(helper));
                }))
                .thenExecuteAfter(70, guarded(restore, () -> {
                    check(helper, level.getDayTime() >= due[0] + 500, () -> "setup: not yet dawn, day time " + level.getDayTime());
                    check(helper, level.getGameTime() < due[1], () -> "setup: the backstop has passed, so this proves nothing");
                    List<Entity> back = keepers(level, at);
                    check(helper, back.size() == 1 && back.get(0).getCustomName().getString().equals(name[0]),
                            () -> "after dawn the post holds " + back.stream().map(e -> e.getName().getString()).toList()
                                    + ", record " + record(helper));
                    check(helper, record(helper).status() == PostRules.Status.PRESENT, () -> "record after dawn: " + record(helper));
                    back.forEach(Entity::discard);
                    restore.run();
                }))
                .thenSucceed();
    }

    /** A night slept through in a real bed: vanilla moves day time to dawn and leaves game time where it was. */
    private static void backAtTheDawnSleptTo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer sleeper = customer(helper, "Sleeper");
        Runnable clockBack = clock(level);
        Runnable restore = () -> {
            clockBack.run();
            retire(helper, sleeper);
        };
        long[] struck = new long[3];
        String[] name = new String[1];
        SettlementPostBlockEntity post = post(helper, "keeper");
        guarded(restore, () -> {
            Mob keeper = staffed(helper, post);
            level.setDayTime(nextDayAt(level, 14_000));
            name[0] = strike(level, keeper);
            struck[0] = level.getGameTime();
            struck[1] = record(helper).dawn();
            struck[2] = record(helper).backAt();
        }).run();
        // A mock player has no connection to tick it. Ticking it here is what counts its sleep, as play would.
        helper.onEachTick(() -> {
            if (sleeper.isSleeping()) {
                sleeper.doTick();
            }
        });
        helper.startSequence()
                // A tick for the sky to darken: a bed reads the night from it, not from the clock.
                .thenExecuteAfter(2, guarded(restore, () -> {
                    BlockPos foot = helper.absolutePos(new BlockPos(4, 1, 0));
                    BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
                    level.setBlockAndUpdate(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
                    level.setBlockAndUpdate(foot.south(), bed.setValue(BedBlock.PART, BedPart.HEAD));
                    sleeper.teleportTo(foot.getX() - 0.5, foot.getY(), foot.getZ() + 0.5);
                    // Only this sleeper is needed, whoever a previous scenario left on the server.
                    level.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 0, level.getServer());
                    var slept = sleeper.startSleepInBed(foot.south());
                    check(helper, slept.right().isPresent() && sleeper.isSleeping(),
                            () -> "the bed refused at day time " + level.getDayTime() % DAY + ": " + slept.left());
                }))
                .thenExecuteAfter(115, guarded(restore, () -> {
                    long passed = level.getGameTime() - struck[0];
                    check(helper, !sleeper.isSleeping(), () -> "setup: still asleep at day time " + level.getDayTime());
                    check(helper, level.getDayTime() >= struck[1] && level.getDayTime() < struck[1] + 200,
                            () -> "setup: woke at day time " + level.getDayTime() + ", dawn was " + struck[1]);
                    // Game time alone had 10,000 ticks to go: a return now is the sleep's doing.
                    check(helper, passed < 200 && struck[2] - level.getGameTime() > 5000,
                            () -> "setup: " + passed + " game ticks passed, backstop at " + struck[2]);
                    // The post may already have looked by itself since the dawn; one more look must not add anyone.
                    post.lookLoaded(level, helper.absolutePos(POST));
                    Entity back = person(helper);
                    check(helper, back != null && back.getCustomName().getString().equals(name[0]),
                            () -> "not back at the dawn slept to: record " + record(helper));
                    List<Entity> here = keepers(level, helper.absolutePos(POST));
                    check(helper, here.size() == 1, () -> "the post holds " + here.size());
                    back.discard();
                    restore.run();
                }))
                .thenSucceed();
    }

    private static void twoAwayReturnOnceEach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Runnable restore = clock(level);
        BlockPos keeperAt = helper.absolutePos(POST);
        // Plots move between runs; four blocks along, towards the chunk's middle, is the same chunk wherever it lands.
        BlockPos scholarAt = keeperAt.east((keeperAt.getX() & 15) < 8 ? 4 : -4);
        String site = SettlementData.siteKey(level, new ChunkPos(keeperAt));
        guarded(restore, () -> {
            check(helper, new ChunkPos(keeperAt).equals(new ChunkPos(scholarAt)), () -> "setup: the two posts straddle a chunk");
            SettlementPostBlockEntity keeperPost = post(helper, "keeper");
            level.setBlockAndUpdate(scholarAt, ModBlocks.SETTLEMENT_POST.get().defaultBlockState());
            SettlementPostBlockEntity scholarPost = (SettlementPostBlockEntity) level.getBlockEntity(scholarAt);
            scholarPost.setRole(id("scholar").toString());
            keeperPost.lookLoaded(level, keeperAt);
            scholarPost.lookLoaded(level, scholarAt);
            SettlementData.Shelf shelf = new SettlementData.Shelf(3, level.getGameTime());
            SettlementData.get(level).setShelf(site, APOTHECARY + "|dittany", shelf);
            List<Entity> before = keepers(level, keeperAt, scholarAt);
            check(helper, before.size() == 2, () -> "setup: two posts brought " + before.size());
            Map<Long, String> names = new java.util.HashMap<>();
            for (BlockPos at : List.of(keeperAt, scholarAt)) {
                Mob person = (Mob) level.getEntity(recordAt(level, at).npc().orElseThrow());
                person.setNoAi(true);
                names.put(at.asLong(), person.getCustomName().getString());
            }
            level.setDayTime(nextDayAt(level, 18_000));
            for (Entity person : before) {
                strike(level, (Mob) person);
            }
            check(helper, keepers(level, keeperAt, scholarAt).isEmpty(), () -> "someone stayed after the blows");
            check(helper, recordAt(level, keeperAt).status() == PostRules.Status.AWAY
                    && recordAt(level, scholarAt).status() == PostRules.Status.AWAY, () -> "not both away");
            keeperPost.lookLoaded(level, keeperAt);
            scholarPost.lookLoaded(level, scholarAt);
            check(helper, keepers(level, keeperAt, scholarAt).isEmpty(), () -> "back before dawn");
            level.setDayTime(recordAt(level, keeperAt).dawn());
            for (int look = 0; look < 3; look++) {
                keeperPost.lookLoaded(level, keeperAt);
                scholarPost.lookLoaded(level, scholarAt);
            }
            List<Entity> back = keepers(level, keeperAt, scholarAt);
            check(helper, back.size() == 2, () -> "after dawn and three looks each the posts hold " + back.size());
            for (BlockPos at : List.of(keeperAt, scholarAt)) {
                Entity person = level.getEntity(recordAt(level, at).npc().orElseThrow());
                check(helper, person != null && person.getCustomName().getString().equals(names.get(at.asLong())),
                        () -> "post " + at.toShortString() + " brought " + person + ", was " + names.get(at.asLong()));
            }
            check(helper, shelf.equals(SettlementData.get(level).site(site).shelves().get(APOTHECARY + "|dittany")),
                    () -> "the shelf changed: " + SettlementData.get(level).site(site).shelves());
            back.forEach(Entity::discard);
            level.setBlockAndUpdate(scholarAt, Blocks.AIR.defaultBlockState());
            restore.run();
        }).run();
        helper.succeed();
    }

    /**
     * A post on a chunk of its own, far from the plot, so its chunks can be let go: nothing can look while they are
     * unloaded, and the first looks after they load again bring the person back, once.
     */
    private static void awayWhileUnloaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Runnable clockBack = clock(level);
        BlockPos at = helper.absolutePos(POST).offset(512, 0, 0);
        ChunkPos chunk = new ChunkPos(at);
        String site = SettlementData.siteKey(level, chunk);
        java.util.function.Consumer<Boolean> force = on -> ChunkPos.rangeClosed(
                new ChunkPos(chunk.x - 1, chunk.z - 1), new ChunkPos(chunk.x + 1, chunk.z + 1))
                .forEach(c -> level.setChunkForced(c.x, c.z, on));
        Runnable restore = () -> {
            clockBack.run();
            if (level.isLoaded(at)) {
                keepers(level, at).forEach(Entity::discard);
                level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            }
            force.accept(false);
            SettlementData.get(level).forget(site);
        };
        String[] name = new String[1];
        force.accept(true);
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, level.isPositionEntityTicking(at), () -> "setup: the far chunk never ticked"))
                .thenExecute(guarded(restore, () -> {
                    SettlementData.get(level).forget(site);
                    level.setBlockAndUpdate(at, ModBlocks.SETTLEMENT_POST.get().defaultBlockState());
                    SettlementPostBlockEntity post = (SettlementPostBlockEntity) level.getBlockEntity(at);
                    post.setRole(id("keeper").toString());
                    post.lookLoaded(level, at);
                    Mob keeper = (Mob) level.getEntity(recordAt(level, at).npc().orElseThrow());
                    keeper.setNoAi(true);
                    level.setDayTime(nextDayAt(level, 16_000));
                    name[0] = strike(level, keeper);
                    check(helper, recordAt(level, at).status() == PostRules.Status.AWAY, () -> "not away: " + recordAt(level, at));
                    force.accept(false);
                }))
                // A positive control: the scenario means nothing unless the chunk really went.
                .thenWaitUntil(() -> check(helper, !level.areEntitiesLoaded(chunk.toLong()), () -> "setup: the chunk never unloaded"))
                .thenExecute(guarded(restore, () -> level.setDayTime(recordAt(level, at).dawn() + 100)))
                .thenExecuteAfter(40, guarded(restore, () -> {
                    check(helper, recordAt(level, at).status() == PostRules.Status.AWAY,
                            () -> "something brought them back while unloaded: " + recordAt(level, at));
                    force.accept(true);
                }))
                // Entities come back from disk after the chunk itself, and a post judges nothing until all nine have.
                .thenWaitUntil(() -> check(helper, ChunkPos.rangeClosed(new ChunkPos(chunk.x - 1, chunk.z - 1),
                        new ChunkPos(chunk.x + 1, chunk.z + 1)).allMatch(c -> level.areEntitiesLoaded(c.toLong())),
                        () -> "setup: the chunks' entities never came back"))
                .thenExecuteAfter(45, guarded(restore, () -> {
                    List<Entity> back = keepers(level, at);
                    check(helper, back.size() == 1 && back.get(0).getCustomName().getString().equals(name[0]),
                            () -> "after reloading past dawn the post holds " + back.stream().map(e -> e.getName().getString()).toList()
                                    + ", record " + recordAt(level, at) + ", day " + level.getDayTime() + ", game " + level.getGameTime()
                                    + ", post " + (level.getBlockEntity(at) instanceof SettlementPostBlockEntity found
                                    ? "role '" + found.role() + "'" : String.valueOf(level.getBlockState(at)))
                                    + ", block ticking " + level.isPositionEntityTicking(at));
                    check(helper, recordAt(level, at).status() == PostRules.Status.PRESENT, () -> "record: " + recordAt(level, at));
                    restore.run();
                }))
                .thenSucceed();
    }

    private static void blowAndReturnThreeTimes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Runnable restore = clock(level);
        guarded(restore, () -> {
            SettlementPostBlockEntity post = post(helper, "keeper");
            Mob keeper = staffed(helper, post);
            String name = keeper.getCustomName().getString();
            String site = ref(helper).site();
            SettlementData.Shelf shelf = new SettlementData.Shelf(2, level.getGameTime());
            SettlementData.get(level).setShelf(site, APOTHECARY + "|dittany", shelf);
            AABB near = new AABB(helper.absolutePos(POST)).inflate(6.0);
            int items = level.getEntitiesOfClass(ItemEntity.class, near).size();
            java.util.Set<UUID> bodies = new java.util.HashSet<>();
            bodies.add(keeper.getUUID());
            for (int round = 1; round <= 3; round++) {
                int r = round;
                level.setDayTime(nextDayAt(level, 15_000));
                strike(level, keeper);
                check(helper, keeper.isRemoved() && record(helper).status() == PostRules.Status.AWAY,
                        () -> "round " + r + ": not away after the blow, record " + record(helper));
                post.lookLoaded(level, helper.absolutePos(POST));
                check(helper, around(helper).isEmpty(), () -> "round " + r + ": back before dawn");
                level.setDayTime(record(helper).dawn());
                keeper = staffed(helper, post);
                post.lookLoaded(level, helper.absolutePos(POST));
                Mob now = keeper;
                check(helper, now.getCustomName().getString().equals(name), () -> "round " + r + ": came back as " + now.getCustomName());
                check(helper, bodies.add(now.getUUID()), () -> "round " + r + ": an old body came back");
                check(helper, around(helper).size() == 1, () -> "round " + r + ": the post holds " + around(helper).size());
            }
            int itemsAfter = level.getEntitiesOfClass(ItemEntity.class, near).size();
            check(helper, itemsAfter == items, () -> "items near the post went from " + items + " to " + itemsAfter);
            check(helper, shelf.equals(SettlementData.get(level).site(site).shelves().get(APOTHECARY + "|dittany")),
                    () -> "the shelf changed");
            keeper.discard();
            restore.run();
        }).run();
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

    private static void purchaseAcrossReconnect(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Returning", id, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        MarketBook.get(helper.getLevel().getServer()).reset(APOTHECARY.toString());
        Mob apothecary = staffed(helper, post(helper, "apothecary"));
        vault(player).depositGalleons(10);
        LocationProgression.openAtHost(player, apothecary);
        int shelf = line(player, APOTHECARY, "dittany").count();
        check(helper, MarketTrade.buy(player, APOTHECARY, "dittany", 3) == MarketTrade.Result.DONE, () -> "refused");
        long paid = vault(player).getTotalInKnuts();
        helper.getLevel().getServer().getPlayerList().saveAll();
        retire(helper, player);

        ServerPlayer back = WizardTestSupport.placeMockPlayer(helper, "Returning", id, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, back);
        try {
            check(helper, vault(back).getTotalInKnuts() == paid, () -> "the vault changed across the reconnect");
            int goods = CareerProgression.carried(back, BuiltInRegistries.ITEM.getValue(id("dittany")));
            check(helper, goods == 3, () -> "came back with " + goods);
            check(helper, MarketTrade.buy(back, APOTHECARY, "dittany", 1) == MarketTrade.Result.NOT_THERE,
                    () -> "bought without speaking to anyone after a reconnect");
            LocationProgression.openAtHost(back, apothecary);
            check(helper, line(back, APOTHECARY, "dittany").count() == shelf - 3, () -> "the shelf changed across the reconnect");
            apothecary.discard();
            helper.succeed();
        } finally {
            retire(helper, back);
        }
    }

    private static void noMoneyFromNothing(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Chancer");
        try {
            Mob teller = staffed(helper, post(helper, "teller"));
            GringottsCounter.admit(player, (GoblinTellerEntity) teller);
            player.getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), 5));
            VaultActionC2SPayload.perform(player, new VaultActionC2SPayload(VaultActionC2SPayload.Action.DEPOSIT_SICKLE.ordinal(), 50));
            long worth = vault(player).getTotalInKnuts()
                    + CareerProgression.carried(player, CurrencyItemRegistry.SICKLE.get()) * 29L;
            check(helper, worth == 5 * 29L, () -> "five Sickles became " + worth + " Knuts");
            long held = vault(player).getTotalInKnuts();
            int pocket = CareerProgression.carried(player, CurrencyItemRegistry.GALLEON.get());
            VaultActionC2SPayload.perform(player, new VaultActionC2SPayload(VaultActionC2SPayload.Action.WITHDRAW_GALLEON.ordinal(), 9));
            check(helper, vault(player).getTotalInKnuts() == held
                            && CareerProgression.carried(player, CurrencyItemRegistry.GALLEON.get()) == pocket,
                    () -> "a Galleon came out of a vault with none");
            teller.discard();
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    // -- the counter under load ------------------------------------------------------------------------

    private static VaultActionC2SPayload vaultAction(VaultActionC2SPayload.Action action, int amount) {
        return new VaultActionC2SPayload(action.ordinal(), amount);
    }

    /** Everything a player owns, vault and pocket, in Knuts: no counter action may change it. */
    private static long worth(ServerPlayer player) {
        return vault(player).getTotalInKnuts()
                + CareerProgression.carried(player, CurrencyItemRegistry.KNUT.get())
                + CareerProgression.carried(player, CurrencyItemRegistry.SICKLE.get()) * (long) CurrencyHelper.KNUTS_PER_SICKLE
                + CareerProgression.carried(player, CurrencyItemRegistry.GALLEON.get()) * (long) CurrencyHelper.KNUTS_PER_GALLEON;
    }

    private static long told(List<String> lines, String key) {
        return lines.stream().filter(key::equals).count();
    }

    private static GoblinTellerEntity teller(GameTestHelper helper) {
        Mob teller = staffed(helper, post(helper, "teller"));
        check(helper, teller instanceof GoblinTellerEntity, () -> "the bank is kept by " + teller);
        return (GoblinTellerEntity) teller;
    }

    /** The audit's run: 7 deposits and 29 withdrawals clicked during lag, all run in one tick. The 2-tick gap kept one. */
    private static void tellerBurst(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Hasty");
        try {
            GoblinTellerEntity teller = teller(helper);
            GringottsCounter.admit(player, teller);
            player.getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), 7));
            player.getInventory().add(new ItemStack(CurrencyItemRegistry.KNUT.get(), 40));
            vault(player).depositGalleons(2);
            long worth = worth(player);
            long held = vault(player).getTotalInKnuts();
            long tick = helper.getLevel().getGameTime();
            int handled = 0;
            for (int i = 0; i < 7; i++) {
                handled += VaultActionC2SPayload.submit(player, vaultAction(VaultActionC2SPayload.Action.DEPOSIT_SICKLE, 1)) ? 1 : 0;
            }
            for (int i = 0; i < 29; i++) {
                handled += VaultActionC2SPayload.submit(player, vaultAction(VaultActionC2SPayload.Action.WITHDRAW_KNUT, 1)) ? 1 : 0;
            }
            int all = handled;
            check(helper, helper.getLevel().getGameTime() == tick, () -> "setup: the burst spanned ticks");
            check(helper, all == 36, () -> "handled " + all + " of 36 clicks in one tick");
            check(helper, CareerProgression.carried(player, CurrencyItemRegistry.SICKLE.get()) == 0,
                    () -> "Sickles left in the pocket: " + CareerProgression.carried(player, CurrencyItemRegistry.SICKLE.get()));
            check(helper, CareerProgression.carried(player, CurrencyItemRegistry.KNUT.get()) == 69,
                    () -> "Knuts in the pocket: " + CareerProgression.carried(player, CurrencyItemRegistry.KNUT.get()));
            check(helper, vault(player).getTotalInKnuts() == held + 7L * CurrencyHelper.KNUTS_PER_SICKLE - 29,
                    () -> "vault " + vault(player).getTotalInKnuts() + ", was " + held);
            check(helper, worth(player) == worth, () -> "worth " + worth + " became " + worth(player));
            teller.discard();
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void tellerBudget(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Hammering");
        int budget = VaultActionC2SPayload.ACTIONS_PER_TICK;
        GoblinTellerEntity teller = teller(helper);
        GringottsCounter.admit(player, teller);
        vault(player).depositGalleons(100);
        long worth = worth(player);
        WizardTestSupport.drainClientbound(player);
        int handled = 0;
        int refused = 0;
        for (int i = 0; i < budget + 10; i++) {
            if (VaultActionC2SPayload.submit(player, vaultAction(VaultActionC2SPayload.Action.WITHDRAW_KNUT, 1))) {
                handled++;
            } else {
                refused++;
            }
        }
        int took = handled;
        int turned = refused;
        guarded(() -> retire(helper, player), () -> {
            check(helper, took == budget && turned == 10, () -> "handled " + took + ", refused " + turned);
            check(helper, CareerProgression.carried(player, CurrencyItemRegistry.KNUT.get()) == budget,
                    () -> "Knuts out: " + CareerProgression.carried(player, CurrencyItemRegistry.KNUT.get()));
            check(helper, worth(player) == worth, () -> "worth " + worth + " became " + worth(player));
            List<String> lines = WizardTestSupport.drainClientbound(player).lines();
            check(helper, told(lines, "gringotts.wizards_and_beasts.reject.busy") == 1,
                    () -> "the refusal was told " + told(lines, "gringotts.wizards_and_beasts.reject.busy") + " times: " + lines);
        }).run();
        helper.startSequence()
                .thenExecuteAfter(1, guarded(() -> retire(helper, player), () -> {
                    check(helper, VaultActionC2SPayload.submit(player, vaultAction(VaultActionC2SPayload.Action.WITHDRAW_KNUT, 1)),
                            () -> "a retry on the next tick was refused");
                    check(helper, CareerProgression.carried(player, CurrencyItemRegistry.KNUT.get()) == budget + 1,
                            () -> "the retry moved " + (CareerProgression.carried(player, CurrencyItemRegistry.KNUT.get()) - budget));
                    check(helper, worth(player) == worth, () -> "worth " + worth + " became " + worth(player));
                    teller.discard();
                    retire(helper, player);
                }))
                .thenSucceed();
    }

    private static void tellerTwoAtOnce(GameTestHelper helper) {
        ServerPlayer saver = customer(helper, "Saver");
        ServerPlayer spender = customer(helper, "Spender");
        try {
            GoblinTellerEntity teller = teller(helper);
            GringottsCounter.admit(saver, teller);
            GringottsCounter.admit(spender, teller);
            saver.getInventory().add(new ItemStack(CurrencyItemRegistry.KNUT.get(), 20));
            vault(spender).depositSickles(1);
            long saverWorth = worth(saver);
            long spenderWorth = worth(spender);
            for (int i = 0; i < 20; i++) {
                VaultActionC2SPayload.submit(saver, vaultAction(VaultActionC2SPayload.Action.DEPOSIT_KNUT, 1));
                VaultActionC2SPayload.submit(spender, vaultAction(VaultActionC2SPayload.Action.WITHDRAW_KNUT, 1));
            }
            check(helper, vault(saver).getTotalInKnuts() == 20 && CareerProgression.carried(saver, CurrencyItemRegistry.KNUT.get()) == 0,
                    () -> "the saver's vault " + vault(saver).getTotalInKnuts());
            check(helper, CareerProgression.carried(spender, CurrencyItemRegistry.KNUT.get()) == 20
                    && vault(spender).getTotalInKnuts() == CurrencyHelper.KNUTS_PER_SICKLE - 20,
                    () -> "the spender's pocket " + CareerProgression.carried(spender, CurrencyItemRegistry.KNUT.get()));
            check(helper, worth(saver) == saverWorth && worth(spender) == spenderWorth, () -> "coin was made or lost");
            teller.discard();
            helper.succeed();
        } finally {
            retire(helper, saver);
            retire(helper, spender);
        }
    }

    private static void tellerAnswers(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Hopeful");
        try {
            GoblinTellerEntity teller = teller(helper);
            GringottsCounter.admit(player, teller);
            vault(player).depositKnuts(5);
            long worth = worth(player);
            WizardTestSupport.drainClientbound(player);
            VaultActionC2SPayload.submit(player, vaultAction(VaultActionC2SPayload.Action.WITHDRAW_GALLEON, 3));
            VaultActionC2SPayload.submit(player, vaultAction(VaultActionC2SPayload.Action.DEPOSIT_SICKLE, 2));
            List<String> lines = WizardTestSupport.drainClientbound(player).lines();
            check(helper, told(lines, "gringotts.wizards_and_beasts.reject.insufficient") == 1,
                    () -> "an unpaid withdrawal was not answered: " + lines);
            check(helper, told(lines, "gringotts.wizards_and_beasts.reject.no_coins") == 1,
                    () -> "a deposit of nothing was not answered: " + lines);
            check(helper, worth(player) == worth && vault(player).getTotalInKnuts() == 5, () -> "something moved");
            teller.discard();
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void tellerReconnect(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Unsteady", id, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        GoblinTellerEntity teller = teller(helper);
        GringottsCounter.admit(player, teller);
        player.getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), 10));
        long worth = worth(player);
        for (int i = 0; i < 10; i++) {
            VaultActionC2SPayload.submit(player, vaultAction(VaultActionC2SPayload.Action.DEPOSIT_SICKLE, 1));
        }
        long held = vault(player).getTotalInKnuts();
        helper.getLevel().getServer().getPlayerList().saveAll();
        retire(helper, player);
        ServerPlayer back = WizardTestSupport.placeMockPlayer(helper, "Unsteady", id, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, back);
        try {
            check(helper, held == 10L * CurrencyHelper.KNUTS_PER_SICKLE, () -> "setup: ten deposits made " + held);
            check(helper, vault(back).getTotalInKnuts() == held && worth(back) == worth,
                    () -> "across the reconnect the vault went " + held + " -> " + vault(back).getTotalInKnuts()
                            + ", worth " + worth + " -> " + worth(back));
            WizardTestSupport.drainClientbound(back);
            VaultActionC2SPayload.submit(back, vaultAction(VaultActionC2SPayload.Action.WITHDRAW_SICKLE, 1));
            List<String> lines = WizardTestSupport.drainClientbound(back).lines();
            check(helper, told(lines, "gringotts.wizards_and_beasts.reject.not_at_counter") == 1,
                    () -> "a click after reconnecting was not answered: " + lines);
            check(helper, vault(back).getTotalInKnuts() == held, () -> "a click from off the counter moved coin");
            GringottsCounter.admit(back, teller);
            VaultActionC2SPayload.submit(back, vaultAction(VaultActionC2SPayload.Action.WITHDRAW_SICKLE, 1));
            check(helper, CareerProgression.carried(back, CurrencyItemRegistry.SICKLE.get()) == 1 && worth(back) == worth,
                    () -> "the retry at the counter: pocket " + CareerProgression.carried(back, CurrencyItemRegistry.SICKLE.get()));
            teller.discard();
            helper.succeed();
        } finally {
            retire(helper, back);
        }
    }

    private static at.koopro.wizardsandbeasts.network.location.LocationViewS2CPayload lastBoard(List<?> payloads) {
        at.koopro.wizardsandbeasts.network.location.LocationViewS2CPayload last = null;
        for (Object payload : payloads) {
            if (payload instanceof at.koopro.wizardsandbeasts.network.location.LocationViewS2CPayload board) {
                last = board;
            }
        }
        return last;
    }

    private static MarketLine lineOf(LocationView view, String good) {
        return view.market().stream().filter(l -> !l.buying() && l.good().equals(good)).findFirst().orElse(null);
    }

    /** Defect 5 of the alpha: after one player bought, the other's open board still showed the old shelf. */
    private static void tradeRefreshesBoards(GameTestHelper helper) {
        ServerPlayer buyer = customer(helper, "Buyer");
        ServerPlayer watcher = customer(helper, "Watcher");
        ServerPlayer away = customer(helper, "Elsewhere");
        try {
            MarketBook.get(helper.getLevel().getServer()).reset(APOTHECARY.toString());
            LocationDefinition def = Locations.open(APOTHECARY);
            var gated = def.market().orElseThrow().sells().stream().filter(good -> !good.minTier().isEmpty()
                    && !good.minTier().equals(def.standing().ranks().get(0).id())).findFirst();
            check(helper, gated.isPresent(), () -> "the apothecary sells nothing behind a tier");
            String good = gated.get().id();
            SettlementPostBlockEntity post = post(helper, "apothecary");
            NpcRole.Shop shop = NpcRoles.get(id("apothecary").toString()).shop().orElseThrow();
            long seed = 0;
            while (!ShelfRules.carries(seed, APOTHECARY.toString(), good, shop.rule(good).carry())) {
                seed++;
            }
            SettlementData.get(helper.getLevel()).site(ref(helper).site(), seed);
            Mob apothecary = staffed(helper, post);
            vault(buyer).depositGalleons(40);
            buyer.setData(ModAttachments.LOCATION_STANDING.get(), buyer.getData(ModAttachments.LOCATION_STANDING.get())
                    .with(APOTHECARY.toString(), LocationProgression.recordOf(buyer, APOTHECARY)
                            .withRank(def.standing().rankIndex(gated.get().minTier()))));
            LocationProgression.openAtHost(buyer, apothecary);
            LocationProgression.openAtHost(watcher, apothecary);
            int shelf = line(watcher, APOTHECARY, "dittany").count();
            check(helper, shelf >= 2, () -> "setup: the dittany shelf holds " + shelf);
            WizardTestSupport.drainClientbound(buyer);
            WizardTestSupport.drainClientbound(watcher);
            WizardTestSupport.drainClientbound(away);

            check(helper, MarketTrade.buy(buyer, APOTHECARY, "dittany", 2) == MarketTrade.Result.DONE, () -> "refused");

            var seen = lastBoard(WizardTestSupport.drainClientboundPayloads(watcher));
            check(helper, seen != null && !seen.open() && seen.view().id().equals(APOTHECARY),
                    () -> "the other customer was sent no board: " + seen);
            MarketLine dittany = lineOf(seen.view(), "dittany");
            MarketLine truth = line(watcher, APOTHECARY, "dittany");
            check(helper, dittany.count() == shelf - 2 && dittany.count() == truth.count(),
                    () -> "the other board shows " + dittany.count() + " on the shelf, the shelf holds " + truth.count());
            check(helper, dittany.unitKnuts() == truth.unitKnuts(),
                    () -> "the other board shows " + dittany.unitKnuts() + " Knuts, the price is " + truth.unitKnuts());
            MarketLine theirs = lineOf(seen.view(), good);
            check(helper, theirs != null && !theirs.unlocked(), () -> "the buyer's unlock reached the other board: " + theirs);
            var own = lastBoard(WizardTestSupport.drainClientboundPayloads(buyer));
            check(helper, own != null && lineOf(own.view(), good).unlocked(), () -> "the buyer's own board lost the unlock");
            check(helper, lastBoard(WizardTestSupport.drainClientboundPayloads(away)) == null,
                    () -> "a player at no counter was sent a board");
            apothecary.discard();
            helper.succeed();
        } finally {
            retire(helper, buyer);
            retire(helper, watcher);
            retire(helper, away);
        }
    }

    private static void boardBurst(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "Restless");
        try {
            int budget = at.koopro.wizardsandbeasts.network.location.LocationActionC2SPayload.ACTIONS_PER_TICK;
            Mob keeper = staffed(helper, post(helper, "keeper"));
            LocationProgression.openAtHost(player, keeper);
            WizardTestSupport.drainClientbound(player);
            int handled = 0;
            for (int i = 0; i < budget + 4; i++) {
                handled += at.koopro.wizardsandbeasts.network.location.LocationActionC2SPayload.submit(player,
                        new at.koopro.wizardsandbeasts.network.location.LocationActionC2SPayload(
                                at.koopro.wizardsandbeasts.network.location.LocationActionC2SPayload.Action.REFRESH, HAMLET, ""))
                        ? 1 : 0;
            }
            int took = handled;
            WizardTestSupport.Clientbound sent = WizardTestSupport.drainClientbound(player);
            long boards = sent.payloads().stream()
                    .filter(p -> p instanceof at.koopro.wizardsandbeasts.network.location.LocationViewS2CPayload).count();
            check(helper, took == budget && boards == budget, () -> "handled " + took + ", boards sent " + boards);
            check(helper, told(sent.lines(), "location.wizards_and_beasts.busy") == 1,
                    () -> "the refusal was told " + told(sent.lines(), "location.wizards_and_beasts.busy") + " times");
            keeper.discard();
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void unlocksArePersonal(GameTestHelper helper) {
        ServerPlayer trusted = customer(helper, "Trusted");
        ServerPlayer stranger = customer(helper, "Stranger");
        try {
            MarketBook.get(helper.getLevel().getServer()).reset(APOTHECARY.toString());
            LocationDefinition def = Locations.open(APOTHECARY);
            var gated = def.market().orElseThrow().sells().stream().filter(good -> !good.minTier().isEmpty()
                    && !good.minTier().equals(def.standing().ranks().get(0).id())).findFirst();
            check(helper, gated.isPresent(), () -> "the apothecary sells nothing behind a tier");
            String good = gated.get().id();
            String tier = gated.get().minTier();
            // Sites differ in what they stock, so this one is seeded to stock the good under test.
            SettlementPostBlockEntity post = post(helper, "apothecary");
            NpcRole.Shop shop = NpcRoles.get(id("apothecary").toString()).shop().orElseThrow();
            long seed = 0;
            while (!ShelfRules.carries(seed, APOTHECARY.toString(), good, shop.rule(good).carry())) {
                seed++;
            }
            SettlementData.get(helper.getLevel()).site(ref(helper).site(), seed);
            Mob apothecary = staffed(helper, post);
            LocationProgression.openAtHost(trusted, apothecary);
            LocationProgression.openAtHost(stranger, apothecary);
            int rank = def.standing().rankIndex(tier);
            trusted.setData(ModAttachments.LOCATION_STANDING.get(), trusted.getData(ModAttachments.LOCATION_STANDING.get())
                    .with(APOTHECARY.toString(), LocationProgression.recordOf(trusted, APOTHECARY).withRank(rank)));
            MarketLine theirs = line(trusted, APOTHECARY, good);
            MarketLine others = line(stranger, APOTHECARY, good);
            check(helper, theirs != null && others != null, () -> "a stocked good is missing from the board");
            check(helper, theirs.unlocked() && !others.unlocked(), () -> "unlocks leaked: " + theirs.unlocked() + " " + others.unlocked());
            apothecary.discard();
            helper.succeed();
        } finally {
            retire(helper, trusted);
            retire(helper, stranger);
        }
    }

    // -- ground, at every rotation -------------------------------------------------------------------------

    private static final Identifier CELLARED = id("wizarding_hamlet/apothecary/a");
    /**
     * Well above the plot's floor, and each rotation on its own ground: plots stand a dozen blocks apart, and a scene
     * this size would otherwise lie in its sibling's chunks.
     */
    private static BlockPos scene(Rotation rotation) {
        return new BlockPos(30, 40, 30 + 200 * (rotation.ordinal() + 1));
    }

    private static final int ROCK_MARGIN = 9;

    private static StructurePlaceSettings turned(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true);
    }

    private static BoundingBox sceneBox(GameTestHelper helper, StructureTemplate template, Rotation rotation) {
        return template.getBoundingBox(turned(rotation), helper.absolutePos(scene(rotation)));
    }

    private static void forceScene(GameTestHelper helper, Rotation rotation) {
        BlockPos scene = scene(rotation);
        WizardTestSupport.forceChunks(helper, scene.offset(-64, -40, -64), scene.offset(64, -40, 64));
    }

    /** Leaves no rock, and no reference, behind: the level is shared with every later scenario. */
    private static void clearScene(GameTestHelper helper, BoundingBox box) {
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX() - ROCK_MARGIN, box.minY() - ROCK_MARGIN, box.minZ() - ROCK_MARGIN,
                box.maxX() + ROCK_MARGIN, box.maxY() + ROCK_MARGIN, box.maxZ() + ROCK_MARGIN)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        for (int x = SectionPos.blockToSectionCoord(box.minX() - 16); x <= SectionPos.blockToSectionCoord(box.maxX() + 16); x++) {
            for (int z = SectionPos.blockToSectionCoord(box.minZ() - 16); z <= SectionPos.blockToSectionCoord(box.maxZ() + 16); z++) {
                level.getChunk(x, z).setAllReferences(Map.of());
            }
        }
    }

    /** The template in solid rock, or in {@code around} if that is air. Its post is taken out: this tests rock, not people. */
    private static void build(GameTestHelper helper, StructureTemplate template, Rotation rotation, BlockState around) {
        ServerLevel level = helper.getLevel();
        BoundingBox box = sceneBox(helper, template, rotation);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX() - ROCK_MARGIN, box.minY() - ROCK_MARGIN, box.minZ() - ROCK_MARGIN,
                box.maxX() + ROCK_MARGIN, box.maxY() + ROCK_MARGIN, box.maxZ() + ROCK_MARGIN)) {
            level.setBlock(pos, around, 2);
        }
        BlockPos origin = helper.absolutePos(scene(rotation));
        template.placeInWorld(level, origin, origin, turned(rotation), level.getRandom(), 2);
        for (var post : template.filterBlocks(origin, turned(rotation), ModBlocks.SETTLEMENT_POST.get())) {
            level.setBlock(post.pos(), around, 2);
        }
    }

    private static List<BlockPos> shell(GameTestHelper helper, StructureTemplate template, Rotation rotation) {
        return template.filterBlocks(helper.absolutePos(scene(rotation)), turned(rotation), Blocks.DEEPSLATE_TILES).stream()
                .map(StructureTemplate.StructureBlockInfo::pos).toList();
    }

    /** Tries a monster room at every origin around the Undercroft. Returns how many started. */
    private static int rooms(GameTestHelper helper, BoundingBox box) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomSource random = RandomSource.create(1234L);
        int started = 0;
        for (BlockPos origin : BlockPos.betweenClosed(box.minX() - 4, box.minY() + 1, box.minZ() - 4, box.maxX() + 4,
                box.minY() + 6, box.maxZ() + 4)) {
            if (level.getBlockState(origin).is(Blocks.STONE)
                    && Feature.MONSTER_ROOM.place(NoneFeatureConfiguration.INSTANCE, level, generator, random, origin.immutable())) {
                started++;
            }
        }
        return started;
    }

    private static void monsterRoom(GameTestHelper helper, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        forceScene(helper, rotation);
        StructureTemplate template = level.getStructureManager().getOrCreate(CELLARED);
        BlockState rock = Blocks.STONE.defaultBlockState();

        // Without a settlement's reference: the defect itself, so this test can fail for the right reason.
        build(helper, template, rotation, rock);
        BoundingBox box = sceneBox(helper, template, rotation);
        List<BlockPos> walls = shell(helper, template, rotation);
        check(helper, walls.size() > 100, () -> "the Undercroft has only " + walls.size() + " shell blocks");
        int unguarded = rooms(helper, box);
        long breached = walls.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.DEEPSLATE_TILES)).count();
        check(helper, unguarded > 0 && breached > 0,
                () -> rotation + ": control found no breach, " + unguarded + " rooms and " + breached + " blocks");

        // With one: the same rock, the same origins, and nothing starts.
        build(helper, template, rotation, rock);
        Structure hamlet = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValueOrThrow(ResourceKey.create(Registries.STRUCTURE, id("wizarding_hamlet")));
        for (int x = SectionPos.blockToSectionCoord(box.minX() - 16); x <= SectionPos.blockToSectionCoord(box.maxX() + 16); x++) {
            for (int z = SectionPos.blockToSectionCoord(box.minZ() - 16); z <= SectionPos.blockToSectionCoord(box.maxZ() + 16); z++) {
                level.getChunk(x, z).addReferenceForStructure(hamlet, 1L);
            }
        }
        int guarded = rooms(helper, box);
        check(helper, guarded == 0, () -> rotation + ": " + guarded + " rooms started under a settlement");
        long standing = walls.stream().filter(pos -> level.getBlockState(pos).is(Blocks.DEEPSLATE_TILES)).count();
        check(helper, standing == walls.size(), () -> rotation + ": " + (walls.size() - standing) + " shell blocks lost");
        clearScene(helper, box);
        helper.succeed();
    }

    private static void footing(GameTestHelper helper, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        forceScene(helper, rotation);
        StructureTemplate template = level.getStructureManager().getOrCreate(id("wizarding_hamlet/keeper/a"));
        build(helper, template, rotation, Blocks.AIR.defaultBlockState());
        BoundingBox box = sceneBox(helper, template, rotation);
        int floorY = box.minY();
        // Solid ground six blocks down, so a pier has somewhere to end.
        int groundY = floorY - 6;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), groundY, box.minZ(), box.maxX(), groundY, box.maxZ())) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        SettlementGround.foot(level, box, floorY, pos -> box.isInside(pos));
        int floors = 0;
        int openBelowPlate = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos floor = new BlockPos(x, floorY, z);
                if (level.isEmptyBlock(floor)) {
                    continue;
                }
                floors++;
                final BlockPos at = floor;
                check(helper, !SettlementGround.open(level.getBlockState(floor.below())), () -> rotation + ": unsupported at " + at);
                openBelowPlate += SettlementGround.open(level.getBlockState(floor.below(SettlementGround.PLATE_DEPTH + 1))) ? 1 : 0;
                boolean pier = !SettlementGround.open(level.getBlockState(floor.below(5)));
                check(helper, pier == SettlementGround.footed(x, z, 5, 6), () -> rotation + ": wrong pier at " + at);
            }
        }
        final int total = floors;
        final int gaps = openBelowPlate;
        check(helper, total >= 150, () -> rotation + ": only " + total + " floor cells");
        check(helper, gaps > total * 3 / 4, () -> rotation + ": the gap was filled, only " + gaps + " of " + total + " open");
        clearScene(helper, box);
        helper.succeed();
    }

    // -- the real assembly, at every turn of the plaza ------------------------------------------------------

    /** Each turn of the plaza is seen this many times: which building lands on which arm is drawn as well. */
    private static final int TURN_SAMPLE = 12;
    /** As {@code worldgen/structure/wizarding_hamlet.json}: its {@code size} and {@code max_distance_from_center}. */
    private static final int JIGSAW_SIZE = 2;
    private static final int MAX_DISTANCE = 48;

    /** Seed and chunk of sites where the world probe saw another structure's pieces inside a generated hamlet. */
    private static final long[][] TAKEN_SITES = {
            {1234567L, -145, 123}, {1234567L, 10, 84}, {1618033988L, -426, 297}, {3141592653L, -351, 496},
            {7777777L, -199, 442}, {987654321L, 177, 164}, {987654321L, 336, 125}, {271828182L, 19, 211}};
    /** Seed and chunk of sites the same worlds keep: giving way must not turn into refusing everything. */
    private static final long[][] FREE_SITES = {{987654321L, 203, 174}, {1234567L, 414, -313}};

    private static void givesWay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // One taken site is a Chamber's, and the Chamber only starts with its module on. A config written before it
        // shipped as PREVIEW still says disabled, so the module is set here, not read from whatever config the run has.
        Runnable chamber = WizardTestSupport.leaseModule(at.koopro.wizardsandbeasts.module.Module.CHAMBER_OF_SECRETS);
        try {
            for (long[] site : TAKEN_SITES) {
                var verdict = verdict(level, site);
                check(helper, verdict == SettlementSite.Verdict.OCCUPIED,
                        () -> "seed " + site[0] + " chunk " + site[1] + ", " + site[2] + " came out " + verdict);
            }
            for (long[] site : FREE_SITES) {
                var verdict = verdict(level, site);
                check(helper, verdict == SettlementSite.Verdict.STANDS,
                        () -> "seed " + site[0] + " chunk " + site[1] + ", " + site[2] + " came out " + verdict);
            }
        } finally {
            chamber.run();
        }
        helper.succeed();
    }

    /** The site rules over a default world of that seed, with nothing generated: they read noise, not chunks. */
    private static SettlementSite.Verdict verdict(ServerLevel level, long[] site) {
        var registries = level.registryAccess();
        var world = (NoiseBasedChunkGenerator) registries.lookupOrThrow(Registries.WORLD_PRESET).getValueOrThrow(WorldPresets.NORMAL)
                .overworld().orElseThrow().generator();
        var random = RandomState.create(world.generatorSettings().value(), registries.lookupOrThrow(Registries.NOISE), site[0]);
        return settlement(level).assess(new Structure.GenerationContext(registries, world, world.getBiomeSource(), random,
                level.getStructureManager(), site[0], new ChunkPos((int) site[1], (int) site[2]), level, biome -> true)).verdict();
    }

    private static SettlementStructure settlement(ServerLevel level) {
        return (SettlementStructure) level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValueOrThrow(ResourceKey.create(Registries.STRUCTURE, id("wizarding_hamlet")));
    }

    /**
     * What vanilla's assembler builds from the hamlet's own pools at a chunk, turned as that chunk's seed turns it. It is
     * started at a fixed height: the test world's floor lies at the bottom of the world, where a cellar has no room.
     */
    private static StructurePiecesBuilder assembled(GameTestHelper helper, ChunkPos chunk) {
        return assembled(helper, chunk, liquidSettings(helper.getLevel()));
    }

    /** As a start saved under {@code liquid} assembles: vanilla's default is what a start from before the fix kept. */
    private static StructurePiecesBuilder assembled(GameTestHelper helper, ChunkPos chunk,
                                                    net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquid) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        var context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), chunk, level, biome -> true);
        var pool = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL)
                .getOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, id("wizarding_hamlet/plaza")));
        var stub = JigsawPlacement.addPieces(context, pool, Optional.empty(), JIGSAW_SIZE, chunk.getBlockAt(0, SCENE_FLOOR, 0), false,
                Optional.empty(), new JigsawStructure.MaxDistance(MAX_DISTANCE, DimensionType.Y_SIZE), PoolAliasLookup.EMPTY,
                JigsawStructure.DEFAULT_DIMENSION_PADDING, liquid);
        check(helper, stub.isPresent(), () -> "jigsaw built nothing at " + chunk);
        return stub.orElseThrow().getPiecesBuilder();
    }

    /**
     * How the hamlet's pieces meet water already in the ground, read from its structure file through the structure's own
     * codec. Jigsaw keeps the setting to itself, and a scene that assumed vanilla's default would test vanilla.
     */
    private static net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquidSettings(ServerLevel level) {
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, level.registryAccess());
        var json = SettlementStructure.CODEC.codec().encodeStart(ops, settlement(level)).getOrThrow().getAsJsonObject();
        return json.has("liquid_settings")
                ? net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.CODEC.parse(ops, json.get("liquid_settings")).getOrThrow()
                : JigsawStructure.DEFAULT_LIQUID_SETTINGS;
    }

    private static List<PoolElementStructurePiece> built(StructurePiecesBuilder pieces) {
        return pieces.build().pieces().stream().filter(PoolElementStructurePiece.class::isInstance)
                .map(PoolElementStructurePiece.class::cast)
                .filter(piece -> piece.getElement() instanceof SinglePoolElement).toList();
    }

    private static Identifier location(PoolElementStructurePiece piece) {
        return ((SinglePoolElement) piece.getElement()).getTemplateLocation();
    }

    private static String role(PoolElementStructurePiece piece) {
        return SettlementSite.roleOf(location(piece).getPath());
    }

    private static List<StructureTemplate.StructureBlockInfo> placed(ServerLevel level, PoolElementStructurePiece piece,
                                                                     net.minecraft.world.level.block.Block block) {
        return level.getStructureManager().getOrCreate(location(piece))
                .filterBlocks(piece.getPosition(), turned(piece.getRotation()), block);
    }

    private static void assembly(GameTestHelper helper) {
        Map<Rotation, Integer> seen = new java.util.EnumMap<>(Rotation.class);
        for (int x = 0; x < 2000 && (seen.size() < 4 || seen.values().stream().anyMatch(n -> n < TURN_SAMPLE)); x++) {
            List<PoolElementStructurePiece> pieces = built(assembled(helper, new ChunkPos(x * 7, -x * 3)));
            PoolElementStructurePiece plaza = pieces.stream().filter(piece -> role(piece).equals("plaza")).findFirst().orElseThrow();
            seen.merge(plaza.getRotation(), 1, Integer::sum);
            wholeAndApart(helper, plaza, pieces);
            connectorsMeet(helper, plaza, pieces);
            undercroftAlone(helper, plaza, pieces);
        }
        check(helper, seen.size() == 4 && seen.values().stream().allMatch(n -> n >= TURN_SAMPLE), () -> "turns seen: " + seen);
        helper.succeed();
    }

    private static void wholeAndApart(GameTestHelper helper, PoolElementStructurePiece plaza, List<PoolElementStructurePiece> pieces) {
        Rotation turn = plaza.getRotation();
        for (String role : SettlementSite.REQUIRED_ROLES) {
            long count = pieces.stream().filter(piece -> role(piece).equals(role)).count();
            check(helper, count == 1, () -> turn + ": " + count + " of " + role);
        }
        BoundingBox hub = plaza.getBoundingBox();
        BlockPos centre = hub.getCenter();
        for (PoolElementStructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            check(helper, Math.abs(box.minX() - centre.getX()) <= MAX_DISTANCE && Math.abs(box.maxX() - centre.getX()) <= MAX_DISTANCE
                            && Math.abs(box.minZ() - centre.getZ()) <= MAX_DISTANCE && Math.abs(box.maxZ() - centre.getZ()) <= MAX_DISTANCE,
                    () -> turn + ": " + role(piece) + " reaches past the jigsaw limit: " + box);
            for (PoolElementStructurePiece other : pieces) {
                check(helper, piece == other || !box.intersects(other.getBoundingBox()),
                        () -> turn + ": " + role(piece) + " overlaps " + role(other) + ": " + box + " " + other.getBoundingBox());
            }
            check(helper, piece == plaza || box.inflatedBy(1).intersects(hub), () -> turn + ": " + role(piece) + " stands off the plaza");
        }
    }

    /** Every connector in the assembly either faces its partner exactly, or is the open plot's, facing nothing. */
    private static void connectorsMeet(GameTestHelper helper, PoolElementStructurePiece plaza, List<PoolElementStructurePiece> pieces) {
        ServerLevel level = helper.getLevel();
        Rotation turn = plaza.getRotation();
        List<StructureTemplate.StructureBlockInfo> hubs = placed(level, plaza, Blocks.JIGSAW);
        check(helper, hubs.size() == SettlementSite.REQUIRED_ROLES.size(), () -> turn + ": the plaza has " + hubs.size() + " connectors");
        int matched = 0;
        for (PoolElementStructurePiece piece : pieces) {
            if (piece == plaza) {
                continue;
            }
            List<StructureTemplate.StructureBlockInfo> doors = placed(level, piece, Blocks.JIGSAW);
            check(helper, doors.size() == 1, () -> turn + ": " + role(piece) + " has " + doors.size() + " connectors");
            StructureTemplate.StructureBlockInfo door = doors.get(0);
            String pool = "wizards_and_beasts:wizarding_hamlet/" + role(piece);
            StructureTemplate.StructureBlockInfo hub = hubs.stream()
                    .filter(info -> info.nbt() != null && info.nbt().getStringOr("pool", "").equals(pool)).findFirst().orElseThrow();
            var faces = door.state().getValue(JigsawBlock.ORIENTATION).front();
            check(helper, faces == hub.state().getValue(JigsawBlock.ORIENTATION).front().getOpposite()
                            && door.pos().relative(faces).equals(hub.pos()),
                    () -> turn + ": " + role(piece) + " connector at " + door.pos() + " does not meet the hub at " + hub.pos());
            matched++;
        }
        final int met = matched;
        check(helper, met == 5, () -> turn + ": " + met + " connectors met");
    }

    /** The shell of the Undercroft lies wholly inside its piece, under the floor, with no other piece in the way. */
    private static void undercroftAlone(GameTestHelper helper, PoolElementStructurePiece plaza, List<PoolElementStructurePiece> pieces) {
        ServerLevel level = helper.getLevel();
        Rotation turn = plaza.getRotation();
        PoolElementStructurePiece shop = pieces.stream().filter(piece -> role(piece).equals("apothecary")).findFirst().orElseThrow();
        List<StructureTemplate.StructureBlockInfo> shell = placed(level, shop, Blocks.DEEPSLATE_TILES);
        check(helper, shell.size() > 100, () -> turn + ": the shell has only " + shell.size() + " blocks");
        int floor = plaza.getBoundingBox().minY();
        check(helper, shop.getBoundingBox().minY() < floor, () -> turn + ": the Undercroft is not under the floor");
        for (StructureTemplate.StructureBlockInfo info : shell) {
            check(helper, shop.getBoundingBox().isInside(info.pos()) && info.pos().getY() < floor,
                    () -> turn + ": shell block out of place at " + info.pos());
            for (StructurePiece other : pieces) {
                check(helper, other == shop || !other.getBoundingBox().isInside(info.pos()),
                        () -> turn + ": shell block inside another piece at " + info.pos());
            }
        }
        for (PoolElementStructurePiece piece : pieces) {
            check(helper, piece == shop || piece.getBoundingBox().minY() == floor,
                    () -> turn + ": " + role(piece) + " does not stand on the shared floor");
        }
    }

    // -- the whole hamlet on terrain, placed as generation places it ----------------------------------------

    /** What a hamlet can find under it once the carvers have run. A site check sees none of this. */
    private enum Terrain {
        RAVINE("a ravine it can pier down into"),
        BOTTOMLESS("a ravine too deep to pier"),
        FLOODED("a ravine full of water"),
        UNEVEN("rock that rises and falls under the floors"),
        CAVERN("a cavern roofed with sound rock");

        final String description;

        Terrain(String description) {
            this.description = description;
        }
    }

    private static final int SCENE_FLOOR = 150;
    /** Rock laid under a scene. A cellar goes six down, and a ravine has to run clear below that. */
    private static final int SCENE_ROCK = 22;
    private static final int RAVINE_HALF_WIDTH = 5;
    private static final int RAVINE_DEPTH = 16;
    private static final Map<Identifier, List<Block>> TEMPLATE_BLOCKS = new java.util.HashMap<>();

    /** Each scene on its own ground, far from every plot; generation's own chunks hold nothing there. */
    private static BlockPos sceneCentre(GameTestHelper helper, Terrain terrain, Rotation rotation) {
        return helper.absolutePos(BlockPos.ZERO).offset(4000 + 400 * terrain.ordinal(), 0, 4000 + 400 * rotation.ordinal())
                .atY(SCENE_FLOOR);
    }

    /** The real pieces, turned as asked, moved so that the plaza's floor lies on {@code at}. */
    private static StructureStart startAt(GameTestHelper helper, Rotation rotation, BlockPos at) {
        return startAt(helper, rotation, at, liquidSettings(helper.getLevel()));
    }

    private static StructureStart startAt(GameTestHelper helper, Rotation rotation, BlockPos at,
                                          net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquid) {
        for (int x = 0; x < 400; x++) {
            ChunkPos chunk = new ChunkPos(x * 5, x * 11);
            StructurePiecesBuilder pieces = assembled(helper, chunk, liquid);
            List<PoolElementStructurePiece> built = built(pieces);
            PoolElementStructurePiece plaza = built.stream().filter(piece -> role(piece).equals("plaza")).findFirst().orElseThrow();
            if (plaza.getRotation() == rotation) {
                BoundingBox hub = plaza.getBoundingBox();
                int dx = at.getX() - hub.getCenter().getX();
                int dy = at.getY() - hub.minY();
                int dz = at.getZ() - hub.getCenter().getZ();
                built.forEach(piece -> piece.move(dx, dy, dz));
                return new StructureStart(settlement(helper.getLevel()), new ChunkPos(at), 0, pieces.build());
            }
        }
        throw new IllegalStateException("no plaza turned " + rotation);
    }

    private static void place(GameTestHelper helper, StructureStart start) {
        ServerLevel level = helper.getLevel();
        BoundingBox all = start.getBoundingBox();
        ChunkPos.rangeClosed(new ChunkPos(new BlockPos(all.minX(), 0, all.minZ())), new ChunkPos(new BlockPos(all.maxX(), 0, all.maxZ())))
                .forEach(chunk -> start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                        level.getRandom(), new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                                chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ()), chunk));
    }

    /** The pieces alone, without the margin a start's box adds for terrain adaptation. */
    private static BoundingBox footprint(StructureStart start) {
        return StructurePiece.createBoundingBox(start.getPieces().stream());
    }

    private static void fill(ServerLevel level, BoundingBox box, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            level.setBlock(pos, state, 2);
        }
    }

    /** Lays rock under the whole footprint, then opens the terrain asked for, along the arm with the cellar on it. */
    private static void lay(GameTestHelper helper, Terrain terrain, StructureStart start, BoundingBox shop, BlockPos centre) {
        ServerLevel level = helper.getLevel();
        BoundingBox all = footprint(start);
        BlockState rock = Blocks.STONE.defaultBlockState();
        fill(level, new BoundingBox(all.minX(), SCENE_FLOOR - SCENE_ROCK, all.minZ(), all.maxX(), SCENE_FLOOR - 1, all.maxZ()), rock);
        BlockPos middle = shop.getCenter();
        boolean alongX = Math.abs(middle.getX() - centre.getX()) > Math.abs(middle.getZ() - centre.getZ());
        BoundingBox trench = new BoundingBox(
                alongX ? all.minX() : middle.getX() - RAVINE_HALF_WIDTH, 0, alongX ? middle.getZ() - RAVINE_HALF_WIDTH : all.minZ(),
                alongX ? all.maxX() : middle.getX() + RAVINE_HALF_WIDTH, 0, alongX ? middle.getZ() + RAVINE_HALF_WIDTH : all.maxZ());
        int top = SCENE_FLOOR - 1;
        switch (terrain) {
            case RAVINE -> fill(level, ranged(trench, top - RAVINE_DEPTH, top), Blocks.AIR.defaultBlockState());
            case BOTTOMLESS -> fill(level, ranged(trench, SCENE_FLOOR - SCENE_ROCK, top), Blocks.AIR.defaultBlockState());
            case FLOODED -> fill(level, ranged(trench, top - RAVINE_DEPTH, top), Blocks.WATER.defaultBlockState());
            case UNEVEN -> {
                for (int x = all.minX(); x <= all.maxX(); x++) {
                    for (int z = all.minZ(); z <= all.maxZ(); z++) {
                        int drop = Math.floorMod((x >> 2) * 3 + (z >> 2) * 5, 5);
                        for (int y = top; y > top - drop; y--) {
                            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
            case CAVERN -> fill(level, new BoundingBox(centre.getX() - 8, top - 16, centre.getZ() - 8, centre.getX() + 8,
                    top - 3, centre.getZ() + 8), Blocks.AIR.defaultBlockState());
        }
    }

    private static BoundingBox ranged(BoundingBox plan, int minY, int maxY) {
        return new BoundingBox(plan.minX(), minY, plan.minZ(), plan.maxX(), maxY, plan.maxZ());
    }

    private static List<Block> templateBlocks(StructureTemplate template, Identifier location) {
        return TEMPLATE_BLOCKS.computeIfAbsent(location, key -> BuiltInRegistries.BLOCK.stream()
                .filter(block -> block != Blocks.AIR && block != Blocks.JIGSAW)
                .filter(block -> !template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).isEmpty()).toList());
    }

    private static void stands(GameTestHelper helper, Terrain terrain, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = sceneCentre(helper, terrain, rotation);
        StructureStart start = startAt(helper, rotation, centre);
        List<PoolElementStructurePiece> pieces = start.getPieces().stream().filter(PoolElementStructurePiece.class::isInstance)
                .map(PoolElementStructurePiece.class::cast).filter(piece -> piece.getElement() instanceof SinglePoolElement).toList();
        PoolElementStructurePiece shop = pieces.stream().filter(piece -> role(piece).equals("apothecary")).findFirst().orElseThrow();
        lay(helper, terrain, start, shop.getBoundingBox(), centre);
        place(helper, start);
        BoundingBox all = footprint(start);
        int cellar = shop.getBoundingBox().minY();

        // Everything the templates hold stands where they put it, walls and working parts alike.
        for (PoolElementStructurePiece piece : pieces) {
            StructureTemplate template = level.getStructureManager().getOrCreate(location(piece));
            for (Block block : templateBlocks(template, location(piece))) {
                for (var info : placed(level, piece, block)) {
                    check(helper, level.getBlockState(info.pos()).is(block),
                            () -> terrain + " " + rotation + ": " + role(piece) + " lost " + block + " at " + info.pos());
                    // A ladder or lantern placed where the ground held water keeps it, and floods the room once it ticks.
                    check(helper, level.getFluidState(info.pos()).isEmpty() || !info.state().getFluidState().isEmpty(),
                            () -> terrain + " " + rotation + ": " + role(piece) + " " + block + " holds water at " + info.pos());
                }
            }
        }
        // Nothing hangs: vanilla's own test of what a block can rest on, not this mod's.
        int supported = 0;
        for (PoolElementStructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos floor = new BlockPos(x, SCENE_FLOOR, z);
                    if (!level.isEmptyBlock(floor)) {
                        supported++;
                        check(helper, !FallingBlock.isFree(level.getBlockState(floor.below())),
                                () -> terrain + " " + rotation + ": floor over nothing at " + floor);
                    }
                }
            }
        }
        final int cells = supported;
        check(helper, cells > 1500, () -> terrain + " " + rotation + ": only " + cells + " floor cells");
        for (var bottom : placed(level, shop, Blocks.DEEPSLATE_TILES)) {
            if (bottom.pos().getY() == cellar) {
                check(helper, !FallingBlock.isFree(level.getBlockState(bottom.pos().below())),
                        () -> terrain + " " + rotation + ": the Undercroft hangs at " + bottom.pos());
            }
        }
        masonryFits(helper, terrain, rotation, all, cellar);
        // The scene is shared ground: nothing of it may outlive the test.
        fill(level, new BoundingBox(all.minX(), SCENE_FLOOR - SCENE_ROCK - 4, all.minZ(), all.maxX(), all.maxY(), all.maxZ()),
                Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    /** The rule for each terrain, read from the finished scene: how much was built, and that none of it dangles. */
    private static void masonryFits(GameTestHelper helper, Terrain terrain, Rotation rotation, BoundingBox all, int cellar) {
        ServerLevel level = helper.getLevel();
        int masonry = 0;
        int piers = 0;
        int open = 0;
        for (int x = all.minX(); x <= all.maxX(); x++) {
            for (int z = all.minZ(); z <= all.maxZ(); z++) {
                int lowest = Integer.MAX_VALUE;
                for (int y = SCENE_FLOOR - 1; y >= SCENE_FLOOR - SCENE_ROCK - 4; y--) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)) {
                        masonry++;
                        lowest = y;
                    } else if (y >= SCENE_FLOOR - SCENE_ROCK && y < cellar - SettlementGround.PLATE_DEPTH - 1
                            && SettlementGround.open(state)) {
                        open++;
                    }
                }
                if (lowest == Integer.MAX_VALUE) {
                    continue;
                }
                BlockPos foot = new BlockPos(x, lowest, z);
                boolean pier = lowest < SCENE_FLOOR - SettlementGround.PLATE_DEPTH && lowest < cellar - SettlementGround.PLATE_DEPTH;
                piers += pier ? 1 : 0;
                check(helper, !pier || !SettlementGround.open(level.getBlockState(foot.below())),
                        () -> terrain + " " + rotation + ": a pier ends in the air at " + foot);
                check(helper, SCENE_FLOOR - lowest <= SettlementGround.PIER_REACH + 1,
                        () -> terrain + " " + rotation + ": masonry runs too deep at " + foot);
            }
        }
        final int built = masonry;
        final int columns = piers;
        final int gap = open;
        switch (terrain) {
            case RAVINE, FLOODED -> {
                check(helper, columns > 0, () -> terrain + " " + rotation + ": a reachable ravine got no pier");
                check(helper, built < 2500, () -> terrain + " " + rotation + ": " + built + " masonry blocks: the ravine was filled");
            }
            case BOTTOMLESS -> {
                check(helper, columns == 0, () -> terrain + " " + rotation + ": " + columns + " piers begun over a gap too deep");
                check(helper, gap > 3000, () -> terrain + " " + rotation + ": only " + gap + " open cells: the gap was plugged");
            }
            case UNEVEN -> check(helper, built > 0 && built < 8000, () -> terrain + " " + rotation + ": " + built + " masonry blocks");
            case CAVERN -> check(helper, built == 0, () -> terrain + " " + rotation + ": " + built + " blocks under sound rock");
        }
    }

    /** Template blocks that hold water now and do not in their template: each one a spring in a cellar. */
    private static List<String> wet(GameTestHelper helper, List<PoolElementStructurePiece> pieces) {
        ServerLevel level = helper.getLevel();
        List<String> wet = new java.util.ArrayList<>();
        for (PoolElementStructurePiece piece : pieces) {
            StructureTemplate template = level.getStructureManager().getOrCreate(location(piece));
            for (Block block : templateBlocks(template, location(piece))) {
                for (var info : placed(level, piece, block)) {
                    if (!level.getFluidState(info.pos()).isEmpty() && info.state().getFluidState().isEmpty()) {
                        wet.add(role(piece) + " " + block + " at " + info.pos().toShortString());
                    }
                }
            }
        }
        return wet;
    }

    /**
     * A start saved before the hamlet ignored waterlogging keeps vanilla's default in its pieces, and a cellar chunk generated
     * after the update would flood. The pass {@code SettlementRooms} makes over every newly generated chunk must dry it.
     */
    private static void oldStartDries(GameTestHelper helper, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(BlockPos.ZERO).offset(13000, 0, 4000 + 400 * rotation.ordinal()).atY(SCENE_FLOOR);
        StructureStart start = startAt(helper, rotation, centre, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
        List<PoolElementStructurePiece> pieces = start.getPieces().stream().filter(PoolElementStructurePiece.class::isInstance)
                .map(PoolElementStructurePiece.class::cast).filter(piece -> piece.getElement() instanceof SinglePoolElement).toList();
        PoolElementStructurePiece shop = pieces.stream().filter(piece -> role(piece).equals("apothecary")).findFirst().orElseThrow();
        lay(helper, Terrain.FLOODED, start, shop.getBoundingBox(), centre);
        place(helper, start);
        BoundingBox all = footprint(start);
        try {
            // The positive control: an old start on wet ground does flood, or this proves nothing.
            List<String> before = wet(helper, pieces);
            check(helper, !before.isEmpty(), () -> rotation + ": setup: an old start placed on water held none");
            ChunkPos.rangeClosed(new ChunkPos(new BlockPos(all.minX(), 0, all.minZ())), new ChunkPos(new BlockPos(all.maxX(), 0, all.maxZ())))
                    .forEach(chunk -> SettlementRooms.clear(level, level.getChunk(chunk.x, chunk.z), start));
            List<String> after = wet(helper, pieces);
            check(helper, after.isEmpty(), () -> rotation + ": " + after.size() + " of " + before.size() + " still hold water: " + after);
        } finally {
            fill(level, new BoundingBox(all.minX(), SCENE_FLOOR - SCENE_ROCK - 4, all.minZ(), all.maxX(), all.maxY(), all.maxZ()),
                    Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    private static void rooms(GameTestHelper helper, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(BlockPos.ZERO).offset(9000, 0, 4000 + 400 * rotation.ordinal()).atY(SCENE_FLOOR);
        StructureStart start = startAt(helper, rotation, centre);
        place(helper, start);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        int grown = 0;
        java.util.Set<BlockPos> outdoors = new java.util.HashSet<>();
        List<BlockPos> everywhere = new java.util.ArrayList<>();
        for (StructurePiece raw : start.getPieces()) {
            if (raw instanceof PoolElementStructurePiece piece && piece.getElement() instanceof SinglePoolElement) {
                for (var air : placed(level, piece, Blocks.AIR)) {
                    BlockPos pos = air.pos();
                    everywhere.add(pos);
                    boolean roofed = false;
                    for (int y = pos.getY() + 1; y <= piece.getBoundingBox().maxY() && !roofed; y++) {
                        roofed = !level.isEmptyBlock(pos.atY(y));
                    }
                    grown += roofed ? 1 : 0;
                    if (!roofed) {
                        outdoors.add(pos);
                    }
                }
            }
        }
        // Only now, so that a leaf never counts as a roof.
        everywhere.forEach(pos -> level.setBlock(pos, leaves, 2));
        final int yard = outdoors.size();
        final int rooms = grown;
        check(helper, rooms > 200 && yard > 200, () -> rotation + ": rooms " + rooms + ", open air " + yard);
        BoundingBox all = footprint(start);
        ChunkPos.rangeClosed(new ChunkPos(new BlockPos(all.minX(), 0, all.minZ())), new ChunkPos(new BlockPos(all.maxX(), 0, all.maxZ())))
                .forEach(chunk -> SettlementRooms.clear(level, level.getChunk(chunk.x, chunk.z), start));
        for (BlockPos pos : everywhere) {
            check(helper, level.getBlockState(pos).is(Blocks.OAK_LEAVES) == outdoors.contains(pos),
                    () -> rotation + ": wrong at " + pos + ", " + level.getBlockState(pos));
        }
        fill(level, new BoundingBox(all.minX(), all.minY(), all.minZ(), all.maxX(), all.maxY(), all.maxZ()), Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    private static void noMuggles(GameTestHelper helper) {
        SettlementNpcEntity npc = helper.spawn(at.koopro.wizardsandbeasts.registry.ModEntities.SETTLEMENT_NPC.get(), new BlockPos(1, 1, 1));
        check(helper, !CastSurvey.isMuggle(npc), () -> "a hamlet's resident counts as a Muggle");
        npc.discard();
        helper.succeed();
    }
}
