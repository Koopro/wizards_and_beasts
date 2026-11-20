package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs the first look at Hogsmeade's high street (H1): the template placed from its own NBT in a disposable save,
 * shot down the street both ways, at Honeydukes and the Three Broomsticks, from above, and at dusk with the windows lit.
 *
 * <p>Development only, inert unless {@code WB_HOGS_LOOK=1}; {@code WB_HOGS_LOOK_WORLD} names the save. Shots go to
 * {@code screenshots/hogsmeade_look}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class HogsmeadeLookCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = System.getenv("WB_HOGS_LOOK") != null && !FMLEnvironment.isProduction();
    private static final Identifier TEMPLATE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hogsmeade/high_street/a");
    private static final Identifier MOUNTAINS =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hogsmeade/mountains/a");
    private static final Identifier SHACK =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hogsmeade/shrieking_shack/a");

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = List.of(
            new Step("", 100, HogsmeadeLookCapture::stage),
            new Step("", 60, p -> look(p, 51.5, 2, 17.5, 90f, 4f)),
            new Step("street_from_east", 10, p -> {}),
            new Step("", 40, p -> look(p, 0.5, 2, 17.5, -90f, 4f)),
            new Step("street_from_west", 10, p -> {}),
            new Step("", 40, p -> look(p, 7.5, 2, 21.0, 180f, -18f)),
            new Step("honeydukes", 10, p -> {}),
            new Step("", 40, p -> look(p, 19.5, 2, 21.0, 180f, -18f)),
            new Step("three_broomsticks", 10, p -> {}),
            new Step("", 40, p -> look(p, 31.5, 2, 14.0, 0f, -18f)),
            new Step("south_side", 10, p -> {}),
            new Step("", 40, p -> look(p, 49.5, 3, 17.5, -105f, -6f)),
            new Step("hogs_head_lane", 10, p -> {}),
            new Step("", 60, p -> look(p, -35.5, 10, 74.5, 180f, 8f)),
            new Step("shrieking_shack", 10, p -> {}),
            new Step("", 60, p -> look(p, 35.5, 26, 96.5, 180f, 14f)),
            new Step("overview", 10, p -> {}),
            new Step("", 60, p -> {
                p.level().setDayTime(12900);
                look(p, 51.5, 2, 17.5, 90f, 4f);
            }),
            new Step("street_at_dusk", 10, p -> {}));

    private static BlockPos base = BlockPos.ZERO;
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private HogsmeadeLookCapture() {}

    private static void stage(ServerPlayer p) {
        ServerLevel level = p.level();
        base = p.blockPosition().offset(-26, -2, -60);
        place(level, TEMPLATE, base);
        place(level, MOUNTAINS, base.offset(-40, 0, -58));
        place(level, SHACK, base.offset(-50, 0, 40));
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
    }

    private static void place(ServerLevel level, Identifier id, BlockPos at) {
        level.getStructureManager().get(id).ifPresentOrElse(template -> template.placeInWorld(level, at, at,
                new StructurePlaceSettings(), level.getRandom(), 2),
                () -> LOGGER.error("Hogsmeade look: template {} missing", id));
    }

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.level(), base.getX() + x, base.getY() + y, base.getZ() + z, Set.of(), yaw, pitch, false);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = 1;
        wait = 80;
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_HOGS_LOOK_WORLD", "wb_entity_showcase"), () -> stage = 3);
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "hogsmeade_look"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Hogsmeade look: cannot write {}", file, e);
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
            LOGGER.info("Hogsmeade look: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        if (!current.shot().isEmpty()) {
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
