package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementGround;
import at.koopro.wizardsandbeasts.treasure.SiteSearch;
import at.koopro.wizardsandbeasts.treasure.TreasureCharts;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Runs a hamlet with two real clients on a real dedicated server: two game processes, each with its own renderer and its
 * own TCP connection. No mock connection is involved. The server decides and is checked here; each client acts through
 * its own keys and screens ({@code client/debug/HamletPlaytest}, part {@code mp}), told what to do through a file, so a
 * race between them is a real one on the wire.
 *
 * <p>Development only, inert unless {@code WB_MP_PROBE} names the shared directory. Results go to {@code mp_report.txt}
 * in it, each line PASS, FAIL or NOTE.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SettlementMpProbe {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SHARED = FMLEnvironment.isProduction() ? null : System.getenv("WB_MP_PROBE");
    private static final int STAFF_RANGE = 32;

    private static MinecraftServer server;
    private static Path dir;
    private static BoundingBox box;
    private static int sequence;

    private SettlementMpProbe() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (SHARED == null) {
            return;
        }
        server = event.getServer();
        dir = Path.of(SHARED);
        Thread script = new Thread(SettlementMpProbe::run, "wb-mp-probe");
        script.setDaemon(true);
        script.start();
    }

    private static void say(String line) {
        LOGGER.info("[MpProbe] {}", line);
        try {
            Files.writeString(dir.resolve("mp_report.txt"), line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("MP probe: cannot write the report", e);
        }
    }

    private static void check(String what, boolean ok, Object detail) {
        say((ok ? "PASS " : "FAIL ") + what + (String.valueOf(detail).isEmpty() ? "" : " :: " + detail));
    }

    private static <T> T onServer(Function<ServerLevel, T> job) {
        CompletableFuture<T> done = new CompletableFuture<>();
        server.execute(() -> {
            try {
                done.complete(job.apply(server.overworld()));
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            return done.get(300, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("server step failed", e);
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static int online() {
        return onServer(level -> server.getPlayerList().getPlayerCount());
    }

    /** Moves a written command into place. Windows refuses the move while the client has the old file open, so it retries. */
    private static void post(Path temporary, Path target) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (java.nio.file.AccessDeniedException e) {
                if (attempt > 50) {
                    throw e;
                }
                pause(50);
            }
        }
    }

    /** Gives a client a line to act on and returns its answer. The file is moved into place, so it is never read half written. */
    private static String tell(String name, String verb) {
        int number = ++sequence;
        try {
            Path temporary = dir.resolve(name + ".cmd.tmp");
            Files.writeString(temporary, number + " " + verb, StandardCharsets.UTF_8);
            post(temporary, dir.resolve(name + ".cmd"));
            Path ack = dir.resolve(name + ".ack");
            long giveUp = System.currentTimeMillis() + 240_000;
            while (System.currentTimeMillis() < giveUp) {
                if (Files.exists(ack)) {
                    String answer = Files.readString(ack, StandardCharsets.UTF_8);
                    if (answer.startsWith(number + " ")) {
                        return answer.substring(answer.indexOf(' ') + 1);
                    }
                }
                pause(100);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return "NO ANSWER";
    }

    /** Both clients told at once, on two threads, so what they do lands on the server as close together as the wire allows. */
    private static List<String> tellBoth(String one, String verbOne, String two, String verbTwo) {
        int first = ++sequence;
        int second = ++sequence;
        try {
            Path a = dir.resolve(one + ".cmd.tmp");
            Path b = dir.resolve(two + ".cmd.tmp");
            Files.writeString(a, first + " " + verbOne, StandardCharsets.UTF_8);
            Files.writeString(b, second + " " + verbTwo, StandardCharsets.UTF_8);
            post(a, dir.resolve(one + ".cmd"));
            post(b, dir.resolve(two + ".cmd"));
            List<String> answers = new ArrayList<>();
            long giveUp = System.currentTimeMillis() + 240_000;
            String[] names = {one, two};
            int[] numbers = {first, second};
            for (int i = 0; i < 2; i++) {
                String answer = "NO ANSWER";
                while (System.currentTimeMillis() < giveUp) {
                    Path ack = dir.resolve(names[i] + ".ack");
                    if (Files.exists(ack)) {
                        String read = Files.readString(ack, StandardCharsets.UTF_8);
                        if (read.startsWith(numbers[i] + " ")) {
                            answer = read.substring(read.indexOf(' ') + 1);
                            break;
                        }
                    }
                    pause(100);
                }
                answers.add(answer);
            }
            return answers;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ServerPlayer player(String name) {
        return server.getPlayerList().getPlayerByName(name);
    }

    private static long vault(String name) {
        return onServer(level -> player(name).getData(ModAttachments.VAULT_DATA.get()).getTotalInKnuts());
    }

    private static int pocket(String name, net.minecraft.world.item.Item item) {
        return onServer(level -> player(name).getInventory().countItem(item));
    }

    /** Vault and pocket together, in Knuts: what no counter action may change. */
    private static long worth(String name) {
        return vault(name) + pocket(name, CurrencyItemRegistry.KNUT.get())
                + pocket(name, CurrencyItemRegistry.SICKLE.get()) * 29L + pocket(name, CurrencyItemRegistry.GALLEON.get()) * 493L;
    }

    private static List<Entity> staff(ServerLevel level) {
        return level.getEntities((Entity) null, AABB.of(box).inflate(STAFF_RANGE), e -> e.hasData(ModAttachments.SETTLEMENT_POST.get()));
    }

    private static Entity host(ServerLevel level, String role) {
        for (Entity person : staff(level)) {
            if (person instanceof SettlementNpcEntity npc ? npc.roleId().endsWith(":" + role)
                    : role.equals("teller") && person instanceof GoblinTellerEntity) {
                return person;
            }
        }
        return null;
    }

    /** Both players stood a couple of blocks from {@code role}, on the side they would walk up to, facing them. */
    private static String standAt(String role, String... names) {
        return onServer(level -> {
            Entity person = host(level, role);
            if (person == null) {
                return "";
            }
            Vec3 toward = Vec3.atCenterOf(box.getCenter().atY(person.blockPosition().getY())).subtract(person.position()).normalize();
            // Side by side and apart, so neither stands in the other's line of sight to the person.
            double side = names.length == 1 ? 0.0 : -1.3;
            for (String name : names) {
                Vec3 at = person.position().add(toward.scale(2.5)).add(toward.z * side, 0, -toward.x * side);
                ServerPlayer player = player(name);
                player.teleportTo(level, at.x, at.y, at.z, Set.of(), 0f, 0f, false);
                side = -side;
            }
            return person.getUUID().toString();
        });
    }

    private static void run() {
        try {
            String one = System.getenv().getOrDefault("WB_MP_ONE", "AlphaOne");
            String two = System.getenv().getOrDefault("WB_MP_TWO", "AlphaTwo");
            say("NOTE two real client processes on one dedicated server, over TCP; no mock connection");
            // The hamlet's land is generated before anyone joins: on a loaded machine generating it under two players
            // held the server past the clients' keep-alive, and both timed out.
            findSite();
            onServer(level -> {
                ChunkPos middle = new ChunkPos(box.getCenter());
                for (int x = -5; x <= 5; x++) {
                    for (int z = -5; z <= 5; z++) {
                        level.getChunkSource().addTicketWithRadius(TicketType.FORCED, new ChunkPos(middle.x + x, middle.z + z), 2);
                    }
                }
                return null;
            });
            long ready = System.currentTimeMillis() + 900_000;
            while (onServer(level -> staff(level).size()) < 5 && System.currentTimeMillis() < ready) {
                pause(2000);
            }
            say("NOTE generated and staffed before the clients joined: " + onServer(level -> staff(level).size()) + " people");
            long giveUp = System.currentTimeMillis() + 3_600_000;
            while (online() < 2 && System.currentTimeMillis() < giveUp) {
                pause(1000);
            }
            check("two clients are on the server", online() == 2, online());
            onServer(level -> {
                BlockPos centre = box.getCenter();
                for (String name : List.of(one, two)) {
                    ServerPlayer player = player(name);
                    if (!HeritageAPI.hasHeritageSelected(player)) {
                        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
                    }
                    player.getData(ModAttachments.VAULT_DATA.get()).depositGalleons(60);
                    player.getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), 10));
                    player.teleportTo(level, centre.getX() + 6.5, box.minY() + 30.0, centre.getZ() + 6.5, Set.of(), 0f, 0f, false);
                }
                level.setDayTime(6000);
                return null;
            });
            tell(one, "close");
            tell(two, "close");
            long staffBy = System.currentTimeMillis() + 300_000;
            while (onServer(level -> staff(level).size()) < 5 && System.currentTimeMillis() < staffBy) {
                pause(1000);
            }
            check("five people stand in the hamlet with both players there", onServer(level -> staff(level).size()) == 5,
                    onServer(level -> staff(level).size()));

            // WB_MP_STEPS=boards runs the shop boards alone, for a rerun of just that part.
            if ("boards".equals(System.getenv("WB_MP_STEPS"))) {
                sameShelf(one, two);
                boardFollows(one, two);
                sharedShelfRace(one, two);
                say("DONE");
                return;
            }
            cellarDry();
            sameShelf(one, two);
            boardFollows(one, two);
            sharedShelfRace(one, two);
            bank(one, two);
            ownBoards(one, two);
            apparition(one, two);
            reconnect(one, two);
            leaveAndReturn(one, two);
            say("DONE");
        } catch (Throwable t) {
            LOGGER.error("MP probe died", t);
            say("FAIL the script died: " + t);
        } finally {
            server.execute(() -> server.halt(false));
        }
    }

    private static void findSite() {
        boolean found = onServer(level -> {
            String chosen = System.getenv("WB_MP_CHUNK");
            int cx;
            int cz;
            if (chosen != null) {
                cx = Integer.parseInt(chosen.split(",")[0]);
                cz = Integer.parseInt(chosen.split(",")[1]);
            } else {
                SiteSearch.Site site = TreasureCharts.nearest(level, SettlementGround.SETTLEMENTS, level.getRespawnData().pos(), 16, s -> false);
                if (site == null) {
                    return false;
                }
                cx = site.startX();
                cz = site.startZ();
            }
            for (StructureStart start : level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS).getAllStarts().values()) {
                if (level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .wrapAsHolder(start.getStructure()).is(SettlementGround.SETTLEMENTS)) {
                    box = start.getBoundingBox();
                    return true;
                }
            }
            return false;
        });
        check("a natural hamlet is on this seed", found, found ? box : "");
        if (!found) {
            throw new IllegalStateException("no hamlet");
        }
    }

    /**
     * The cellar after its fluids have ticked with players near: no water and nothing waterlogged under the shop. A site
     * whose cellar was dug into an aquifer flooded this way, from ladders and lanterns placed holding water.
     */
    private static void cellarDry() {
        pause(30_000);
        String wet = onServer(level -> {
            BlockPos seal = null;
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (level.getBlockState(pos).is(at.koopro.wizardsandbeasts.registry.ModBlocks.CHAMBER_SEAL.get())) {
                    seal = pos.immutable();
                    break;
                }
            }
            if (seal == null) {
                return "no seal";
            }
            int water = 0;
            int logged = 0;
            for (BlockPos pos : BlockPos.betweenClosed(seal.offset(-9, -2, -9), seal.offset(9, 4, 9))) {
                if (!level.getFluidState(pos).isEmpty()) {
                    water++;
                    logged += level.getBlockState(pos).hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) ? 1 : 0;
                }
            }
            return "seal " + seal.toShortString() + ", water " + water + ", waterlogged " + logged;
        });
        say("NOTE around the cellar after 30 s of ticking near players: " + wet);
    }

    /** Two players at one person: both boards open, and the second does not shut or steal the first. */
    private static void sameShelf(String one, String two) {
        String id = standAt("apothecary", one, two);
        check("the apothecary is there", !id.isEmpty(), id);
        pause(4000);
        List<String> opened = tellBoth(one, "speak " + id, two, "speak " + id);
        check("two players open the same person's board at once", opened.stream().allMatch(a -> a.contains("LocationScreen")), opened);
        tell(one, "shot both_at_apothecary_one");
        tell(two, "shot both_at_apothecary_two");
    }

    /** One buys while the other only watches: the watcher's open board follows, though they did nothing (alpha defect 5). */
    private static void boardFollows(String one, String two) {
        String good = tell(one, "firstgood");
        int before = Integer.parseInt(tell(two, "shelf " + good));
        long paid = vault(one);
        tell(one, "buy " + good);
        pause(1500);
        int buyerReads = Integer.parseInt(tell(one, "shelf " + good));
        int watcherReads = Integer.parseInt(tell(two, "shelf " + good));
        check("the watcher's open board follows a purchase it did not make", watcherReads == buyerReads && buyerReads == before - 1,
                good + ": before " + before + ", buyer reads " + buyerReads + ", watcher reads " + watcherReads
                        + ", paid " + (paid - vault(one)));
        tell(two, "shot board_followed_two");
    }

    /** One shelf, two buyers in the same instant: the shelf falls by what was really sold, and nobody pays for nothing. */
    private static void sharedShelfRace(String one, String two) {
        String good = tell(one, "firstgood");
        check("there is something on the shelf", !good.isEmpty() && !good.equals("none"), good);
        int before = Integer.parseInt(tell(one, "shelf " + good));
        long vaultOne = vault(one);
        long vaultTwo = vault(two);
        for (int round = 0; round < 3; round++) {
            tellBoth(one, "buy " + good, two, "buy " + good);
        }
        pause(1500);
        int after = Integer.parseInt(tell(one, "shelf " + good));
        int seenByTwo = Integer.parseInt(tell(two, "shelf " + good));
        long paidOne = vaultOne - vault(one);
        long paidTwo = vaultTwo - vault(two);
        say("NOTE shelf " + before + " -> " + after + ", the other client reads " + seenByTwo + "; paid " + paidOne + " and " + paidTwo);
        check("the shelf is shared and fell by exactly what was bought", before - after == Math.min(before, 6), before + " -> " + after);
        check("both clients read the same shelf", after == seenByTwo, after + " / " + seenByTwo);
        check("nobody paid for what they did not get", paidOne > 0 && paidTwo > 0, paidOne + " / " + paidTwo);
        tell(one, "close");
        tell(two, "close");
    }

    private static void bank(String one, String two) {
        String id = standAt("teller", one, two);
        check("the teller is there", !id.isEmpty(), id);
        pause(4000);
        List<String> opened = tellBoth(one, "speak " + id, two, "speak " + id);
        check("two players at one teller", opened.stream().allMatch(a -> a.contains("GringottsScreen")), opened);
        long startOne = vault(one);
        long startTwo = vault(two);
        int sickles = pocket(one, CurrencyItemRegistry.SICKLE.get());
        tellBoth(one, "ledger 2 10", two, "ledger 2 10");
        pause(1500);
        check("deposits by two players land apart", vault(one) - startOne == 290 && vault(two) - startTwo == 290,
                (vault(one) - startOne) + " / " + (vault(two) - startTwo));
        check("the coins left the first pocket", pocket(one, CurrencyItemRegistry.SICKLE.get()) == sickles - 10, pocket(one, CurrencyItemRegistry.SICKLE.get()));
        long midOne = vault(one);
        long midTwo = vault(two);
        tellBoth(one, "ledger 5 29", two, "ledger 5 29");
        pause(1500);
        check("withdrawals by two players land apart", midOne - vault(one) == 29 && midTwo - vault(two) == 29,
                (midOne - vault(one)) + " / " + (midTwo - vault(two)));
        // The lag case made certain: every click of a burst leaves the client in one tick, so the server gets them together.
        onServer(level -> {
            player(one).getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), 10));
            return null;
        });
        long worthOne = worth(one);
        long worthTwo = worth(two);
        long burstOne = vault(one);
        long burstTwo = vault(two);
        int knutsTwo = pocket(two, CurrencyItemRegistry.KNUT.get());
        tellBoth(one, "burst 2 10", two, "burst 5 29");
        pause(2000);
        check("ten Sickle clicks in one client tick all land", vault(one) - burstOne == 290
                        && pocket(one, CurrencyItemRegistry.SICKLE.get()) == 0,
                (vault(one) - burstOne) + " Knuts in, " + pocket(one, CurrencyItemRegistry.SICKLE.get()) + " Sickles left");
        check("twenty-nine Knut clicks in one client tick all land", burstTwo - vault(two) == 29
                        && pocket(two, CurrencyItemRegistry.KNUT.get()) - knutsTwo == 29,
                (burstTwo - vault(two)) + " Knuts out, pocket +" + (pocket(two, CurrencyItemRegistry.KNUT.get()) - knutsTwo));
        check("no coin made or lost in the bursts", worth(one) == worthOne && worth(two) == worthTwo,
                worthOne + " -> " + worth(one) + " / " + worthTwo + " -> " + worth(two));
        tell(one, "shot both_at_teller");
        tell(one, "close");
        tell(two, "close");
    }

    private static void ownBoards(String one, String two) {
        String id = standAt("keeper", one, two);
        check("the keeper is there", !id.isEmpty(), id);
        pause(4000);
        tellBoth(one, "speak " + id, two, "speak " + id);
        String accepted = tell(one, "accept");
        pause(1000);
        String heldOne = tell(one, "held");
        String heldTwo = tell(two, "held");
        check("a contract taken by one player is theirs alone", !accepted.equals("none") && heldOne.contains(accepted) && !heldTwo.contains(accepted),
                accepted + " / one holds " + heldOne + " / two holds " + heldTwo);
        tell(one, "close");
        tell(two, "close");
    }

    /** A lethal blow on the server: both clients lose the person at once, and the same person is back at dawn. */
    private static void apparition(String one, String two) {
        String id = standAt("wandmaker", one, two);
        pause(4000);
        check("both clients see the wandmaker", tell(one, "sees " + id).equals("true") && tell(two, "sees " + id).equals("true"), id);
        String[] where = new String[1];
        String name = onServer(level -> {
            Entity person = host(level, "wandmaker");
            String who = person.getDisplayName().getString();
            wandmakerPost = person.getData(ModAttachments.SETTLEMENT_POST.get());
            AABB near = person.getBoundingBox().inflate(6.0);
            int before = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, near).size();
            ((net.minecraft.world.entity.LivingEntity) person).hurtServer(level, level.damageSources().magic(), 1000f);
            int after = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, near).size();
            where[0] = before + " -> " + after + " items within 6 blocks, removed " + person.isRemoved();
            return who;
        });
        pause(2000);
        check("the blow took them from both clients", tell(one, "sees " + id).equals("false") && tell(two, "sees " + id).equals("false"), name);
        check("no body and no drop on the server", where[0].contains("removed true") && where[0].startsWith(where[0].split(" ")[0] + " -> "
                + where[0].split(" ")[0] + " "), where[0]);
        String record = onServer(level -> {
            SettlementData.Post post = staffPost(level, "wandmaker");
            return post == null ? "no record" : "status " + post.status() + ", dawn at day time " + post.dawn()
                    + " (now " + level.getDayTime() + "), backstop in " + (post.backAt() - level.getGameTime()) + " game ticks";
        });
        say("NOTE the record after the blow: " + record);
        // The night slept through as players sleep it: evening falls, a bed each, both clients right-click theirs, and the
        // server wakes everyone at dawn. Day time jumps; game time does not.
        long[] struckGame = new long[1];
        String beds = onServer(level -> {
            struckGame[0] = level.getGameTime();
            // Setup: no monster may keep a player from resting, and none matters to this check.
            server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            level.setDayTime(level.getDayTime() / 24000L * 24000L + 14_000L);
            StringBuilder laid = new StringBuilder();
            for (String sleeper : List.of(one, two)) {
                BlockPos bed = bedFor(level, player(sleeper).blockPosition());
                laid.append(sleeper).append(' ').append(bed == null ? "none" : bed.getX() + " " + bed.getY() + " " + bed.getZ()).append(';');
            }
            return laid.toString();
        });
        say("NOTE beds set down for the night: " + beds);
        pause(3000);
        List<String> asleep = new ArrayList<>();
        for (String entry : beds.split(";")) {
            String[] parts = entry.split(" ");
            if (parts.length == 4) {
                asleep.add(parts[0] + ": " + tell(parts[0], "sleep " + parts[1] + " " + parts[2] + " " + parts[3]));
            }
        }
        check("both players lie down in their beds", asleep.size() == 2 && asleep.stream().allMatch(a -> a.endsWith("asleep true")), asleep);
        long wakeBy = System.currentTimeMillis() + 120_000;
        while (!(tell(one, "awake").equals("true") && tell(two, "awake").equals("true")) && System.currentTimeMillis() < wakeBy) {
            pause(1000);
        }
        String woke = onServer(level -> "day time " + level.getDayTime() + ", " + (level.getGameTime() - struckGame[0])
                + " game ticks since the blow");
        String waits = onServer(level -> {
            SettlementData.Post post = staffPost(level, "wandmaker");
            return post == null ? "no record" : "status " + post.status() + ", dawn " + post.dawn() + ", day time now " + level.getDayTime();
        });
        say("NOTE woke: " + woke + "; record " + waits);
        onServer(level -> {
            server.setDifficulty(net.minecraft.world.Difficulty.EASY, true);
            return null;
        });
        long back = System.currentTimeMillis() + 120_000;
        String again = "";
        while (again.isEmpty() && System.currentTimeMillis() < back) {
            again = backAgain();
            pause(1000);
        }
        check("the person is back at the dawn the players slept to", again.startsWith(name + "|"), again.isEmpty() ? "not back: " + waits : again);
        check("their return came from the sleep, not from game time", onServer(level -> {
            SettlementData.Post post = staffPost(level, "wandmaker");
            return level.getGameTime() - struckGame[0] < 12_000 && post != null && post.status() == at.koopro.wizardsandbeasts.settlement.post.PostRules.Status.PRESENT;
        }), woke);
        pause(3000);
        if (!again.isEmpty()) {
            String seen = again.substring(again.indexOf('|') + 1);
            check("both clients see them back", tell(one, "sees " + seen).equals("true") && tell(two, "sees " + seen).equals("true"), seen);
        }
    }

    /**
     * Sets down a bed within reach of {@code near}, on open floor, and puts the player on its foot looking at it; returns
     * the foot, or null where no floor would take one.
     */
    private static BlockPos bedFor(ServerLevel level, BlockPos near) {
        for (BlockPos foot : BlockPos.withinManhattan(near, 4, 1, 4)) {
            BlockPos head = foot.east();
            BlockPos stand = foot.west();
            if (open(level, foot) && open(level, head) && open(level, stand)) {
                foot = foot.immutable();
                var bed = net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST);
                level.setBlockAndUpdate(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.FOOT));
                level.setBlockAndUpdate(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                for (ServerPlayer player : level.players()) {
                    if (player.blockPosition().equals(near)) {
                        player.teleportTo(level, foot.getX() - 0.5, foot.getY(), foot.getZ() + 0.5, Set.of(), -90f, 30f, false);
                    }
                }
                return foot;
            }
        }
        return null;
    }

    /** Two blocks of air on a sturdy floor: room for a bed, or for someone to stand. */
    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    private static String backAgain() {
        return onServer(level -> {
            Entity person = host(level, "wandmaker");
            return person == null ? "" : person.getDisplayName().getString() + "|" + person.getUUID();
        });
    }

    /** The post the wandmaker kept, read off them before the blow: afterwards there is nobody to read it from. */
    private static PostRef wandmakerPost;

    private static SettlementData.Post staffPost(ServerLevel level, String role) {
        return wandmakerPost == null ? null : SettlementData.get(level).post(wandmakerPost);
    }

    private static void reconnect(String one, String two) {
        long held = vault(two);
        int sickles = pocket(two, CurrencyItemRegistry.SICKLE.get());
        int knuts = pocket(two, CurrencyItemRegistry.KNUT.get());
        tell(two, "leave");
        pause(3000);
        check("the second client left", online() == 1, online());
        long giveUp = System.currentTimeMillis() + 300_000;
        new Thread(() -> tell(two, "join"), "wb-mp-rejoin").start();
        while (online() < 2 && System.currentTimeMillis() < giveUp) {
            pause(1000);
        }
        check("the second client came back", online() == 2, online());
        pause(3000);
        check("the vault survived the disconnect", vault(two) == held, held + " -> " + vault(two));
        check("the pocket survived the disconnect", pocket(two, CurrencyItemRegistry.SICKLE.get()) == sickles
                && pocket(two, CurrencyItemRegistry.KNUT.get()) == knuts, "");
    }

    /** Both players go far enough for the hamlet's chunks to drop, and come back to the same people. */
    private static void leaveAndReturn(String one, String two) {
        List<String> before = onServer(level -> staff(level).stream().map(e -> e.getUUID().toString()).sorted().toList());
        onServer(level -> {
            ChunkPos middle = new ChunkPos(box.getCenter());
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, new ChunkPos(middle.x + x, middle.z + z), 2);
                }
            }
            for (String name : List.of(one, two)) {
                player(name).teleportTo(level, box.getCenter().getX() + 2000.5, 200.0, box.getCenter().getZ() + 0.5, Set.of(), 0f, 0f, false);
            }
            return null;
        });
        pause(30_000);
        int stillLoaded = onServer(level -> staff(level).size());
        say("NOTE people still loaded with both players 2000 blocks off: " + stillLoaded);
        onServer(level -> {
            BlockPos centre = box.getCenter();
            for (String name : List.of(one, two)) {
                player(name).teleportTo(level, centre.getX() + 6.5, box.minY() + 30.0, centre.getZ() + 6.5, Set.of(), 0f, 0f, false);
            }
            return null;
        });
        long giveUp = System.currentTimeMillis() + 300_000;
        long nextNote = 0;
        while (onServer(level -> staff(level).size()) < before.size() && System.currentTimeMillis() < giveUp) {
            if (System.currentTimeMillis() > nextNote) {
                nextNote = System.currentTimeMillis() + 30_000;
                say("NOTE back at the hamlet: " + onServer(level -> {
                    BlockPos centre = box.getCenter();
                    return "players " + List.of(one, two).stream().map(n -> player(n) == null ? n + " offline"
                            : n + " " + player(n).blockPosition().toShortString()).toList()
                            + ", centre chunk entity-ticking " + level.isPositionEntityTicking(centre)
                            + ", people " + staff(level).size() + ", game time " + level.getGameTime();
                }));
            }
            pause(1000);
        }
        List<String> after = onServer(level -> staff(level).stream().map(e -> e.getUUID().toString()).sorted().toList());
        check("the same people after the chunks dropped and came back", before.equals(after), before.size() + " / " + after.size()
                + (before.equals(after) ? "" : " before " + before + " after " + after));
    }
}
