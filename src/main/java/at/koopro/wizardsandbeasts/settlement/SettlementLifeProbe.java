package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.brew.CauldronHeat;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.chamber.ChamberQuestService;
import at.koopro.wizardsandbeasts.chamber.ChamberSealBlock;
import at.koopro.wizardsandbeasts.chamber.ChamberStage;
import at.koopro.wizardsandbeasts.currency.vault.GringottsCounter;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.LocationView;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.market.MarketLine;
import at.koopro.wizardsandbeasts.market.MarketTrade;
import at.koopro.wizardsandbeasts.network.currency.VaultActionC2SPayload;
import at.koopro.wizardsandbeasts.network.owl.RequestOWLExamPacket;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.settlement.npc.NpcTalk;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import at.koopro.wizardsandbeasts.settlement.post.PostRules;
import at.koopro.wizardsandbeasts.settlement.post.SettlementPostBlockEntity;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementGround;
import at.koopro.wizardsandbeasts.treasure.SiteSearch;
import at.koopro.wizardsandbeasts.treasure.TreasureCharts;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Uses a generated hamlet: every counter, service and working block, through the entry points play uses, on real terrain,
 * then watches its people live for several in-game hours. A game test's scene is hand-built; only this finds what
 * generation left out, like a cauldron with no fire under it.
 *
 * <p>Development only, inert unless {@code WB_SETTLEMENT_LIFE} is set, on the {@code settlementProbe} run. The stand-in
 * customer is NeoForge's {@link FakePlayer}: server-side only, so this proves what the server decides and nothing about a
 * client. {@code life} does the work and saves; {@code return} runs on the same directory and reads back what survived.
 * Results go to {@code settlement_life_report.txt}. Each line starts PASS or FAIL.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SettlementLifeProbe {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = FMLEnvironment.isProduction() ? null : System.getenv("WB_SETTLEMENT_LIFE");
    private static final Path REPORT = Path.of("settlement_life_report.txt");
    private static final Path STATE = Path.of("settlement_life_state.txt");
    private static final Identifier APOTHECARY = id("apothecary");
    private static final Identifier OLLIVANDERS = id("ollivanders");
    private static final Identifier READING_ROOM = id("reading_room");
    private static final Identifier HAMLET = id("hamlet");
    /** Ticks to let forced chunks load and every post look. */
    private static final int STAFF_TICKS = 300;
    /** How long the people are watched: five in-game hours. */
    private static final int WATCH_TICKS = 5000;
    private static final int SAMPLE_TICKS = 20;
    /** A minute: enough to cross a plaza, open a door and stroll back to a counter. */
    private static final int WALK_HOME_TICKS = 1200;
    /** Blocks in a row without moving, while wanting to, that count as stuck. */
    private static final int STUCK_SAMPLES = 30;

    private enum Phase { NONE, STAFFING, WATCHING, WALKING_HOME, UNLOADED, RELOADED, AWAY, RETURNING, ALL_DONE }

    /** People struck just before the save, read back after the restart: who, at which post, and the dawn they wait for. */
    private record Away(PostRef post, String name, long dawn) {}

    private static final List<Away> awayAtRestart = new ArrayList<>();

    private static final List<String> lines = new ArrayList<>();
    private static final Map<UUID, Watch> watched = new HashMap<>();
    private static Phase phase = Phase.NONE;
    private static int wait;
    private static int failures;
    private static int passes;
    private static BoundingBox box = BoundingBox.infinite();
    private static BlockPos centre = BlockPos.ZERO;
    private static FakePlayer customer;
    private static Set<UUID> people = new HashSet<>();
    private static UUID struck;
    private static String struckName = "";
    private static long struckBack;
    private static UUID displacedWalker;
    private static UUID displacedStray;

    private SettlementLifeProbe() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static final class Watch {
        BlockPos home = BlockPos.ZERO;
        double furthest;
        int inWall;
        int inFluid;
        int stuck;
        int stillRun;
        int worstRun;
        BlockPos last = BlockPos.ZERO;
        final Set<Long> cells = new HashSet<>();
        String name = "";
    }

    private static void say(String line) {
        lines.add(line);
        LOGGER.info("[SettlementLife] {}", line);
    }

    private static void check(String what, boolean ok, Object detail) {
        if (ok) {
            passes++;
        } else {
            failures++;
        }
        say((ok ? "PASS " : "FAIL ") + what + ": " + detail);
    }

    private static void write() {
        try {
            Files.write(REPORT, lines, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            lines.clear();
        } catch (IOException e) {
            LOGGER.error("Life probe could not write", e);
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (MODE == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        try {
            if (MODE.equals("life")) {
                Files.deleteIfExists(REPORT);
                SiteSearch.Site site = TreasureCharts.nearest(level, SettlementGround.SETTLEMENTS,
                        level.getRespawnData().pos(), 12, s -> false);
                if (site == null) {
                    say("FAIL no hamlet within reach");
                    write();
                    server.halt(false);
                    return;
                }
                var chunk = level.getChunk(site.startX(), site.startZ(),
                        net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
                StructureStart start = chunk.getAllStarts().values().iterator().next();
                box = start.getBoundingBox();
                centre = box.getCenter();
                say("hamlet at " + centre.toShortString() + " seed " + level.getSeed());
            } else {
                List<String> state = Files.readAllLines(STATE);
                centre = BlockPos.of(Long.parseLong(state.get(0)));
                box = BoundingBox.fromCorners(centre.offset(-42, -32, -42), centre.offset(42, 32, 42));
            }
            force(level, true);
            phase = Phase.STAFFING;
            wait = STAFF_TICKS;
        } catch (IOException e) {
            LOGGER.error("Life probe failed", e);
            server.halt(false);
        }
    }

    private static void force(ServerLevel level, boolean on) {
        ChunkPos middle = new ChunkPos(centre);
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                level.setChunkForced(middle.x + x, middle.z + z, on);
            }
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (MODE == null || phase == Phase.NONE) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (phase == Phase.WATCHING && level.getGameTime() % SAMPLE_TICKS == 0) {
            sample(level);
        }
        if (--wait > 0) {
            return;
        }
        switch (phase) {
            case STAFFING -> {
                if (MODE.equals("return")) {
                    readBack(server, level);
                    if (awayAtRestart.isEmpty()) {
                        finish(server);
                        return;
                    }
                    // Ordinary time, run fast: the day runs to their dawn tick by tick, nothing set by hand.
                    long dawn = awayAtRestart.stream().mapToLong(Away::dawn).max().orElse(0);
                    wait = (int) Math.max(40, dawn - level.getDayTime() + 200);
                    say("waiting " + wait + " ticks for the dawn of those away at the restart");
                    write();
                    server.tickRateManager().requestGameToSprint(wait + 400);
                    phase = Phase.RETURNING;
                    return;
                }
                people = staff(level);
                check("five people stand in the hamlet", people.size() == 5, people.size());
                use(level);
                write();
                for (Entity person : everyone(level)) {
                    Watch watch = new Watch();
                    watch.home = person instanceof Mob mob ? mob.getHomePosition() : person.blockPosition();
                    watch.name = person.getName().getString();
                    watched.put(person.getUUID(), watch);
                }
                server.tickRateManager().requestGameToSprint(WATCH_TICKS + 4000);
                phase = Phase.WATCHING;
                wait = WATCH_TICKS;
            }
            case WATCHING -> {
                report(level);
                write();
                displace(level);
                phase = Phase.WALKING_HOME;
                wait = WALK_HOME_TICKS;
            }
            case WALKING_HOME -> {
                walkedHome(level);
                write();
                force(level, false);
                phase = Phase.UNLOADED;
                wait = 400;
            }
            case UNLOADED -> {
                check("chunks released: nobody is left loaded", everyone(level).isEmpty(), everyone(level).size());
                force(level, true);
                phase = Phase.RELOADED;
                wait = 400;
            }
            case RELOADED -> {
                Set<UUID> back = staff(level);
                check("after unload and reload the same five are here, once each", back.equals(people), back.size());
                for (Entity person : everyone(level)) {
                    if (person instanceof SettlementNpcEntity npc && struck == null) {
                        struck = npc.getUUID();
                        struckName = npc.getName().getString();
                        npc.hurtServer(level, level.damageSources().magic(), 1000f);
                        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(npc.blockPosition()).inflate(4.0));
                        check("a lethal blow removes them", npc.isRemoved(), struckName);
                        check("and drops nothing", drops.isEmpty(), drops.size());
                        SettlementData.Post post = SettlementData.get(level).post(
                                npc.getData(ModAttachments.SETTLEMENT_POST.get()));
                        check("recorded away", post != null && post.status() == PostRules.Status.AWAY, post);
                        struckBack = post == null ? 0 : post.backAt();
                    }
                }
                write();
                server.tickRateManager().requestGameToSprint(30000);
                phase = Phase.AWAY;
                wait = (int) Math.max(40, struckBack - level.getGameTime() + 200);
                say("waiting " + wait + " ticks for their return");
            }
            case AWAY -> {
                long dawnTicks = level.getDayTime() % 24000L;
                Entity same = everyone(level).stream()
                        .filter(e -> e.getName().getString().equals(struckName)).findFirst().orElse(null);
                check("the same person returned at their time", same != null, struckName);
                check("under a new body and nobody else's", same != null && !same.getUUID().equals(struck), same);
                check("the post holds one person each", staff(level).size() == 5, staff(level).size());
                say("day time at the check " + dawnTicks);
                server.tickRateManager().stopSprinting();
                strikeTwoBeforeTheSave(level);
                saveState(server, level);
                finish(server);
            }
            case RETURNING -> {
                for (Away away : awayAtRestart) {
                    long same = everyone(level).stream().filter(e -> e.getName().getString().equals(away.name())).count();
                    SettlementData.Post post = SettlementData.get(level).post(away.post());
                    check(away.name() + " is back once, at dawn, after the restart", same == 1 && post != null
                            && post.status() == PostRules.Status.PRESENT, same + " of them, record " + post);
                }
                check("the hamlet holds its five", staff(level).size() == 5, staff(level).size());
                say("day time at the check " + level.getDayTime() + ", dawn was " + awayAtRestart.stream().mapToLong(Away::dawn).max().orElse(0));
                server.tickRateManager().stopSprinting();
                finish(server);
            }
            default -> { }
        }
    }

    private static void finish(MinecraftServer server) {
        say("RESULT " + passes + " passed, " + failures + " failed");
        write();
        phase = Phase.NONE;
        server.halt(false);
    }

    private static List<Entity> everyone(ServerLevel level) {
        return level.getEntities((Entity) null, AABB.of(box).inflate(32.0),
                e -> e.hasData(ModAttachments.SETTLEMENT_POST.get()));
    }

    private static Set<UUID> staff(ServerLevel level) {
        Set<UUID> ids = new HashSet<>();
        everyone(level).forEach(e -> ids.add(e.getUUID()));
        return ids;
    }

    private static Entity host(ServerLevel level, String role) {
        for (Entity person : everyone(level)) {
            if (person instanceof SettlementNpcEntity npc ? npc.roleId().equals(id(role).toString())
                    : role.equals("teller") && person instanceof GoblinTellerEntity) {
                return person;
            }
        }
        return null;
    }

    private static void standBy(Entity host) {
        customer.setPos(host.getX() + 1.0, host.getY(), host.getZ());
    }

    private static List<BlockPos> find(ServerLevel level, java.util.function.Predicate<BlockState> what) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (what.test(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    private static PlayerVaultData vault() {
        return customer.getData(ModAttachments.VAULT_DATA.get());
    }

    private static MarketLine line(Identifier location, String good) {
        LocationView view = LocationProgression.view(customer, location);
        return view == null ? null : view.market().stream().filter(l -> !l.buying() && l.good().equals(good)).findFirst().orElse(null);
    }

    private static InteractionResult use(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).useWithoutItem(level, customer,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    // -- using the place ----------------------------------------------------------------------------------

    private static void use(ServerLevel level) {
        customer = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(new byte[] {7}), "Prober"));
        HeritageAPI.commit(customer, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        bank(level);
        apothecary(level);
        wandmaker(level);
        readingRoom(level);
        keeper(level);
        undercroft(level);
        chart(level);
    }

    private static void bank(ServerLevel level) {
        Entity teller = host(level, "teller");
        check("the bank has a goblin teller", teller instanceof GoblinTellerEntity, teller);
        if (!(teller instanceof GoblinTellerEntity goblin)) {
            return;
        }
        standBy(goblin);
        GringottsCounter.admit(customer, goblin);
        customer.getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), 7));
        long before = vault().getTotalInKnuts();
        VaultActionC2SPayload.perform(customer, new VaultActionC2SPayload(VaultActionC2SPayload.Action.DEPOSIT_SICKLE.ordinal(), 7));
        check("deposit: seven Sickles reach the vault", vault().getTotalInKnuts() - before == 203, vault().getTotalInKnuts() - before);
        check("deposit: the coins leave the pocket", customer.getInventory().countItem(CurrencyItemRegistry.SICKLE.get()) == 0, "");
        long total = vault().getTotalInKnuts();
        VaultActionC2SPayload.perform(customer, new VaultActionC2SPayload(VaultActionC2SPayload.Action.EXCHANGE_SICKLE_TO_KNUTS.ordinal(), 1));
        check("conversion changes coins, not worth", vault().getTotalInKnuts() == total, vault().getTotalInKnuts());
        VaultActionC2SPayload.perform(customer, new VaultActionC2SPayload(VaultActionC2SPayload.Action.WITHDRAW_KNUT.ordinal(), 29));
        check("withdrawal: coin comes back and the vault falls", customer.getInventory().countItem(CurrencyItemRegistry.KNUT.get()) == 29
                && vault().getTotalInKnuts() == total - 29, vault().getTotalInKnuts());
        customer.setPos(goblin.getX() + 40, goblin.getY(), goblin.getZ());
        long held = vault().getTotalInKnuts();
        VaultActionC2SPayload.perform(customer, new VaultActionC2SPayload(VaultActionC2SPayload.Action.WITHDRAW_KNUT.ordinal(), 1));
        check("no withdrawal from across the plaza", vault().getTotalInKnuts() == held, "");
    }

    private static void apothecary(ServerLevel level) {
        Entity keeper = host(level, "apothecary");
        check("the shop has its apothecary", keeper != null, keeper);
        if (keeper == null) {
            return;
        }
        standBy(keeper);
        check("the board opens", LocationProgression.openAtHost(customer, keeper), "");
        long broke = vault().getTotalInKnuts();
        vault().withdrawSmartKnuts(broke);
        check("an empty vault buys nothing", MarketTrade.buy(customer, APOTHECARY, "dittany", 1) == MarketTrade.Result.CANNOT_AFFORD,
                vault().getTotalInKnuts());
        vault().depositGalleons(60);
        MarketLine dittany = line(APOTHECARY, "dittany");
        check("dittany is on the shelf", dittany != null && dittany.count() > 0, dittany == null ? "none" : dittany.count());
        if (dittany != null) {
            int shelf = dittany.count();
            check("buying takes from this shelf", MarketTrade.buy(customer, APOTHECARY, "dittany", 2) == MarketTrade.Result.DONE
                    && line(APOTHECARY, "dittany").count() == shelf - 2, line(APOTHECARY, "dittany").count());
            for (int slot = 0; slot < customer.getInventory().getNonEquipmentItems().size(); slot++) {
                customer.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            int dropsBefore = level.getEntitiesOfClass(ItemEntity.class, customer.getBoundingBox().inflate(4.0)).size();
            MarketTrade.Result full = MarketTrade.buy(customer, APOTHECARY, "dittany", 1);
            int dropped = level.getEntitiesOfClass(ItemEntity.class, customer.getBoundingBox().inflate(4.0)).size() - dropsBefore;
            check("a full pocket drops the goods at the feet, never destroys them", full == MarketTrade.Result.DONE && dropped == 1, full);
            customer.getInventory().clearContent();
            level.getEntitiesOfClass(ItemEntity.class, customer.getBoundingBox().inflate(4.0)).forEach(Entity::discard);
            int left = line(APOTHECARY, "dittany").count();
            while (left > 0) {
                MarketTrade.Result result = MarketTrade.buy(customer, APOTHECARY, "dittany", Math.min(left, 8));
                if (result != MarketTrade.Result.DONE) {
                    break;
                }
                left = line(APOTHECARY, "dittany").count();
            }
            long held = vault().getTotalInKnuts();
            check("a shelf runs out", left == 0 && MarketTrade.buy(customer, APOTHECARY, "dittany", 1) == MarketTrade.Result.SOLD_OUT, left);
            check("sold out charges nothing", vault().getTotalInKnuts() == held, "");
        }
        List<BlockPos> cauldrons = find(level, s -> s.is(ModBlocks.PEWTER_CAULDRON.get()) || s.is(ModBlocks.BRASS_CAULDRON.get())
                || s.is(ModBlocks.WIZARDING_COPPER_CAULDRON.get()));
        check("three cauldrons", cauldrons.size() == 3, cauldrons.size());
        for (BlockPos pos : cauldrons) {
            check("cauldron on heat at " + pos.toShortString(), CauldronHeat.hasHeatSource(level, pos.below()),
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).getBlock()));
        }
        for (BlockPos pos : find(level, s -> s.is(ModBlocks.PEWTER_CAULDRON.get()))) {
            if (level.getBlockEntity(pos) instanceof CauldronBlockEntity pot) {
                pot.setFilled(true);
                pot.addIngredient(new ItemStack(Items.BLAZE_POWDER));
                pot.addIngredient(new ItemStack(Items.SUGAR));
                CauldronBlockEntity.StartResult started = pot.startBrewing(CauldronTier.PEWTER, customer.getUUID());
                check("a brew starts in the pewter cauldron", started == CauldronBlockEntity.StartResult.STARTED, started);
            }
        }
    }

    private static void wandmaker(ServerLevel level) {
        Entity wandmaker = host(level, "wandmaker");
        check("the shop has its wandmaker", wandmaker != null, wandmaker);
        if (wandmaker == null) {
            return;
        }
        standBy(wandmaker);
        LocationProgression.openAtHost(customer, wandmaker);
        LocationView view = LocationProgression.view(customer, OLLIVANDERS);
        check("the wand trial is on the board", view != null && view.services().stream()
                .anyMatch(s -> s.id().equals(id("wand_trial")) && s.available()), "");
        List<BlockPos> benches = find(level, s -> s.is(ModBlocks.WANDMAKERS_BENCH.get()));
        check("one bench", benches.size() == 1, benches.size());
        if (!benches.isEmpty()) {
            BlockPos pos = benches.get(0);
            customer.setPos(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
            InteractionResult used = use(level, pos);
            check("the bench answers", used.consumesAction(), used);
            if (level.getBlockEntity(pos) instanceof WandmakersBenchBlockEntity bench) {
                bench.getInventory().set(1, net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.STICK, 3)), 3);
                bench.setChanged();
                check("bench slot takes an item", bench.getInventory().getAmountAsInt(1) == 3, "");
            }
        }
    }

    private static void readingRoom(ServerLevel level) {
        Entity scholar = host(level, "scholar");
        check("the Reading Room has its scholar", scholar != null, scholar);
        if (scholar == null) {
            return;
        }
        standBy(scholar);
        LocationProgression.openAtHost(customer, scholar);
        LocationView view = LocationProgression.view(customer, READING_ROOM);
        for (String subject : List.of("astronomy", "defence_against_dark_arts", "history_of_magic")) {
            check("lesson offered: " + subject, view != null && view.services().stream()
                    .anyMatch(s -> s.id().equals(id("lesson_" + subject)) && s.available()), "");
        }
        check("a lesson is taught", LocationProgression.useService(customer, READING_ROOM, id("lesson_astronomy"))
                == LocationProgression.Result.DONE, "");
        check("one lesson a day", LocationProgression.useService(customer, READING_ROOM, id("lesson_history_of_magic"))
                == LocationProgression.Result.REFUSED, "");
        LocationDefinition def = Locations.open(READING_ROOM);
        check("nothing at the Reading Room sells a spell", def != null && def.market().isEmpty(), "");
        check("topic asked and answered", ask(READING_ROOM, "spells"), "");
        List<BlockPos> desks = find(level, s -> s.is(ModBlocks.EXAMINATION_DESK.get()));
        check("one examination desk", desks.size() == 1, desks.size());
        if (!desks.isEmpty()) {
            customer.setPos(desks.get(0).getX() + 1.5, desks.get(0).getY(), desks.get(0).getZ() + 0.5);
            check("the desk counts as a desk", RequestOWLExamPacket.atExaminationDesk(customer), "");
            check("the desk answers", use(level, desks.get(0)).consumesAction(), "");
        }
        List<BlockPos> lecterns = find(level, s -> s.is(ModBlocks.SPELL_TEACHER.get()));
        check("one Study Lectern", lecterns.size() == 1, lecterns.size());
        List<BlockPos> chests = find(level, s -> s.is(Blocks.CHEST));
        int stocked = 0;
        for (BlockPos pos : chests) {
            stocked += level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.getLootTable() != null ? 1 : 0;
        }
        check("two chests with loot to find", chests.size() == 2 && stocked == 2, stocked + "/" + chests.size());
    }

    private static boolean ask(Identifier location, String topic) {
        NpcTalk.ask(customer, location, topic);
        LocationView view = LocationProgression.view(customer, location);
        return view != null && !view.talk().reply().getString().isEmpty();
    }

    private static void keeper(ServerLevel level) {
        Entity keeper = host(level, "keeper");
        check("the post has its keeper", keeper != null, keeper);
        if (keeper == null) {
            return;
        }
        standBy(keeper);
        LocationProgression.openAtHost(customer, keeper);
        LocationView view = LocationProgression.view(customer, HAMLET);
        check("the board offers work", view != null && !view.offers().isEmpty(), view == null ? 0 : view.offers().size());
        check("the keeper greets", view != null && !view.talk().greeting().getString().isEmpty(), "");
        SettlementDiscovery.arrive(customer, keeper.getData(ModAttachments.SETTLEMENT_POST.get()).site());
        check("arrival recorded once", customer.getData(ModAttachments.KNOWN_PLACES.get()).sites().size() == 1, "");
    }

    private static void undercroft(ServerLevel level) {
        List<BlockPos> seals = find(level, s -> s.is(ModBlocks.CHAMBER_SEAL.get()));
        check("the seal is two blocks", seals.size() == 2, seals.size());
        if (seals.isEmpty()) {
            return;
        }
        BlockPos seal = seals.get(0);
        customer.setPos(seal.getX() + 1.5, seal.getY(), seal.getZ() + 0.5);
        boolean wasOpen = level.getBlockState(seal).getValue(ChamberSealBlock.OPEN);
        level.setBlockAndUpdate(seal, level.getBlockState(seal).cycle(ChamberSealBlock.OPEN));
        check("Alohomora's rule opens the seal", !wasOpen && level.getBlockState(seal).getValue(ChamberSealBlock.OPEN), "");
        check("both blocks open", level.getBlockState(seals.get(1)).getValue(ChamberSealBlock.OPEN), "");
        check("no Chamber stage comes of it", !ChamberQuestService.quest(customer).has(ChamberStage.SEAL_OPENED), "");
    }

    /** The chart's search as {@code HamletCharts} runs it, a cell at a time, on real terrain. */
    private static void chart(ServerLevel level) {
        BlockPos from = level.getRespawnData().pos();
        SiteSearch.Walk walk = TreasureCharts.walk(level, SettlementGround.SETTLEMENTS, from, 12);
        check("the walk can start", walk != null, "");
        if (walk == null) {
            return;
        }
        long worst = 0;
        long total = 0;
        int cells = 0;
        while (!walk.finished()) {
            long before = System.nanoTime();
            walk.step();
            long took = System.nanoTime() - before;
            worst = Math.max(worst, took);
            total += took;
            cells++;
            // The probe holds the tick, so it runs the chunk source's own queue for what the walk is waiting on.
            if (walk.waiting()) {
                level.getChunkSource().pollTask();
            }
        }
        check("the walk finds a hamlet", walk.found() != null, walk.found());
        say(String.format("  chart walk: %d cells, %.0f ms in all, worst single cell %.1f ms", cells, total / 1e6, worst / 1e6));
        if (walk.found() != null) {
            var chunk = level.getChunk(walk.found().startX(), walk.found().startZ(),
                    net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
            check("the X is over a real hamlet", chunk.getAllStarts().keySet().stream().anyMatch(structure -> level.registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).wrapAsHolder(structure)
                    .is(SettlementGround.SETTLEMENTS)), "");
        }
    }

    // -- watching them ------------------------------------------------------------------------------------

    private static void sample(ServerLevel level) {
        for (Entity person : everyone(level)) {
            Watch watch = watched.get(person.getUUID());
            if (watch == null || !(person instanceof LivingEntity living) || !(person instanceof Mob mob)) {
                continue;
            }
            BlockPos at = person.blockPosition();
            watch.furthest = Math.max(watch.furthest, Math.sqrt(at.distSqr(watch.home)));
            watch.cells.add(at.asLong());
            watch.inWall += living.isInWall() ? 1 : 0;
            watch.inFluid += person.isInLiquid() ? 1 : 0;
            if (at.equals(watch.last) && mob.getNavigation().isInProgress()) {
                watch.stillRun++;
                watch.worstRun = Math.max(watch.worstRun, watch.stillRun);
                watch.stuck += watch.stillRun == STUCK_SAMPLES ? 1 : 0;
            } else {
                watch.stillRun = 0;
            }
            watch.last = at;
        }
    }

    /**
     * Sends one person across the plaza and one far beyond it. The first has to find their own way back through a door;
     * the second is past walking, and is for the post to bring home, as nobody is watching.
     */
    private static void displace(ServerLevel level) {
        Entity walker = host(level, "scholar");
        Entity stray = host(level, "keeper");
        if (walker instanceof Mob mob) {
            BlockPos home = mob.getHomePosition();
            Vec3 toward = Vec3.atCenterOf(centre).subtract(Vec3.atCenterOf(home)).normalize();
            BlockPos out = home.offset((int) (toward.x * 16), 1, (int) (toward.z * 16));
            walker.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
            displacedWalker = walker.getUUID();
            say("sent " + walker.getName().getString() + " " + (int) Math.sqrt(out.distSqr(home)) + " blocks from home, onto the plaza");
        }
        if (stray instanceof Mob mob) {
            BlockPos home = mob.getHomePosition();
            stray.teleportTo(home.getX() + 30.5, home.getY() + 1.0, home.getZ() + 0.5);
            displacedStray = stray.getUUID();
            say("sent " + stray.getName().getString() + " 30 blocks past their home");
        }
    }

    private static void walkedHome(ServerLevel level) {
        if (level.getEntity(displacedWalker) instanceof Mob mob) {
            check("a person left on the plaza walks home through their door", mob.isWithinHome(),
                    Math.sqrt(mob.blockPosition().distSqr(mob.getHomePosition())));
        } else {
            check("a person left on the plaza is still there to walk", false, displacedWalker);
        }
        if (level.getEntity(displacedStray) instanceof Mob mob) {
            check("a person far past home is brought back by their post", mob.isWithinHome(),
                    Math.sqrt(mob.blockPosition().distSqr(mob.getHomePosition())));
        } else {
            check("a person far past home is still there", false, displacedStray);
        }
    }

    private static void report(ServerLevel level) {
        check("nobody left in five hours", staff(level).equals(people), staff(level).size());
        for (Map.Entry<UUID, Watch> entry : new TreeMap<>(watched).entrySet()) {
            Watch watch = entry.getValue();
            say(String.format("  %s: furthest %.1f from post, %d cells walked, %d samples in a wall, %d in water, stuck spells %d, longest still-wanting %d",
                    watch.name, watch.furthest, watch.cells.size(), watch.inWall, watch.inFluid, watch.stuck, watch.worstRun));
            check(watch.name + " stays near their post", watch.furthest <= PostRules.STRAY_BLOCKS, watch.furthest);
            check(watch.name + " is never in a wall", watch.inWall == 0, watch.inWall);
            check(watch.name + " is never stuck", watch.stuck == 0, watch.stuck);
        }
    }

    // -- across a restart ---------------------------------------------------------------------------------

    /** Two of the people take a lethal blow right before the save, so the server stops with both away. */
    private static void strikeTwoBeforeTheSave(ServerLevel level) {
        for (String role : List.of("keeper", "scholar")) {
            Entity person = host(level, role);
            if (person == null) {
                check(role + " is there to strike before the save", false, "");
                continue;
            }
            PostRef ref = person.getData(ModAttachments.SETTLEMENT_POST.get());
            String name = person.getName().getString();
            ((net.minecraft.world.entity.LivingEntity) person).hurtServer(level, level.damageSources().magic(), 1000f);
            SettlementData.Post post = SettlementData.get(level).post(ref);
            check(name + " is away when the server stops", post != null && post.status() == PostRules.Status.AWAY, post);
            if (post != null) {
                awayAtRestart.add(new Away(ref, name, post.dawn()));
            }
        }
        say("struck before the save at day time " + level.getDayTime());
    }

    private static void saveState(MinecraftServer server, ServerLevel level) {
        List<String> state = new ArrayList<>(List.of(Long.toString(centre.asLong())));
        for (Entity person : everyone(level)) {
            state.add("person " + person.getUUID() + " " + person.getName().getString());
        }
        for (Away away : awayAtRestart) {
            state.add("away " + away.dawn() + " " + away.post().post() + " " + away.post().site() + " " + away.name());
        }
        for (BlockPos pos : find(level, s -> s.is(ModBlocks.WANDMAKERS_BENCH.get()))) {
            state.add("bench " + pos.asLong());
        }
        for (BlockPos pos : find(level, s -> s.is(ModBlocks.CHAMBER_SEAL.get()))) {
            state.add("seal " + pos.asLong());
        }
        Entity apothecary = host(level, "apothecary");
        if (apothecary != null) {
            standBy(apothecary);
            LocationProgression.openAtHost(customer, apothecary);
            MarketLine line = line(APOTHECARY, "dittany");
            state.add("dittany " + (line == null ? -1 : line.count()) + " " + level.getGameTime());
        }
        try {
            Files.write(STATE, state);
        } catch (IOException e) {
            LOGGER.error("Life probe could not save", e);
        }
        server.saveEverything(false, true, true);
    }

    private static void readBack(MinecraftServer server, ServerLevel level) {
        try {
            List<String> state = Files.readAllLines(STATE);
            customer = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(new byte[] {7}), "Prober"));
            Set<String> names = new HashSet<>();
            everyone(level).forEach(e -> names.add(e.getUUID() + " " + e.getName().getString()));
            int expected = 0;
            for (String line : state.subList(1, state.size())) {
                String[] parts = line.split(" ", 2);
                switch (parts[0]) {
                    case "person" -> {
                        expected++;
                        check("came back: " + parts[1], names.contains(parts[1]), "");
                    }
                    case "bench" -> check("bench slot survived", level.getBlockEntity(BlockPos.of(Long.parseLong(parts[1])))
                            instanceof WandmakersBenchBlockEntity bench && bench.getInventory().getAmountAsInt(1) == 3, "");
                    case "seal" -> check("seal stayed open", level.getBlockState(BlockPos.of(Long.parseLong(parts[1])))
                            .getValue(ChamberSealBlock.OPEN), "");
                    case "away" -> {
                        String[] values = parts[1].split(" ", 4);
                        Away away = new Away(new PostRef(values[2], Long.parseLong(values[1])), values[3], Long.parseLong(values[0]));
                        SettlementData.Post post = SettlementData.get(level).post(away.post());
                        check(away.name() + " is still away after the restart, waiting for the same dawn", post != null
                                && post.status() == PostRules.Status.AWAY && post.dawn() == away.dawn(), post);
                        check(away.name() + " was not brought back early", everyone(level).stream()
                                .noneMatch(e -> e.getName().getString().equals(away.name())), "day time " + level.getDayTime());
                        awayAtRestart.add(away);
                    }
                    case "dittany" -> {
                        String[] values = parts[1].split(" ");
                        Entity apothecary = host(level, "apothecary");
                        if (apothecary != null) {
                            standBy(apothecary);
                            LocationProgression.openAtHost(customer, apothecary);
                            MarketLine now = line(APOTHECARY, "dittany");
                            int was = Integer.parseInt(values[0]);
                            check("the shelf came through as sold, plus what time restocked", now != null && now.count() >= was
                                    && now.count() <= was + 4, was + " -> " + (now == null ? "?" : now.count())
                                    + " after " + (level.getGameTime() - Long.parseLong(values[1])) + " ticks");
                        }
                    }
                    default -> { }
                }
            }
            check("nobody extra", names.size() == expected, names.size() + " of " + expected);
        } catch (IOException e) {
            LOGGER.error("Life probe failed", e);
        }
    }
}
