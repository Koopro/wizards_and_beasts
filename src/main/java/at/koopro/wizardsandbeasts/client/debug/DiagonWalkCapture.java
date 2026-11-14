package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.diagon.DiagonAlley;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a real client into Diagon Alley and out again: the bar of a Leaky Cauldron placed from its own template, the
 * back door used the way a player uses it (the block's own use), the passage between the two worlds, the yard and its
 * wall, the alley, and back through the yard's door to the bar. Shots go to {@code screenshots/diagon_walk}.
 *
 * <p>Development only, inert unless {@code WB_DIAGON_WALK} is set; {@code WB_DIAGON_WALK_WORLD} names a save that must
 * exist (default {@code wb_entity_showcase}).
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class DiagonWalkCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = System.getenv("WB_DIAGON_WALK") != null && !FMLEnvironment.isProduction();
    private static final Identifier PUB = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
            "diagon_alley/leaky_cauldron/a");
    /** The pub's back door, lower half, in its template; it faces into the bar (south). */
    private static final BlockPos PUB_DOOR = new BlockPos(2, 1, 0);

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = List.of(
            new Step("", 60, DiagonWalkCapture::stage),
            new Step("", 30, p -> lookPub(p, 4, 1, 11, 180f)),
            new Step("pub_bar", 10, p -> {}),
            new Step("", 30, p -> lookPub(p, 2, 1, 1, 180f)),
            new Step("", 3, DiagonWalkCapture::useBackDoor),
            new Step("passage", 6, p -> {}),
            new Step("", 40, p -> {}),
            new Step("yard", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 12.6, 7, 17.5, -90f, 0f)),
            new Step("alley", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 17.5, 7, 20.5, 180f, -10f)),
            new Step("ollivanders", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 1.5, 7, 17.5, 90f, 0f)),
            new Step("yard_door", 10, p -> {}),
            new Step("", 3, DiagonWalkCapture::useYardDoor),
            new Step("", 45, p -> {}),
            new Step("back_in_bar", 10, p -> {}));

    private static BlockPos pub = BlockPos.ZERO;
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private DiagonWalkCapture() {}

    /** A Leaky Cauldron a little way off, on the save's ground; noon. */
    private static void stage(ServerPlayer p) {
        ServerLevel level = p.level().getServer().overworld();
        pub = BlockPos.containing(p.position()).offset(40, -1, 0);
        level.getStructureManager().get(PUB).ifPresent(template -> template.placeInWorld(level, pub, pub,
                new StructurePlaceSettings(), level.getRandom(), 2));
        level.setDayTime(6000);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    }

    private static void lookPub(ServerPlayer p, int x, int y, int z, float yaw) {
        look(p, pub.offset(x, y, z), yaw, 0f);
    }

    private static void look(ServerPlayer p, BlockPos feet, float yaw, float pitch) {
        ServerLevel overworld = p.level().getServer().overworld();
        p.teleportTo(overworld, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), yaw, pitch, false);
    }

    private static void lookPocket(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        ServerLevel pocket = p.level().getServer().getLevel(DiagonAlley.LEVEL);
        if (pocket == null) {
            LOGGER.warn("Diagon walk: no pocket level");
            return;
        }
        p.teleportTo(pocket, DiagonAlley.ORIGIN.getX() + x, DiagonAlley.ORIGIN.getY() + y, DiagonAlley.ORIGIN.getZ() + z,
                Set.of(), yaw, pitch, false);
    }

    private static void use(ServerPlayer p, ServerLevel level, BlockPos door) {
        BlockState state = level.getBlockState(door);
        LOGGER.info("Diagon walk: using {} at {}", state, door);
        state.useWithoutItem(level, p, new BlockHitResult(Vec3.atCenterOf(door), Direction.NORTH, door, false));
    }

    private static void useBackDoor(ServerPlayer p) {
        use(p, p.level().getServer().overworld(), pub.offset(PUB_DOOR));
    }

    private static void useYardDoor(ServerPlayer p) {
        ServerLevel pocket = p.level().getServer().getLevel(DiagonAlley.LEVEL);
        if (pocket != null) {
            use(p, pocket, DiagonAlley.ORIGIN.offset(0, 7, 17));
        }
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
                System.getenv().getOrDefault("WB_DIAGON_WALK_WORLD", "wb_entity_showcase"), () -> stage = 3);
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "diagon_walk"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        LOGGER.info("Diagon walk: shot {} in {} (screen {})", SCRIPT.get(step).shot(),
                mc.level == null ? "none" : mc.level.dimension().identifier(), mc.screen);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Diagon walk: cannot write {}", file, e);
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
            LOGGER.info("Diagon walk: done");
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
        if (!(mc.screen instanceof at.koopro.wizardsandbeasts.client.diagon.DiagonPassageScreen)) {
            mc.setScreen(null);
        }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                current.server().accept(player);
            }
        });
    }
}
