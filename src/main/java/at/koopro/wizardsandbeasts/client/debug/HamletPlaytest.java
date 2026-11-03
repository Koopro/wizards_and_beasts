package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.chamber.ChamberSealBlock;
import at.koopro.wizardsandbeasts.client.currency.gui.GringottsScreen;
import at.koopro.wizardsandbeasts.client.location.LocationScreen;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.journey.ApprenticePathService;
import at.koopro.wizardsandbeasts.journey.PathRecord;
import at.koopro.wizardsandbeasts.journey.PathStep;
import at.koopro.wizardsandbeasts.location.LocationView;
import at.koopro.wizardsandbeasts.market.MarketLine;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementGround;
import at.koopro.wizardsandbeasts.treasure.SiteSearch;
import at.koopro.wizardsandbeasts.treasure.TreasureCharts;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Plays a naturally generated hamlet through a real client: its renderer, its screens, its keys and its network path. The
 * script runs on its own thread and hands each action to the client or server thread, so it reads top to bottom.
 *
 * <p>Input goes through {@link KeyMapping}s and the open screen's own {@code mouseClicked}, never the operating system, so a
 * run touches nothing outside this process. It is scripted play, not a person: it proves what a client does and shows what
 * a client draws, and says nothing about how a place feels to walk. Nothing here places a template; every site is what
 * generation made of a fresh world.
 *
 * <p>Development only, inert unless {@code WB_PLAYTEST} is set (a comma-separated list of parts).
 * {@code WB_PLAYTEST_SEED} seeds the world, {@code WB_PLAYTEST_LABEL} names the run and {@code WB_PLAYTEST_CHUNK} ({@code
 * x,z}) picks a site; without it, the nearest to spawn. Results go to {@code playtest_report.txt} and
 * {@code screenshots/playtest/<label>}; each result line starts PASS, FAIL or NOTE.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class HamletPlaytest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PARTS = FMLEnvironment.isProduction() ? null : System.getenv("WB_PLAYTEST");
    private static final String LABEL = System.getenv().getOrDefault("WB_PLAYTEST_LABEL", "run");
    private static final Path REPORT = Path.of("playtest_report.txt");
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final long SETTLE_MS = 2500;

    private static boolean started;
    private static volatile long clientTicks;
    private static volatile CountDownLatch frame;
    private static volatile Path frameFile;

    /** What the tour reads off the world once: the site, its people and their doors. */
    private static BoundingBox box;
    private static BlockPos centre;
    private static int floorY;

    private HamletPlaytest() {}

    // -- plumbing -----------------------------------------------------------------------------------------

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void say(String line) {
        LOGGER.info("[Playtest] {}", line);
        try {
            Files.writeString(REPORT, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Playtest: cannot write the report", e);
        }
    }

    private static void check(String what, boolean ok, Object detail) {
        say((ok ? "PASS " : "FAIL ") + what + (String.valueOf(detail).isEmpty() ? "" : " :: " + detail));
    }

    private static <T> T client(Callable<T> job) {
        CompletableFuture<T> done = new CompletableFuture<>();
        mc().execute(() -> {
            try {
                done.complete(job.call());
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return join(done);
    }

    private static <T> T server(Function<ServerLevel, T> job) {
        MinecraftServer server = mc().getSingleplayerServer();
        CompletableFuture<T> done = new CompletableFuture<>();
        server.execute(() -> {
            try {
                done.complete(job.apply(server.overworld()));
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return join(done);
    }

    private static <T> T join(CompletableFuture<T> done) {
        try {
            return done.get(1200, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("playtest step failed", e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void ticks(int count) {
        long until = clientTicks + count;
        long giveUp = System.currentTimeMillis() + 50L * count + 60_000L;
        while (clientTicks < until && System.currentTimeMillis() < giveUp) {
            sleep(10);
        }
    }

    private static boolean until(BooleanSupplier ready, int maxTicks) {
        long giveUp = clientTicks + maxTicks;
        long hardStop = System.currentTimeMillis() + 50L * maxTicks + 60_000L;
        while (clientTicks < giveUp && System.currentTimeMillis() < hardStop) {
            if (ready.getAsBoolean()) {
                return true;
            }
            sleep(50);
        }
        return ready.getAsBoolean();
    }

    /** The next rendered frame, as the player sees it, or with the HUD hidden for looking at the world itself. */
    private static void shot(String name, boolean hud) {
        client(() -> {
            mc().options.hideGui = !hud;
            return null;
        });
        sleep(250);
        Path file = mc().gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "playtest", LABEL, name + ".png"));
        CountDownLatch written = new CountDownLatch(1);
        frameFile = file;
        frame = written;
        try {
            if (!written.await(60, TimeUnit.SECONDS)) {
                say("FAIL shot " + name + " never arrived");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        CountDownLatch written = frame;
        if (written == null) {
            return;
        }
        frame = null;
        Path file = frameFile;
        Screenshot.takeScreenshot(mc().getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Playtest: cannot write {}", file, e);
            } finally {
                written.countDown();
            }
        }));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        clientTicks++;
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (PARTS == null || started || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        started = true;
        mc().options.pauseOnLostFocus = false;
        GLFW.glfwSetWindowSize(mc().getWindow().handle(), WIDTH, HEIGHT);
        Thread script = new Thread(HamletPlaytest::run, "wb-playtest");
        script.setDaemon(true);
        script.start();
    }

    private static void run() {
        try {
            Files.deleteIfExists(REPORT);
            say("run " + LABEL + " parts " + PARTS + " window "
                    + client(() -> mc().getWindow().getWidth() + "x" + mc().getWindow().getHeight())
                    + " gl " + client(() -> com.mojang.blaze3d.platform.GLX._getCpuInfo()));
            shot("title", true);
            List<String> parts = List.of(PARTS.split(","));
            if (parts.contains("mp")) {
                multiplayer(System.getenv("WB_PLAYTEST_MP"));
                say("DONE");
                return;
            }
            openWorld();
            if (parts.contains("chart")) {
                chart();
            }
            findSite();
            if (parts.contains("tour")) {
                tour();
            }
            if (parts.contains("approach")) {
                approaches();
            }
            if (parts.contains("play")) {
                play();
            }
            if (parts.contains("knock") || parts.contains("door")) {
                prepare();
            }
            if (parts.contains("door")) {
                door();
            }
            if (parts.contains("knock")) {
                knock();
            }
            say("DONE");
        } catch (Throwable t) {
            LOGGER.error("Playtest died", t);
            say("FAIL the script died: " + t);
        } finally {
            client(() -> {
                mc().stop();
                return null;
            });
        }
    }

    // -- two clients --------------------------------------------------------------------------------------

    /**
     * Joins the server named by {@code WB_PLAYTEST_MP} and does as {@code settlement/SettlementMpProbe} says. It reads
     * {@code <dir>/<name>.cmd} and answers in {@code <dir>/<name>.ack}, so the server decides the order and checks the result.
     */
    private static void multiplayer(String address) throws IOException {
        Path dir = Path.of(System.getenv("WB_PLAYTEST_DIR"));
        String name = client(() -> mc().getUser().getName());
        connect(address);
        boolean in = until(() -> mc().player != null && mc().level != null, 12_000);
        check("joined " + address + " as " + name, in, "");
        Path command = dir.resolve(name + ".cmd");
        String last = "";
        long lastHeard = System.currentTimeMillis();
        while (System.currentTimeMillis() - lastHeard < 900_000) {
            if (Files.exists(command)) {
                String line;
                try {
                    line = Files.readString(command, StandardCharsets.UTF_8).trim();
                } catch (IOException e) {
                    sleep(50);
                    continue;
                }
                if (!line.isEmpty() && !line.equals(last)) {
                    last = line;
                    lastHeard = System.currentTimeMillis();
                    String number = line.substring(0, line.indexOf(' '));
                    String verb = line.substring(line.indexOf(' ') + 1);
                    String answer;
                    try {
                        answer = obey(verb, address);
                    } catch (RuntimeException e) {
                        answer = "ERROR " + e;
                        LOGGER.error("Playtest: {} failed", verb, e);
                    }
                    Path temporary = dir.resolve(name + ".ack.tmp");
                    Files.writeString(temporary, number + " " + answer, StandardCharsets.UTF_8);
                    Files.move(temporary, dir.resolve(name + ".ack"), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }
            }
            sleep(50);
        }
    }

    private static void connect(String address) {
        mc().execute(() -> ConnectScreen.startConnecting(new TitleScreen(), mc(), ServerAddress.parseString(address),
                new ServerData("playtest", address, ServerData.Type.OTHER), false, null));
    }

    private static Entity seen(String id) {
        UUID uuid = UUID.fromString(id);
        for (Entity entity : mc().level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }

    private static String obey(String verb, String address) {
        String[] words = verb.split(" ");
        switch (words[0]) {
            case "close" -> {
                closeScreen();
                return "ok";
            }
            case "shot" -> {
                shot(words[1], true);
                return "ok";
            }
            case "sees" -> {
                return String.valueOf(client(() -> seen(words[1]) != null));
            }
            case "speak" -> {
                Vec3 at = client(() -> seen(words[1]) == null ? null : seen(words[1]).getBoundingBox().getCenter());
                if (at == null) {
                    return "not seen";
                }
                for (int attempt = 0; attempt < 3 && client(() -> mc().screen == null); attempt++) {
                    Vec3 now = client(() -> seen(words[1]) == null ? at : seen(words[1]).getBoundingBox().getCenter());
                    aim(now);
                    rightClick();
                    until(() -> mc().screen != null, 100);
                }
                ticks(10);
                return client(() -> String.valueOf(mc().screen));
            }
            case "firstgood" -> {
                LocationView view = view();
                return view == null ? "none" : view.market().stream().filter(l -> !l.buying() && l.unlocked() && l.available() && l.count() >= 2)
                        .map(MarketLine::good).findFirst().orElse("none");
            }
            case "shelf" -> {
                LocationView view = view();
                return String.valueOf(view == null ? -1 : view.market().stream().filter(l -> !l.buying() && l.good().equals(words[1]))
                        .mapToInt(MarketLine::count).findFirst().orElse(-1));
            }
            case "buy" -> {
                return String.valueOf(press("BUY", words[1] + "|1"));
            }
            case "ledger" -> {
                for (int i = 0; i < Integer.parseInt(words[2]); i++) {
                    ledger(Integer.parseInt(words[1]));
                }
                return "ok";
            }
            case "burst" -> {
                // Every click inside one client tick: they leave together and reach the server together, as clicks made
                // during lag do. The screen's own hit test still decides what each click presses.
                int clicks = Integer.parseInt(words[2]);
                client(() -> {
                    AbstractWidget button = (AbstractWidget) mc().screen.children().get(Integer.parseInt(words[1]));
                    for (int i = 0; i < clicks; i++) {
                        mouseClick(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
                    }
                    return null;
                });
                ticks(10);
                return "ok";
            }
            case "sleep" -> {
                // A bed set down by the server: aimed at and right-clicked, as a player goes to bed.
                BlockPos bed = new BlockPos(Integer.parseInt(words[1]), Integer.parseInt(words[2]), Integer.parseInt(words[3]));
                aim(Vec3.atCenterOf(bed).add(0, -0.2, 0));
                boolean onBed = until(() -> client(() -> mc().hitResult instanceof BlockHitResult hit
                        && mc().level.getBlockState(hit.getBlockPos()).is(BlockTags.BEDS)), 40);
                rightClick();
                boolean asleep = until(() -> client(() -> mc().player.isSleeping()), 100);
                return "aimed " + onBed + ", asleep " + asleep;
            }
            case "awake" -> {
                return String.valueOf(client(() -> !mc().player.isSleeping()));
            }
            case "accept" -> {
                LocationView view = view();
                if (view == null || view.offers().isEmpty()) {
                    return "none";
                }
                String id = view.offers().get(0).id();
                press("ACCEPT", id);
                return id;
            }
            case "held" -> {
                LocationView view = view();
                return view == null ? "no board" : view.active().stream().map(line -> line.id()).toList().toString();
            }
            case "leave" -> {
                client(() -> {
                    mc().disconnectFromWorld(net.minecraft.network.chat.Component.literal("playtest"));
                    return null;
                });
                return String.valueOf(until(() -> mc().level == null, 600));
            }
            case "join" -> {
                connect(address);
                return String.valueOf(until(() -> mc().player != null && mc().level != null, 6_000));
            }
            default -> {
                return "unknown";
            }
        }
    }

    // -- the world ----------------------------------------------------------------------------------------

    private static void openWorld() {
        long seed = Long.parseLong(System.getenv().getOrDefault("WB_PLAYTEST_SEED", "271828182"));
        String name = "playtest_" + LABEL;
        // Not through client(): opening a world holds the client thread for as long as the first chunks take.
        mc().execute(() -> {
            if (mc().getLevelSource().levelExists(name)) {
                mc().createWorldOpenFlows().openWorld(name, () -> say("FAIL the world would not open"));
            } else {
                GameRules rules = new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures());
                mc().createWorldOpenFlows().createFreshLevel(name, new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.EASY,
                        true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(seed, true, false),
                        WorldPresets::createNormalWorldDimensions, new TitleScreen());
            }
        });
        boolean in = until(() -> {
            answerPrompt();
            return mc().player != null && mc().level != null && mc().getSingleplayerServer() != null
                    && !mc().getSingleplayerServer().getPlayerList().getPlayers().isEmpty();
        }, 12_000);
        check("a naturally generated world opens, seed " + seed, in, "");
        if (!in) {
            throw new IllegalStateException("no world");
        }
        ticks(100);
        // A new character is met by the heritage screen. It is the player's first sight of the game, so it is kept.
        say("NOTE on arrival the open screen is " + client(() -> String.valueOf(mc().screen)));
        shot("first_join", true);
        server(level -> {
            ServerPlayer player = me(level);
            if (!HeritageAPI.hasHeritageSelected(player)) {
                HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            }
            return null;
        });
        closeScreen();
        ticks(40);
        say("NOTE after choosing a heritage the open screen is " + client(() -> String.valueOf(mc().screen)));
        closeScreen();
    }

    private static String lastPrompt = "";

    /**
     * Reopening a world can stop on a question (experimental settings, a backup). It is answered as a player would, by
     * the button that goes on, and named in the report, since a player meets it too.
     */
    private static void answerPrompt() {
        client(() -> {
            Screen screen = mc().screen;
            if (screen == null || screen instanceof TitleScreen || screen.children().isEmpty()) {
                return null;
            }
            for (var child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button) {
                    String label = button.getMessage().getString();
                    if (label.matches("(?i).*(proceed|continue|yes|don't back|without backup|open).*")) {
                        String name = screen.getClass().getSimpleName() + " '" + screen.getTitle().getString() + "' -> " + label;
                        if (!name.equals(lastPrompt)) {
                            lastPrompt = name;
                            say("NOTE opening the world asked: " + name);
                        }
                        button.onPress(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                        return null;
                    }
                }
            }
            return null;
        });
    }

    private static void findSite() {
        String chosen = System.getenv("WB_PLAYTEST_CHUNK");
        long began = System.nanoTime();
        BlockPos[] centreOut = new BlockPos[1];
        boolean found = server(level -> {
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
            var chunk = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS);
            for (StructureStart start : chunk.getAllStarts().values()) {
                if (level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .wrapAsHolder(start.getStructure()).is(SettlementGround.SETTLEMENTS)) {
                    box = start.getBoundingBox();
                    centre = box.getCenter();
                    floorY = start.getPieces().get(0).getBoundingBox().minY();
                    centreOut[0] = centre;
                    return true;
                }
            }
            return false;
        });
        check("a hamlet stands there", found, found ? box + " after " + (System.nanoTime() - began) / 1_000_000 + " ms" : "");
        if (!found) {
            throw new IllegalStateException("no hamlet");
        }
    }

    private static void view(double x, double y, double z, float yaw, float pitch) {
        server(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false);
            return null;
        });
        sleep(SETTLE_MS);
        // Not a shot of a half-built world: on a loaded machine the sections trail the teleport by many seconds.
        until(() -> client(() -> mc().levelRenderer.hasRenderedAllSections()), 600);
    }

    private static void lookFrom(Vec3 eye, Vec3 at) {
        double dx = at.x - eye.x;
        double dy = at.y - eye.y;
        double dz = at.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        view(eye.x, eye.y - 1.62, eye.z, yaw, pitch);
    }

    private static void fly() {
        server(level -> {
            // A spectator's camera stays where it is put; a flying player's drops as soon as anything touches the state.
            level.getServer().getPlayerList().getPlayers().get(0).setGameMode(GameType.SPECTATOR);
            return null;
        });
    }

    private record Person(UUID id, String role, String name, Vec3 pos) {}

    private static List<Person> people() {
        return server(level -> {
            List<Person> found = new ArrayList<>();
            for (Entity e : level.getEntities((Entity) null, AABB.of(box).inflate(32.0), p -> p.hasData(ModAttachments.SETTLEMENT_POST.get()))) {
                String role = e instanceof SettlementNpcEntity npc ? npc.roleId().substring(npc.roleId().indexOf(':') + 1) : "teller";
                found.add(new Person(e.getUUID(), role, e.getDisplayName().getString(), e.position()));
            }
            return found;
        });
    }

    /** Every door of the hamlet: its lower half. */
    private static List<BlockPos> doors() {
        return server(level -> {
            List<BlockPos> found = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.DOORS) && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                    found.add(pos.immutable());
                }
            }
            return found;
        });
    }

    // -- the tour -----------------------------------------------------------------------------------------

    private static void tour() {
        fly();
        Vec3 middle = Vec3.atCenterOf(centre.atY(floorY + 3));
        int half = Math.max(box.getXSpan(), box.getZSpan()) / 2;
        server(level -> {
            level.setDayTime(6000);
            return null;
        });
        lookFrom(middle.add(0, half * 0.9, half * 0.9), middle);
        boolean staffed = until(() -> people().size() >= 5, 2_400);
        check("all five people are at their posts", staffed, people());
        shot("overview_day", false);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Vec3 from = middle.add(side.getStepX() * (half + 16.0), 1.5, side.getStepZ() * (half + 16.0));
            lookFrom(from, middle);
            shot("approach_" + side.getName(), false);
        }
        lookFrom(middle.add(9, 0.0, 9), middle.add(0, 5, 0));
        shot("plaza_spire", false);
        List<BlockPos> doors = doors();
        say("NOTE doors " + doors);
        for (Person person : people()) {
            BlockPos door = nearest(doors, person.pos());
            Vec3 outside = towardPlaza(door, 9.0);
            lookFrom(outside.add(0, 1.2, 0), Vec3.atCenterOf(door).add(0, 2.5, 0));
            shot("exterior_" + person.role(), false);
            Vec3 inside = towardCentreless(door, person.pos());
            lookFrom(inside, person.pos().add(0, 1.4, 0));
            shot("interior_" + person.role(), false);
        }
        server(level -> {
            level.setDayTime(18000);
            return null;
        });
        lookFrom(middle.add(0, half * 0.9, half * 0.9), middle);
        shot("overview_night", false);
        lookFrom(middle.add(9, 0.0, 9), middle.add(0, 5, 0));
        shot("plaza_night", false);
        for (Person person : people()) {
            Vec3 inside = towardCentreless(nearest(doors, person.pos()), person.pos());
            lookFrom(inside, person.pos().add(0, 1.4, 0));
            shot("night_interior_" + person.role(), false);
        }
    }

    private static BlockPos nearest(List<BlockPos> doors, Vec3 to) {
        BlockPos best = doors.get(0);
        for (BlockPos door : doors) {
            if (Vec3.atCenterOf(door).distanceToSqr(to) < Vec3.atCenterOf(best).distanceToSqr(to)) {
                best = door;
            }
        }
        return best;
    }

    /** Every door faces the plaza, so a building's front is seen from the Spire's side of it. */
    private static Vec3 towardPlaza(BlockPos door, double by) {
        Vec3 in = Vec3.atCenterOf(centre.atY(door.getY())).subtract(Vec3.atCenterOf(door)).normalize();
        return Vec3.atCenterOf(door).add(in.scale(by));
    }

    /** A place inside the room, between the door and the person, at eye height. */
    private static Vec3 towardCentreless(BlockPos door, Vec3 person) {
        Vec3 from = Vec3.atCenterOf(door);
        Vec3 to = person.subtract(from);
        return from.add(to.scale(0.35)).add(0, 1.2, 0);
    }

    // -- the way in ---------------------------------------------------------------------------------------

    /**
     * How the land around the site lets a person come and go, well past the walls: on foot alone, then with the safe drops a
     * player takes without thinking, then swimming too. Forces the chunks it reads, so it answers for the ground, not for
     * what happens to be loaded.
     */
    private static void approaches() {
        for (String mode : List.of("foot", "drops", "swim")) {
            say("NOTE approach " + mode + ": " + server(level -> flood(level, mode)));
        }
        say("NOTE west-to-east ground beside the site: " + server(HamletPlaytest::edgeProfile));
    }

    /** Open plaza a few steps in front of a door. The middle of the plaza is the Spire: stone, not somewhere to stand. */
    private static BlockPos plazaCell(ServerLevel level) {
        BlockPos door = null;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), floorY, box.minZ(), box.maxX(), floorY + 3, box.maxZ())) {
            if (level.getBlockState(pos).is(BlockTags.DOORS)) {
                door = pos.immutable();
                break;
            }
        }
        BlockPos spot = BlockPos.containing(towardPlaza(door, 4.0)).atY(door.getY());
        for (int y = door.getY() + 2; y > door.getY() - 3; y--) {
            if (standable(level, spot.atY(y))) {
                return spot.atY(y);
            }
        }
        return spot;
    }

    private static boolean water(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
    }

    private static String flood(ServerLevel level, String mode) {
        int reach = 64;
        BoundingBox limit = box.inflatedBy(reach, 40, reach);
        boolean drops = !mode.equals("foot");
        boolean swim = mode.equals("swim");
        BlockPos from = plazaCell(level);
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty() && seen.size() < 400_000) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = drops ? -3 : -1; dy <= 1; dy++) {
                    BlockPos next = pos.relative(direction).above(dy);
                    boolean floats = swim && water(level, next) && !barred(level, next.above());
                    if (limit.isInside(next) && !seen.contains(next) && (floats || standable(level, next))
                            && (!water(level, next) || swim)) {
                        seen.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        int west = 0;
        int east = 0;
        int north = 0;
        int south = 0;
        for (BlockPos pos : seen) {
            west += pos.getX() <= limit.minX() + 2 ? 1 : 0;
            east += pos.getX() >= limit.maxX() - 2 ? 1 : 0;
            north += pos.getZ() <= limit.minZ() + 2 ? 1 : 0;
            south += pos.getZ() >= limit.maxZ() - 2 ? 1 : 0;
        }
        return seen.size() + " cells; far edge reached W " + west + " E " + east + " N " + north + " S " + south;
    }

    private static String edgeProfile(ServerLevel level) {
        StringBuilder row = new StringBuilder();
        for (int x = box.minX() - 24; x <= box.maxX() + 24; x += 4) {
            BlockPos top = level.getChunk(x >> 4, centre.getZ() >> 4).getBlockState(new BlockPos(x, 0, centre.getZ())) == null ? null
                    : level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, centre.getZ()));
            row.append(top.getY() - 1 - floorY).append(water(level, top.below()) ? "w " : " ");
        }
        return "relative to the floor, every 4 blocks, w is water: " + row;
    }

    // -- walking ------------------------------------------------------------------------------------------

    private static ServerPlayer me(ServerLevel level) {
        return level.getServer().getPlayerList().getPlayers().get(0);
    }

    /**
     * No way through for a person, except what they open with a hand: doors, and the trapdoor over a ladder. Any other
     * trapdoor is furniture: a counter top opened and climbed through is not a way a player would take.
     */
    private static boolean barred(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean hatch = state.is(BlockTags.TRAPDOORS) && level.getBlockState(pos.below()).is(BlockTags.CLIMBABLE);
        return !state.getCollisionShape(level, pos).isEmpty() && !state.is(BlockTags.DOORS) && !hatch
                && !state.is(BlockTags.CLIMBABLE);
    }

    private static boolean climbable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.CLIMBABLE) || state.is(BlockTags.TRAPDOORS) && level.getBlockState(pos.below()).is(BlockTags.CLIMBABLE);
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        return !barred(level, pos) && !barred(level, pos.above()) && (climbable(level, pos)
                || !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty());
    }

    /** A person's way from where the player stands to any cell {@code wanted} accepts: one block up or down, through doors, on ladders. */
    private static List<BlockPos> route(java.util.function.Predicate<BlockPos> wanted) {
        return server(level -> {
            BlockPos from = me(level).blockPosition();
            BoundingBox limit = box.inflatedBy(64);
            Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(from);
            cameFrom.put(from, from);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                if (wanted.test(pos)) {
                    List<BlockPos> path = new ArrayList<>();
                    for (BlockPos at = pos; !at.equals(from); at = cameFrom.get(at)) {
                        path.add(at);
                    }
                    Collections.reverse(path);
                    return path;
                }
                List<BlockPos> next = new ArrayList<>();
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    for (int dy = -1; dy <= 1; dy++) {
                        next.add(pos.relative(direction).above(dy));
                    }
                }
                if (climbable(level, pos) || climbable(level, pos.above())) {
                    next.add(pos.above());
                }
                if (climbable(level, pos.below())) {
                    next.add(pos.below());
                }
                for (BlockPos step : next) {
                    if (limit.isInside(step) && !cameFrom.containsKey(step) && standable(level, step)) {
                        cameFrom.put(step, pos);
                        queue.add(step);
                    }
                }
            }
            return null;
        });
    }

    private static void aim(Vec3 at) {
        client(() -> {
            Vec3 eye = mc().player.getEyePosition();
            double dx = at.x - eye.x;
            double dy = at.y - eye.y;
            double dz = at.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            mc().player.setYRot(yaw);
            mc().player.setYHeadRot(yaw);
            mc().player.setXRot(pitch);
            return null;
        });
        ticks(3);
    }

    private static void keys(boolean forward, boolean jump) {
        client(() -> {
            mc().options.keyUp.setDown(forward);
            mc().options.keyJump.setDown(jump);
            return null;
        });
    }

    private static void rightClick() {
        client(() -> {
            KeyMapping.click(mc().options.keyUse.getDefaultKey());
            return null;
        });
        ticks(3);
    }

    private static boolean closedDoor(BlockPos pos) {
        return client(() -> {
            BlockState state = mc().level.getBlockState(pos);
            return state.is(BlockTags.DOORS) && !state.getValue(DoorBlock.OPEN)
                    || state.is(BlockTags.TRAPDOORS) && !state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN);
        });
    }

    /**
     * Walks a route with the movement keys, opening doors with a right click as a player does. A cell not reached in time
     * counts against the place, not the script: it is reported, and the player is put past it so the walk can go on.
     */
    private static boolean walk(String what, List<BlockPos> path) {
        if (path == null) {
            check("a way on foot: " + what, false, "no route");
            return false;
        }
        for (BlockPos cell : path) {
            if (closedDoor(cell)) {
                keys(false, false);
                aim(Vec3.atCenterOf(cell));
                boolean onDoor = until(() -> client(() -> mc().hitResult instanceof BlockHitResult hit
                        && (mc().level.getBlockState(hit.getBlockPos()).is(BlockTags.DOORS)
                        || mc().level.getBlockState(hit.getBlockPos()).is(BlockTags.TRAPDOORS))), 40);
                check("the crosshair finds the door at " + cell.toShortString(), onDoor, "");
                // A person strolling through can swing it between the look and the click, so a miss is tried again.
                boolean open = false;
                int clicks = 0;
                while (!open && clicks < 3) {
                    clicks++;
                    rightClick();
                    open = until(() -> !closedDoor(cell), 40);
                }
                check("a right click opens it", open, clicks + " click(s)");
                closeScreen();
            }
            Vec3 target = Vec3.atBottomCenterOf(cell);
            boolean reached = false;
            for (int i = 0; i < 120 && !reached; i++) {
                Vec3 here = client(() -> mc().player.position());
                double flat = Math.hypot(target.x - here.x, target.z - here.z);
                if (flat < 0.45 && Math.abs(here.y - target.y) < 1.2) {
                    reached = true;
                    break;
                }
                aim(new Vec3(target.x, here.y + 1.62, target.z));
                keys(flat >= 0.3, target.y > here.y + 0.4);
            }
            if (!reached) {
                keys(false, false);
                shot("stuck_" + what.replace(' ', '_').replace('\'', '_'), true);
                BlockPos at = cell;
                say("FAIL stuck walking " + what + " at " + cell.toShortString() + ", player "
                        + client(() -> mc().player.blockPosition().toShortString()) + ", screen " + client(() -> String.valueOf(mc().screen))
                        + ", within two blocks: " + server(level -> nearby(level, at, 2.0))
                        + "; set down at the end of the route");
                closeScreen();
                Vec3 end = Vec3.atBottomCenterOf(path.get(path.size() - 1));
                view(end.x, end.y, end.z, 0f, 0f);
                return false;
            }
        }
        keys(false, false);
        check("walked on foot: " + what, true, path.size() + " cells");
        return true;
    }

    /** People stroll, so the way to them is worked out again until the player is within reach of where they are now. */
    private static boolean walkToPerson(Person person) {
        boolean walked = true;
        for (int attempt = 0; attempt < 4; attempt++) {
            Vec3 at = personNow(person);
            double away = client(() -> mc().player.position().distanceTo(at));
            if (away < 3.0) {
                return walked;
            }
            // Within arm's reach of them, which at a counter means from the customer's side of it.
            walked &= walk("to the " + person.role(), route(cell -> Vec3.atBottomCenterOf(cell).distanceTo(at) < 2.9
                    && Math.abs(cell.getY() - at.y) < 1.5));
        }
        return walked;
    }

    private static Vec3 personNow(Person person) {
        return server(level -> {
            Entity entity = level.getEntity(person.id());
            return entity == null ? person.pos() : entity.position();
        });
    }

    // -- at the counter -----------------------------------------------------------------------------------

    /** Faces a person and right-clicks, as a player does. The screen has to open from that alone. */
    private static boolean speak(Person person) {
        // The middle of their body, as a player aims: a goblin is shorter than the height a person's chest would be.
        Vec3 at = client(() -> {
            Entity entity = seen(person.id().toString());
            return entity == null ? person.pos().add(0, 1.0, 0) : entity.getBoundingBox().getCenter();
        });
        aim(at);
        boolean aimed = until(() -> client(() -> mc().hitResult instanceof EntityHitResult hit
                && hit.getEntity().getUUID().equals(person.id())), 40);
        double distance = client(() -> mc().player.getEyePosition().distanceTo(at));
        check("the crosshair finds the " + person.role(), aimed, String.format("%.1f blocks", distance));
        rightClick();
        boolean opened = until(() -> mc().screen != null, 100);
        if (!opened) {
            // Once more, as a player whose click went nowhere would, with the picture of what they were looking at.
            shot("no_screen_" + person.role(), true);
            say("NOTE first click on the " + person.role() + " opened nothing; looking at " + client(() -> String.valueOf(mc().hitResult == null
                    ? "nothing" : mc().hitResult instanceof EntityHitResult hit ? hit.getEntity().getName().getString() : mc().hitResult.getType())));
            rightClick();
            opened = until(() -> mc().screen != null, 100);
        }
        check("right-clicking the " + person.role() + " opens their screen", opened, client(() -> String.valueOf(mc().screen)));
        ticks(10);
        return opened;
    }

    private static void closeScreen() {
        client(() -> {
            mc().setScreen(null);
            return null;
        });
        ticks(5);
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object call(Object owner, String name) {
        try {
            Method method = owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocationView view() {
        return client(() -> mc().screen instanceof LocationScreen screen ? (LocationView) field(screen, "view") : null);
    }

    private static void mouseClick(double x, double y) {
        Screen screen = mc().screen;
        if (screen != null) {
            screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
        }
    }

    /**
     * Clicks the board's button for {@code action}, the first whose argument starts with {@code argument}, through the
     * screen's own hit test. A button is only clickable once drawn, so this scrolls as a player would to bring it into view.
     */
    private static boolean press(String action, String argument) {
        for (int attempt = 0; attempt < 240; attempt++) {
            boolean clicked = client(() -> {
                if (!(mc().screen instanceof LocationScreen screen)) {
                    return false;
                }
                for (Object button : List.copyOf((List<?>) field(screen, "buttons"))) {
                    if (String.valueOf(call(button, "action")).equals(action) && String.valueOf(call(button, "argument")).startsWith(argument)) {
                        mouseClick((int) call(button, "x") + 2, (int) call(button, "y") + 2);
                        return true;
                    }
                }
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
                return false;
            });
            if (clicked) {
                ticks(10);
                return true;
            }
            ticks(2);
        }
        return false;
    }

    private static void scrollTop() {
        client(() -> {
            if (mc().screen instanceof LocationScreen screen) {
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 200);
            }
            return null;
        });
        ticks(3);
    }

    private static long vault() {
        return server(level -> me(level).getData(ModAttachments.VAULT_DATA.get()).getTotalInKnuts());
    }

    private static int pocket(Item item) {
        return server(level -> me(level).getInventory().countItem(item));
    }

    private static List<String> topics() {
        LocationView view = view();
        List<String> ids = new ArrayList<>();
        if (view != null) {
            view.talk().topics().forEach(topic -> ids.add(topic.id()));
        }
        return ids;
    }

    /** Asks every topic the person has; a shot of the screen after each shows the answer as it is drawn. */
    private static void talkThrough(String role) {
        List<String> topics = topics();
        check("the " + role + " has things to say", !topics.isEmpty(), topics);
        shot(role + "_screen", true);
        for (String topic : topics) {
            scrollTop();
            boolean asked = press("TOPIC", topic);
            String reply = view() == null ? "" : view().talk().reply().getString();
            check("asking the " + role + " about " + topic, asked && !reply.isEmpty(), reply);
            if (topic.equals(topics.get(0))) {
                shot(role + "_reply", true);
            }
        }
        scrollTop();
    }

    /** Buys the first thing on the shelf that can be bought, by the Buy One button, and checks the three things it moves. */
    private static void buyFirst(String role) {
        LocationView view = view();
        MarketLine line = view == null ? null : view.market().stream()
                .filter(l -> !l.buying() && l.unlocked() && l.available()).findFirst().orElse(null);
        check("the " + role + " has something to sell", line != null, view == null ? "no board" : view.market().size() + " lines");
        if (line == null) {
            return;
        }
        long before = vault();
        int stock = line.count();
        boolean pressed = press("BUY", line.good() + "|1");
        ticks(10);
        long after = vault();
        LocationView next = view();
        int left = next == null ? -9 : next.market().stream().filter(l -> !l.buying() && l.good().equals(line.good()))
                .mapToInt(MarketLine::count).findFirst().orElse(-9);
        check("buying " + line.good() + " from the " + role, pressed && after == before - line.unitKnuts(),
                "paid " + (before - after) + " of " + line.unitKnuts() + ", shelf " + stock + " -> " + left);
        shot(role + "_after_purchase", true);
    }

    private static void play() {
        prepare();
        Map<String, Person> byRole = new HashMap<>();
        people().forEach(person -> byRole.put(person.role(), person));

        keeper(byRole.get("keeper"));
        wandmaker(byRole.get("wandmaker"));
        apothecary(byRole.get("apothecary"));
        scholar(byRole.get("scholar"));
        teller(byRole.get("teller"));
        undercroft();
    }

    /** A character as play finds them, set down short of the site and walked to the plaza on foot. */
    private static void prepare() {
        server(level -> {
            ServerPlayer player = me(level);
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            if (!HeritageAPI.hasHeritageSelected(player)) {
                HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            }
            player.getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), 7));
            player.getInventory().add(new ItemStack(CurrencyItemRegistry.KNUT.get(), 40));
            player.getData(ModAttachments.VAULT_DATA.get()).depositGalleons(60);
            // An empty hand: a character starts with the Handbook in slot one, and a right click that misses opens it.
            player.getInventory().setSelectedSlot(8);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(8));
            level.setDayTime(6000);
            return null;
        });
        say("NOTE setup, not play: Wizardkind heritage, 7 Sickles and 40 Knuts in the pocket, 60 Galleons in the vault");
        // Set down on the ground short of the site: the last stretch is on foot, from wherever the land allows.
        BlockPos landing = server(level -> {
            for (Direction side : List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST)) {
                BlockPos at = centre.relative(side, Math.max(box.getXSpan(), box.getZSpan()) / 2 + 24);
                level.getChunk(at);
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
                if (!water(level, top.below())) {
                    return top;
                }
            }
            return null;
        });
        check("dry ground on some side of the site", landing != null, landing == null ? "" : landing.toShortString());
        view(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, 180f, 0f);
        until(() -> client(() -> mc().player.onGround() || mc().player.isInWater()), 400);
        shot("arrival", true);
        BlockPos plaza = server(HamletPlaytest::plazaCell);
        if (!walk("the plaza", route(cell -> cell.closerThan(plaza, 3.0)))) {
            say("NOTE put on the plaza by hand so the rest of the walk can be judged");
            view(plaza.getX() + 0.5, plaza.getY(), plaza.getZ() + 0.5, 0f, 0f);
        }
        boolean staffed = until(() -> people().size() >= 5, 2_400);
        check("all five people came to their posts for a player on foot", staffed, people());
        shot("plaza_arrival", true);
    }

    // -- the Keeper's door ----------------------------------------------------------------------------------

    /** What the server saw at the watched door, a line a tick it changed, and who stood at it. Read and cleared by the script. */
    private static final List<String> doorLog = java.util.Collections.synchronizedList(new ArrayList<>());
    private static volatile BlockPos watchedDoor;
    private static Boolean doorWasOpen;

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        BlockPos door = watchedDoor;
        if (door == null) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        BlockState state = level.getBlockState(door);
        if (!state.is(BlockTags.DOORS)) {
            return;
        }
        boolean open = state.getValue(DoorBlock.OPEN);
        if (doorWasOpen != null && doorWasOpen != open) {
            doorLog.add("tick " + level.getGameTime() + " door " + (open ? "opened" : "closed") + "; " + atDoor(level, door));
        }
        doorWasOpen = open;
    }

    /** Everyone within four blocks of a door: where they stand, whether their body is in the doorway, what they are doing. */
    private static String atDoor(ServerLevel level, BlockPos door) {
        return nearby(level, door, 4.0);
    }

    /** Everyone within {@code reach} of a cell: where they stand, whether their body is in it, what they are doing. */
    private static String nearby(ServerLevel level, BlockPos door, double reach) {
        AABB cell = new AABB(door).expandTowards(0, 1, 0);
        List<String> near = new ArrayList<>();
        for (Entity e : level.getEntities((Entity) null, new AABB(door).inflate(reach), e -> e.hasData(ModAttachments.SETTLEMENT_POST.get())
                || e instanceof ServerPlayer)) {
            String doing = e instanceof net.minecraft.world.entity.Mob mob ? mob.goalSelector.getAvailableGoals().stream()
                    .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                    .map(g -> g.getGoal().getClass().getSimpleName()).toList().toString() : "player";
            near.add(String.format("%s at %.2f,%.2f,%.2f%s %s", e.getName().getString(), e.getX(), e.getY(), e.getZ(),
                    e.getBoundingBox().intersects(cell) ? " IN THE DOORWAY" : "", doing));
        }
        return near.isEmpty() ? "nobody near" : String.join("; ", near);
    }

    /**
     * The Keeper's door, used as a player uses it, again and again while the people go about their day: walk up from the
     * plaza, look at it, right-click, step through, come back out, shut it. Each try records what the crosshair found, what
     * the door did, and who stood where; the server logs every swing of the door and who was at it.
     */
    private static void door() {
        Map<String, Person> byRole = new HashMap<>();
        people().forEach(person -> byRole.put(person.role(), person));
        Person keeper = byRole.get("keeper");
        if (keeper == null) {
            say("FAIL no keeper");
            return;
        }
        BlockPos door = nearest(doors(), keeper.pos());
        Vec3 outside = towardPlaza(door, 2.6);
        BlockPos stand = BlockPos.containing(outside.x, door.getY(), outside.z);
        BlockPos inside = door.relative(Direction.getApproximateNearest(Vec3.atCenterOf(door).subtract(outside).x, 0,
                Vec3.atCenterOf(door).subtract(outside).z));
        say("NOTE the Keeper's door at " + door.toShortString() + ", " + client(() -> mc().level.getBlockState(door).toString())
                + "; tries from " + stand.toShortString() + ", through to " + inside.toShortString());
        watchedDoor = door;
        doorWasOpen = null;
        int tries = Integer.parseInt(System.getenv().getOrDefault("WB_PLAYTEST_DOOR_TRIES", "12"));
        int[] natural = new int[4];
        for (int attempt = 1; attempt <= tries; attempt++) {
            tryDoor("try " + attempt, door, stand, inside, natural);
            ticks(200 + 40 * attempt);
        }
        check("the Keeper's door opens to a right click on it", natural[0] == tries, natural[0] + " of " + tries);
        check("the crosshair finds the door, not a person behind it", natural[1] == 0, natural[1] + " of " + tries + " on a person");
        check("nobody shuts it on the player", natural[2] == 0, natural[2] + " of " + tries);
        check("the player gets through", natural[3] == natural[0], natural[3] + " of " + natural[0]);
        // The alpha's report, made certain instead of waited for: someone standing still right behind the door, then in the
        // doorway itself. Setup, said as such: the person is put there and held still for the one try.
        for (String where : List.of("behind", "in the doorway of")) {
            BlockPos spot = where.equals("behind") ? inside : door;
            say("NOTE setup: " + server(level -> {
                Entity person = level.getEntity(keeper.id());
                if (!(person instanceof net.minecraft.world.entity.Mob mob)) {
                    return "the keeper is not loaded";
                }
                mob.setNoAi(true);
                mob.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                return "the keeper put " + where + " the door, held still, at " + spot.toShortString();
            }));
            int[] placed = new int[4];
            tryDoor("someone " + where + " the door", door, stand, inside, placed);
            say(String.format("NOTE with someone %s the door: opened %d, crosshair on a person %d, shut on us %d, through %d",
                    where, placed[0], placed[1], placed[2], placed[3]));
            server(level -> {
                if (level.getEntity(keeper.id()) instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setNoAi(false);
                }
                return null;
            });
            ticks(100);
        }
        watchedDoor = null;
    }

    /** One try at a door: walk up, look, right-click, step through, come back. Adds to {opened, on a person, shut, through}. */
    private static void tryDoor(String label, BlockPos door, BlockPos stand, BlockPos inside, int[] tally) {
        walk("to the Keeper's door, " + label, route(cell -> cell.equals(stand)));
        closeDoorIfOpen(door);
        String near = server(level -> atDoor(level, door));
        // As a player does: at the door, at chest height.
        aim(Vec3.atBottomCenterOf(door).add(0, 1.0, 0));
        ticks(5);
        String looking = client(() -> mc().hitResult instanceof BlockHitResult hit
                ? "block " + mc().level.getBlockState(hit.getBlockPos()).getBlock() + " at " + hit.getBlockPos().toShortString()
                : mc().hitResult instanceof EntityHitResult hit ? "PERSON " + hit.getEntity().getName().getString()
                : String.valueOf(mc().hitResult == null ? "nothing" : mc().hitResult.getType()));
        doorLog.clear();
        rightClick();
        boolean open = until(() -> !closedDoor(door), 20);
        ticks(20);
        boolean stillOpen = !closedDoor(door);
        String screen = client(() -> String.valueOf(mc().screen));
        closeScreen();
        boolean through = open && walk("through the Keeper's door, " + label, route(cell -> cell.equals(inside)));
        tally[0] += open ? 1 : 0;
        tally[1] += looking.startsWith("PERSON") ? 1 : 0;
        tally[2] += open && !stillOpen ? 1 : 0;
        tally[3] += through ? 1 : 0;
        say(String.format("NOTE %s: crosshair on %s; door %s%s; screen after the click %s; through %s; at the door: %s; door log %s",
                label, looking, open ? "opened" : "DID NOT OPEN", open && !stillOpen ? " then SHUT within a second" : "", screen,
                through, near, List.copyOf(doorLog)));
        if (!open || !through) {
            shot("door_" + label.replace(' ', '_'), true);
        }
        if (through) {
            walk("back out of the Keeper's, " + label, route(cell -> cell.equals(stand)));
        }
    }

    private static void closeDoorIfOpen(BlockPos door) {
        server(level -> {
            BlockState state = level.getBlockState(door);
            if (state.is(BlockTags.DOORS) && state.getValue(DoorBlock.OPEN)) {
                // Setup between tries, and logged as such: the next try starts at a shut door.
                doorLog.add("setup shut the door");
                ((DoorBlock) state.getBlock()).setOpen(null, level, state, door, false);
            }
            return null;
        });
        ticks(5);
    }

    // -- the Undercroft's knocker --------------------------------------------------------------------------

    /** The knocker's own shape, in the world: what a player looks at, which is not the middle of its cell. */
    private static Vec3 shapeCentre(BlockPos pos) {
        return client(() -> {
            var shape = mc().level.getBlockState(pos).getShape(mc().level, pos);
            return shape.isEmpty() ? Vec3.atCenterOf(pos) : shape.bounds().getCenter().add(Vec3.atLowerCornerOf(pos));
        });
    }

    /**
     * The Undercroft, from the plaza, on foot: down through the trapdoor and the ladder, to the knocker. The crosshair must
     * be on the knocker before the click; the server must see it pressed; the seal must open, and still be open after the
     * world is closed and opened again.
     */
    private static void knock() {
        BlockPos seal = nearestBlock(ModBlocks.CHAMBER_SEAL.get());
        check("the seal stands in the hamlet", seal != null, seal == null ? "" : seal.toShortString());
        if (seal == null) {
            return;
        }
        BlockPos button = server(level -> {
            for (BlockPos pos : BlockPos.betweenClosed(seal.offset(-5, -3, -5), seal.offset(5, 3, 5))) {
                if (level.getBlockState(pos).is(BlockTags.BUTTONS)) {
                    return pos.immutable();
                }
            }
            return null;
        });
        check("a knocker stands near the seal", button != null, button == null ? "" : button.toShortString()
                + " " + server(level -> level.getBlockState(button).toString()));
        if (button == null) {
            return;
        }
        say("NOTE around the cellar before going down: " + server(level -> {
            int water = 0;
            int logged = 0;
            for (BlockPos pos : BlockPos.betweenClosed(seal.offset(-9, -2, -9), seal.offset(9, 4, 9))) {
                if (!level.getFluidState(pos).isEmpty()) {
                    water++;
                    logged += level.getBlockState(pos).hasProperty(BlockStateProperties.WATERLOGGED) ? 1 : 0;
                }
            }
            return "water " + water + ", waterlogged " + logged;
        }));
        Vec3 knob = shapeCentre(button);
        boolean there = walk("the Undercroft's knocker", route(cell -> Vec3.atBottomCenterOf(cell).add(0, 1.62, 0)
                .distanceTo(knob) < 3.0 && Math.abs(cell.getY() - button.getY()) <= 2));
        check("the knocker is reached on foot, through the trapdoor and down the ladder", there,
                client(() -> mc().player.blockPosition().toShortString()));
        shot("knock_before", true);
        check("the seal starts closed", !server(level -> level.getBlockState(seal).getValue(ChamberSealBlock.OPEN)), "");
        aim(knob);
        boolean aimed = until(() -> client(() -> mc().hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(button)), 40);
        String looking = client(() -> mc().hitResult instanceof BlockHitResult hit
                ? mc().level.getBlockState(hit.getBlockPos()).getBlock() + " at " + hit.getBlockPos().toShortString()
                : String.valueOf(mc().hitResult == null ? "nothing" : mc().hitResult.getType()));
        double reach = client(() -> mc().player.getEyePosition().distanceTo(knob));
        check("the crosshair finds the knocker", aimed, looking + String.format(", %.1f blocks from the eye", reach));
        String held = client(() -> mc().player.getMainHandItem().toString());
        rightClick();
        boolean pressed = until(() -> server(level -> level.getBlockState(button).getValue(ButtonBlock.POWERED)), 20);
        check("the server pressed the knocker", pressed, "hand " + held);
        boolean opened = until(() -> server(level -> level.getBlockState(seal).getValue(ChamberSealBlock.OPEN)), 60);
        check("the knocker opens the seal", opened, server(level -> level.getBlockState(seal).toString()));
        ticks(10);
        shot("knock_open", true);
        boolean through = walk("through the seal", route(cell -> cell.closerThan(seal, 1.5) && !cell.equals(seal)
                && !cell.equals(seal.above())));
        shot("knock_inside", true);
        check("the player walks through the opened seal", through, "");
        // Closed and opened again, as a player quits and comes back: the seal is saved with its chunk.
        client(() -> {
            mc().disconnectFromWorld(net.minecraft.network.chat.Component.literal("playtest"));
            return null;
        });
        until(() -> mc().level == null, 6_000);
        // A world asked for while the client is still on its way out is never opened; the title screen comes first.
        until(() -> client(() -> mc().screen instanceof TitleScreen), 6_000);
        ticks(40);
        openWorld();
        boolean kept = until(() -> server(level -> level.getBlockState(seal).getValue(ChamberSealBlock.OPEN)), 200);
        check("the seal is still open after the world was closed and opened again", kept,
                server(level -> level.getBlockState(seal).toString()));
        shot("knock_after_reopen", true);
    }

    private static void keeper(Person keeper) {
        if (keeper == null) {
            say("FAIL no keeper");
            return;
        }
        walkToPerson(keeper);
        if (!speak(keeper)) {
            return;
        }
        talkThrough("keeper");
        scales();
        LocationView view = view();
        check("the Keeper's board offers work", view != null && !view.offers().isEmpty(), view == null ? "" : view.offers().size());
        if (view != null && !view.offers().isEmpty()) {
            boolean accepted = press("ACCEPT", view.offers().get(0).id());
            check("a contract is accepted", accepted && view() != null && !view().active().isEmpty(), view.offers().get(0).id());
            shot("keeper_contract", true);
        }
        closeScreen();
    }

    private static void wandmaker(Person wandmaker) {
        if (wandmaker == null) {
            say("FAIL no wandmaker");
            return;
        }
        walkToPerson(wandmaker);
        if (!speak(wandmaker)) {
            return;
        }
        talkThrough("wandmaker");
        buyFirst("wandmaker");
        closeScreen();
        useBlock("the Wandmaker's Bench", ModBlocks.WANDMAKERS_BENCH.get(), "bench");
    }

    private static void apothecary(Person apothecary) {
        if (apothecary == null) {
            say("FAIL no apothecary");
            return;
        }
        walkToPerson(apothecary);
        if (!speak(apothecary)) {
            return;
        }
        talkThrough("apothecary");
        buyFirst("apothecary");
        long held = vault();
        server(level -> {
            me(level).getData(ModAttachments.VAULT_DATA.get()).withdrawSmartKnuts(held);
            return null;
        });
        ticks(5);
        LocationView view = view();
        MarketLine line = view == null ? null : view.market().stream().filter(l -> !l.buying() && l.count() > 0).findFirst().orElse(null);
        if (line != null) {
            int shelf = line.count();
            press("BUY", line.good() + "|1");
            shot("apothecary_cannot_afford", true);
            ticks(20);
            int left = view() == null ? -9 : view().market().stream().filter(l -> !l.buying() && l.good().equals(line.good()))
                    .mapToInt(MarketLine::count).findFirst().orElse(-9);
            check("an empty vault buys nothing and takes nothing from the shelf", vault() == 0 && left == shelf, "shelf " + shelf + " -> " + left);
        }
        server(level -> {
            me(level).getData(ModAttachments.VAULT_DATA.get()).depositKnuts(held);
            return null;
        });
        closeScreen();
        useBlock("a cauldron", ModBlocks.PEWTER_CAULDRON.get(), "cauldron");
    }

    private static void scholar(Person scholar) {
        if (scholar == null) {
            say("FAIL no scholar");
            return;
        }
        walkToPerson(scholar);
        if (!speak(scholar)) {
            return;
        }
        talkThrough("scholar");
        LocationView view = view();
        boolean offered = view != null && view.services().stream().anyMatch(s -> s.id().getPath().equals("lesson_astronomy") && s.available());
        check("the scholar offers a lesson", offered, view == null ? "" : view.services().size() + " services");
        boolean taught = press("USE_SERVICE", "wizards_and_beasts:lesson_astronomy");
        shot("scholar_lesson", true);
        check("a lesson can be taken", taught, "");
        closeScreen();
        useBlock("the examination desk", ModBlocks.EXAMINATION_DESK.get(), "desk");
        useBlock("the Study Lectern", ModBlocks.SPELL_TEACHER.get(), "lectern");
        useBlock("a chest", Blocks.CHEST, "chest");
    }

    private static void teller(Person teller) {
        if (teller == null) {
            say("FAIL no teller");
            return;
        }
        walkToPerson(teller);
        if (!speak(teller)) {
            return;
        }
        check("the teller opens the Gringotts counter", client(() -> mc().screen instanceof GringottsScreen), client(() -> String.valueOf(mc().screen)));
        shot("teller_screen", true);
        long start = vault();
        int sickles = pocket(CurrencyItemRegistry.SICKLE.get());
        for (int i = 0; i < 7; i++) {
            ledger(2);
        }
        ticks(10);
        check("seven Sickles deposited coin by coin", vault() - start == 203 && pocket(CurrencyItemRegistry.SICKLE.get()) == sickles - 7,
                "vault +" + (vault() - start) + ", pocket " + sickles + " -> " + pocket(CurrencyItemRegistry.SICKLE.get()));
        long mid = vault();
        int knuts = pocket(CurrencyItemRegistry.KNUT.get());
        for (int i = 0; i < 29; i++) {
            ledger(5);
        }
        ticks(10);
        check("twenty-nine Knuts withdrawn coin by coin", mid - vault() == 29 && pocket(CurrencyItemRegistry.KNUT.get()) == knuts + 29,
                "vault -" + (mid - vault()) + ", pocket " + knuts + " -> " + pocket(CurrencyItemRegistry.KNUT.get()));
        shot("teller_after", true);
        check("the vault stands at the sum of what moved", vault() == start + 203 - 29, vault());
        closeScreen();
    }

    /** One of the coin rows' arrows, by position among the screen's widgets: Galleon in and out, Sickle, Knut. */
    private static void ledger(int widget) {
        client(() -> {
            AbstractWidget button = (AbstractWidget) mc().screen.children().get(widget);
            mouseClick(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
            return null;
        });
        ticks(2);
    }

    private static BlockPos nearestBlock(Block block) {
        return server(level -> {
            BlockPos here = me(level).blockPosition();
            BlockPos best = null;
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (level.getBlockState(pos).is(block) && (best == null || pos.distSqr(here) < best.distSqr(here))) {
                    best = pos.immutable();
                }
            }
            return best;
        });
    }

    /** Walks to a working block, faces it, right-clicks it, and shows what opened. */
    private static void useBlock(String what, Block block, String name) {
        BlockPos pos = nearestBlock(block);
        check(what + " stands in the hamlet", pos != null, pos == null ? "" : pos.toShortString());
        if (pos == null) {
            return;
        }
        walk("to " + what, route(cell -> Vec3.atCenterOf(cell).distanceTo(Vec3.atCenterOf(pos)) < 2.6 && Math.abs(cell.getY() - pos.getY()) <= 1));
        aim(Vec3.atCenterOf(pos));
        boolean aimed = until(() -> client(() -> mc().hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)), 40);
        check("the crosshair finds " + what, aimed, client(() -> String.valueOf(mc().hitResult == null ? "" : mc().hitResult.getType())));
        rightClick();
        ticks(20);
        say("NOTE " + what + " answered with: " + client(() -> String.valueOf(mc().screen)));
        shot("use_" + name, true);
        closeScreen();
    }

    private static void undercroft() {
        BlockPos seal = nearestBlock(ModBlocks.CHAMBER_SEAL.get());
        check("the seal stands in the hamlet", seal != null, seal == null ? "" : seal.toShortString());
        if (seal == null) {
            return;
        }
        BlockPos button = server(level -> {
            for (BlockPos pos : BlockPos.betweenClosed(seal.offset(-5, -3, -5), seal.offset(5, 3, 5))) {
                if (level.getBlockState(pos).is(BlockTags.BUTTONS)) {
                    return pos.immutable();
                }
            }
            return null;
        });
        check("a knocker stands near the seal", button != null, button == null ? "" : button.toShortString());
        if (button == null) {
            return;
        }
        boolean there = walk("the Undercroft's knocker", route(cell -> Vec3.atCenterOf(cell).distanceTo(Vec3.atCenterOf(button)) < 2.6
                && Math.abs(cell.getY() - button.getY()) <= 2));
        shot("undercroft_wall", true);
        if (!there) {
            say("NOTE the knocker was not reached on foot; pressing it from where the walk ended");
        }
        boolean closed = !server(level -> level.getBlockState(seal).getValue(ChamberSealBlock.OPEN));
        check("the seal starts closed", closed, "");
        aim(Vec3.atCenterOf(button));
        rightClick();
        boolean opened = until(() -> server(level -> level.getBlockState(seal).getValue(ChamberSealBlock.OPEN)), 60);
        check("the knocker opens the seal, right-clicked", opened, "");
        ticks(10);
        shot("undercroft_open", true);
        walk("through the seal", route(cell -> cell.closerThan(seal, 1.5) ));
        shot("undercroft_inside", true);
    }

    /** The board at every GUI scale a player can pick, then in a small window, to judge text and layout. */
    private static void scales() {
        for (int scale : new int[] {1, 2, 3, 4}) {
            client(() -> {
                mc().options.guiScale().set(scale);
                mc().resizeDisplay();
                return null;
            });
            ticks(5);
            shot("scale_" + scale, true);
        }
        client(() -> {
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            return null;
        });
        ticks(5);
    }

    private static void chart() {
        long began = System.nanoTime();
        server(level -> {
            ServerPlayer player = me(level);
            if (!HeritageAPI.hasHeritageSelected(player)) {
                HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            }
            PathRecord record = PathRecord.EMPTY;
            for (PathStep step : PathStep.values()) {
                record = step == PathStep.ARRIVE ? record : record.with(step);
            }
            player.setData(ModAttachments.APPRENTICE_PATH.get(), record);
            ApprenticePathService.evaluate(player);
            return null;
        });
        say("NOTE setup, not play: every step but the first recorded done; the last step was then met through the real evaluate()");
        boolean given = until(() -> server(level -> me(level).getData(ModAttachments.KNOWN_PLACES.get()).charted()), 2_400);
        check("the Wayfarer's Chart is handed over when the Path ends", given, (System.nanoTime() - began) / 1_000_000 + " ms");
        boolean once = server(level -> {
            ServerPlayer player = me(level);
            int maps = 0;
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                maps += stack.is(Items.FILLED_MAP) ? 1 : 0;
            }
            say("NOTE filled maps carried: " + maps);
            for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
                if (player.getInventory().getItem(slot).getHoverName().getString().contains("Wayfarer")) {
                    player.getInventory().setSelectedSlot(slot < 9 ? slot : 0);
                    if (slot >= 9) {
                        ItemStack chart = player.getInventory().getItem(slot);
                        player.getInventory().setItem(slot, player.getInventory().getItem(0));
                        player.getInventory().setItem(0, chart);
                    }
                    return true;
                }
            }
            return false;
        });
        check("the chart is in the pack", once, "");
        ticks(40);
        shot("chart_in_hand", true);
    }
}
