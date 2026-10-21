package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.ability.wheel.AbilityWheelScreen;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.bestiary.gui.BestiaryScreen;
import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.client.ministry.gui.MinistryLicenceScreen;
import at.koopro.wizardsandbeasts.client.owl.screen.OWLExamScreen;
import at.koopro.wizardsandbeasts.client.owl.screen.OWLResultsReadOnlyScreen;
import at.koopro.wizardsandbeasts.client.owl.screen.ProfessionSelectionScreen;
import at.koopro.wizardsandbeasts.client.skill.gui.SkillTreeScreen;
import at.koopro.wizardsandbeasts.client.spell.gui.SpellMenuScreen;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderInteractHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Photographs the player-facing screens through a real client, at the GUI scales players actually use,
 * so the shared UI language is judged by looking at every screen side by side.
 *
 * <p>Development only, and inert unless {@code WB_UI_CAPTURE} names an output folder under
 * {@code screenshots/}. {@code WB_UI_CAPTURE_WORLD} names the save to open (a disposable copy: the
 * wandmaker's bench is placed into it). The window is 1920x1080 and every screen is shot at GUI scale 3
 * and at 4 -- the scale vanilla's "Auto" picks at 1080p, and so the tightest a player normally sees. A
 * screen opened through a menu (the bench, Ollivander's trial) is opened the way the server opens it;
 * the rest are opened on the client as their keybinds do. {@code WB_UI_CAPTURE_ONLY} (comma-separated
 * names) narrows the run. The client quits after the last shot.
 *
 * <p>Scaling sweeps: {@code WB_UI_CAPTURE_SCALES} (comma-separated, default {@code 3,4}) picks the GUI
 * scales, and {@code WB_UI_CAPTURE_WINDOW} ({@code WIDTHxHEIGHT}, default {@code 1920x1080}) the window --
 * e.g. {@code 2560x1080} for ultrawide or {@code 1280x720} for a small screen. When a window is named,
 * the files carry it ({@code spell_wheel__1280x720__s3.png}) so sweeps can share one folder.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class UiScreenCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LOAD_TICKS = 100;
    private static final int SETTLE_TICKS = 25;
    private static final String OUT = FMLEnvironment.isProduction() ? null : System.getenv("WB_UI_CAPTURE");
    private static final int[] SCALES = parseScales(System.getenv("WB_UI_CAPTURE_SCALES"));
    private static final String WINDOW = System.getenv("WB_UI_CAPTURE_WINDOW");
    private static final int WINDOW_W = windowSide(WINDOW, 0, 1920);
    private static final int WINDOW_H = windowSide(WINDOW, 1, 1080);

    /** One screen: its file name, and how to open it (on the client, or on the server for its player). */
    private record Step(String name, Runnable client, Consumer<ServerPlayer> server) {
        static Step client(String name, Runnable open) {
            return new Step(name, open, null);
        }

        static Step server(String name, Consumer<ServerPlayer> open) {
            return new Step(name, null, open);
        }
    }

    private static List<Step> steps = List.of();
    private static boolean opened;
    private static boolean running;
    private static int index = -1;
    private static int scale;
    private static int wait;
    private static boolean captureThisFrame;

    private UiScreenCapture() {}

    private static int[] parseScales(String spec) {
        if (spec == null || spec.isBlank()) {
            return new int[] {3, 4};
        }
        return java.util.Arrays.stream(spec.split(",")).map(String::trim).filter(t -> !t.isEmpty())
                .mapToInt(Integer::parseInt).toArray();
    }

    private static int windowSide(String spec, int index, int fallback) {
        if (spec == null || !spec.matches("\\d+x\\d+")) {
            return fallback;
        }
        return Integer.parseInt(spec.split("x")[index]);
    }

    /** Sixteen memorised places, the most a seasoned Apparator keeps: enough to make the list scroll. */
    private static void openApparition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        List<at.koopro.wizardsandbeasts.apparition.ApparitionPoint> points = new java.util.ArrayList<>();
        String[] names = {"Home", "Diagon Alley gate", "Hogsmeade, the Three Broomsticks", "Ottery St Catchpole",
                "Mine", "Forest edge", "Lake", "Ministry visitors' entrance", "Shell Cottage", "Godric's Hollow",
                "Spinner's End", "The Burrow", "Little Hangleton", "Tower", "Camp", "Mountain pass"};
        for (int i = 0; i < names.length; i++) {
            var dimension = i == 6 ? net.minecraft.world.level.Level.NETHER : mc.level.dimension();
            points.add(new at.koopro.wizardsandbeasts.apparition.ApparitionPoint(names[i], dimension,
                    mc.player.position().add(40 * i, 0, -25 * i), 0f));
        }
        at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPointsState.accept(points);
        open(new at.koopro.wizardsandbeasts.client.apparition.gui.ApparitionSelectorScreen());
    }

    private static List<Step> plan() {
        Map<OWLSubject, OWLGrade> grades = new EnumMap<>(OWLSubject.class);
        OWLGrade[] spread = OWLGrade.values();
        int i = 0;
        for (OWLSubject subject : OWLSubject.values()) {
            grades.put(subject, spread[i++ % spread.length]);
        }
        List<Step> all = new java.util.ArrayList<>(List.of(
                Step.client("character_sheet", () -> open(new CharacterSheetScreen())),
                Step.client("skill_web", () -> open(new SkillTreeScreen())),
                Step.client("spell_menu", () -> open(new SpellMenuScreen())),
                Step.client("ability_wheel", () -> open(new AbilityWheelScreen())),
                Step.client("bestiary", () -> open(new BestiaryScreen())),
                Step.client("owl_exam", () -> open(new OWLExamScreen())),
                Step.client("education_tab", () -> open(CharacterSheetScreen.onEducationTab())),
                Step.client("owl_sitting", UiScreenCapture::openSitting),
                Step.client("owl_results", () -> open(new OWLResultsReadOnlyScreen(grades, null))),
                Step.client("owl_profession", () -> open(new ProfessionSelectionScreen(grades))),
                Step.client("ministry_licence", () -> open(new MinistryLicenceScreen(false))),
                Step.server("wandmakers_bench", UiScreenCapture::openBench),
                Step.server("ollivander_trial", OllivanderInteractHandler::openTrial),
                Step.client("admin_control_center", AdminClientRequests::open),
                // The 2026-10-15 finishing pass: the screens the first audit did not reach.
                Step.client("spell_wheel", () -> open(new at.koopro.wizardsandbeasts.client.spell.wheel.SpellWheelScreen())),
                Step.client("apparition", UiScreenCapture::openApparition),
                Step.client("imperio", () -> open(new at.koopro.wizardsandbeasts.client.spell.gui.ImperioCommandScreen())),
                Step.client("mirror_call", () -> open(new at.koopro.wizardsandbeasts.client.trinket.gui.MirrorCallScreen(
                        java.util.UUID.randomUUID()))),
                Step.client("mirror_view", () -> open(new at.koopro.wizardsandbeasts.client.trinket.gui.MirrorViewScreen(
                        Minecraft.getInstance().player.getUUID(), Minecraft.getInstance().player.getName().getString()))),
                Step.client("diary_write", () -> open(new at.koopro.wizardsandbeasts.client.trinket.gui.DiaryWriteScreen())),
                Step.client("diary_possession", () -> open(new at.koopro.wizardsandbeasts.client.trinket.gui.DiaryPossessionScreen(
                        "Let me tell you about the Chamber."))),
                Step.client("config", () -> open(new at.koopro.wizardsandbeasts.client.gui.config.WizardsConfigScreen(null))),
                Step.client("config_heritage", () -> open(
                        at.koopro.wizardsandbeasts.client.gui.config.WizardsConfigScreen.chapter(null, "Heritage"))),
                Step.client("config_gate", () -> open(
                        at.koopro.wizardsandbeasts.client.gui.config.WizardsConfigScreen.darkArtsGate(null)))));
        // Every page of the sheet, as sheet_<page>: the progression hub is judged page by page.
        for (at.koopro.wizardsandbeasts.client.gui.character.SheetTab tab
                : at.koopro.wizardsandbeasts.client.gui.character.SheetTab.values()) {
            all.add(Step.client("sheet_" + tab.name().toLowerCase(java.util.Locale.ROOT),
                    () -> open(CharacterSheetScreen.onTab(tab))));
        }
        String only = System.getenv("WB_UI_CAPTURE_ONLY");
        if (only == null || only.isBlank()) {
            return all;
        }
        List<String> wanted = List.of(only.split(","));
        return all.stream().filter(s -> wanted.contains(s.name())).toList();
    }

    private static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /**
     * The desk mid-sitting: opens an O.W.L. sitting for the player on the integrated server (no desk needed for a
     * photograph), then the desk screen, which re-lays itself out when the view arrives within the settle time.
     */
    private static void openSitting() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server != null && mc.player != null) {
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    // The fullest sitting, an O.W.L. (nine papers): the capture save is a disposable copy, so its
                    // resit wait is simply lapsed.
                    var owl = player.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.OWL_DATA.get());
                    player.setData(at.koopro.wizardsandbeasts.registry.ModAttachments.OWL_DATA.get(),
                            new at.koopro.wizardsandbeasts.owl.data.PlayerOWLData(owl.grades(), owl.examTaken(),
                                    owl.sittings(), 0L, owl.newtGrades(), owl.newtSittings(), owl.newtLastSatTick()));
                    at.koopro.wizardsandbeasts.education.ExamSittings.begin(player,
                            at.koopro.wizardsandbeasts.owl.Qualification.OWL, false);
                    at.koopro.wizardsandbeasts.education.ExamSittings.onWork(player,
                            at.koopro.wizardsandbeasts.owl.career.CareerWork.Kind.CHARMWORK);
                }
            });
        }
        open(new OWLExamScreen());
    }

    /** Where the bench was placed while the world loaded: the client must have the block before the menu opens. */
    private static BlockPos benchPos;

    private static void placeBench(ServerPlayer player) {
        benchPos = player.blockPosition().relative(player.getDirection(), 2);
        BlockState bench = BuiltInRegistries.BLOCK
                .getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wandmakers_bench"))
                .defaultBlockState();
        player.level().setBlockAndUpdate(benchPos, bench);
    }

    /** Uses the bench, as a right click does. */
    private static void openBench(ServerPlayer player) {
        if (benchPos == null) {
            return;
        }
        ServerLevel level = player.level();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(benchPos), Direction.UP, benchPos, false);
        level.getBlockState(benchPos).useWithoutItem(level, player, hit);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (OUT == null || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        steps = plan();
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), WINDOW_W, WINDOW_H);
        LOGGER.info("UI capture: {} screens x {} scales into {}", steps.size(), SCALES.length, OUT);
        mc.createWorldOpenFlows().openWorld(System.getenv().getOrDefault("WB_UI_CAPTURE_WORLD", "wb_ui_capture"),
                () -> LOGGER.warn("UI capture: world did not open"));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (OUT == null || !opened) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            return;
        }
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (!running) {
            running = true;
            wait = LOAD_TICKS;
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    placeBench(player);
                }
            });
            return;
        }
        if (captureThisFrame || --wait > 0) {
            return;
        }
        if (index < 0) {
            index = 0;
            scale = 0;
            present(mc, server);
            return;
        }
        mc.getToastManager().clear();
        mc.gui.getChat().clearMessages(false);
        captureThisFrame = true;
    }

    /** Sets the scale for this shot and opens the step's screen. */
    private static void present(Minecraft mc, MinecraftServer server) {
        if (mc.options.guiScale().get() != SCALES[scale]) {
            mc.options.guiScale().set(SCALES[scale]);
            mc.resizeDisplay();
        }
        Step step = steps.get(index);
        if (step.client() != null) {
            step.client().run();
        } else {
            mc.setScreen(null);
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    step.server().accept(player);
                }
            });
        }
        wait = SETTLE_TICKS;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!captureThisFrame) {
            return;
        }
        captureThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        Path file = mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR).resolve(OUT)
                .resolve(steps.get(index).name() + (WINDOW == null ? "" : "__" + WINDOW_W + "x" + WINDOW_H)
                        + "__s" + SCALES[scale] + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("UI capture: cannot write {}", file, e);
            }
        }));
        if (++scale >= SCALES.length) {
            scale = 0;
            index++;
        }
        if (index >= steps.size()) {
            LOGGER.info("UI capture: done");
            mc.stop();
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && mc.player != null) {
            present(mc, server);
        }
    }
}
