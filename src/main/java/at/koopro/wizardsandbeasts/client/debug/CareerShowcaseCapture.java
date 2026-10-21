package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
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
 * Photographs the Career tab through the real path: grades and the career choice on the server, bestiary
 * pages through {@code setTier}, a contract accepted from the server's own board, and the view the server
 * syncs. Then the same wizard at the top rank.
 *
 * <p>Development only, inert unless {@code WB_CAREER_SHOWCASE=1}. {@code WB_CAREER_SHOWCASE_WORLD} names a
 * disposable save (default {@code wb_equipment_capture}); shots go to {@code screenshots/career_showcase}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class CareerShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED =
            System.getenv("WB_CAREER_SHOWCASE") != null && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = List.of(
            new Step("", 40, CareerShowcaseCapture::trainee),
            new Step("career_trainee", 30, p -> {}),
            new Step("", 20, CareerShowcaseCapture::master),
            new Step("career_master", 30, p -> {}));

    private static boolean opened;
    private static int stage; // 0 idle, 1 loading, 2 running, 3 done
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private CareerShowcaseCapture() {}

    private static Identifier creature(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static void trainee(ServerPlayer p) {
        HeritageAPI.commit(p, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        p.setData(ModAttachments.CAREER_PROGRESS.get(), PlayerCareerProgress.EMPTY);
        Map<OWLSubject, OWLGrade> grades = new EnumMap<>(OWLSubject.class);
        grades.put(OWLSubject.CARE_OF_MAGICAL_CREATURES, OWLGrade.E);
        grades.put(OWLSubject.CHARMS, OWLGrade.A);
        p.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(grades, true, 1, 1L));
        OWLExaminationHandler.chooseProfession(p, Profession.MAGIZOOLOGIST);
        for (String species : List.of("niffler", "bowtruckle", "mooncalf")) {
            BestiaryDataHelper.setTier(p, creature(species), DiscoveryTier.OBSERVED);
        }
        p.getInventory().add(new ItemStack(WandItemRegistry.UNICORN_HAIR.get(), 1));
        List<CareerView.ContractLine> offers = CareerProgression.view(p).offers();
        if (!offers.isEmpty()) {
            CareerProgression.accept(p, offers.getFirst().id());
        }
        CareerProgression.sync(p);
    }

    private static void master(ServerPlayer p) {
        var progress = p.getData(ModAttachments.CAREER_PROGRESS.get());
        p.setData(ModAttachments.CAREER_PROGRESS.get(),
                progress.with("magizoologist", progress.track("magizoologist").withXp(3100).withRank(4)));
        CareerProgression.sync(p);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = 1;
        wait = 80;
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_CAREER_SHOWCASE_WORLD", "wb_equipment_capture"), () -> stage = 3);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || stage == 0 || stage == 3) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) {
            return;
        }
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (captureThisFrame || --wait > 0) {
            return;
        }
        if (stage == 1) {
            stage = 2;
            step = 0;
            begin(mc, server);
            return;
        }
        if (!SCRIPT.get(step).shot().isEmpty()) {
            mc.getToastManager().clear();
            captureThisFrame = true;
        } else {
            advance(mc, server);
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!captureThisFrame) {
            return;
        }
        captureThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "career_showcase"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Career showcase: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            advance(mc, server);
        }
    }

    private static void advance(Minecraft mc, MinecraftServer server) {
        step++;
        if (step >= SCRIPT.size()) {
            stage = 3;
            LOGGER.info("Career showcase: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        if (!current.shot().isEmpty()) {
            mc.setScreen(CharacterSheetScreen.onCareerTab());
            return;
        }
        mc.setScreen(null);
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                current.server().accept(player);
            }
        });
    }
}
