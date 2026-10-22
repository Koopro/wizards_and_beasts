package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.artefact.cabinet.CabinetService;
import at.koopro.wizardsandbeasts.artefact.cabinet.VanishingCabinetBlock;
import at.koopro.wizardsandbeasts.artefact.cabinet.VanishingCabinetBlockEntity;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
 * Photographs the placed artefacts, so a block's look is judged in the world rather than in a model viewer: a pair
 * of Vanishing Cabinets head-on, from three-quarters and just after the wand gesture links them (the action-bar line
 * in frame); then the Mirror of Erised close up, the vision screen of a first gaze, and the reflection it shows once
 * the day's gazes are spent.
 *
 * <p>Development only, inert unless {@code WB_ARTEFACT_SHOWCASE=1}. {@code WB_ARTEFACT_SHOWCASE_WORLD} names a
 * disposable save (the stage is built into it); shots go to {@code screenshots/artefact_showcase}.
 */
@EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ArtefactShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED =
            System.getenv("WB_ARTEFACT_SHOWCASE") != null && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = List.of(
            new Step("", 60, ArtefactShowcaseCapture::stage),
            new Step("front", 20, p -> {}),
            new Step("", 30, p -> look(p, 3.5, -0.5, 135f, 12f)),
            new Step("three_quarter", 20, p -> {}),
            new Step("", 20, ArtefactShowcaseCapture::link),
            new Step("linked", 10, p -> {}),
            new Step("", 30, p -> look(p, -3.5, -3.0, 180f, 12f)),
            new Step("erised_world", 20, p -> {}),
            new Step("", 40, p -> at.koopro.wizardsandbeasts.artefact.erised.ErisedService.gaze(p, erised())),
            new Step("erised_vision", 10, p -> {}),
            new Step("", 40, ArtefactShowcaseCapture::spendTheDay),
            new Step("erised_reflection", 10, p -> {}));


    /** Where the stage was built: the player's feet when it started. */
    private static BlockPos origin = BlockPos.ZERO;

    private static boolean opened;
    private static int stage; // 0 idle, 1 loading, 2 running, 3 done
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private ArtefactShowcaseCapture() {}

    /** A stone floor, a clear box above it, noon, and two cabinets facing the camera across it. */
    private static void stage(ServerPlayer p) {
        ServerLevel level = p.level();
        origin = p.blockPosition();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        for (int x = -6; x <= 6; x++) {
            for (int z = -9; z <= 3; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                for (int y = 0; y <= 5; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = -6; x <= 6; x++) {
            for (int y = 0; y <= 4; y++) {
                level.setBlock(origin.offset(x, y, -8), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }
        place(level, origin.offset(-1, 0, -6), Direction.SOUTH, p);
        place(level, origin.offset(2, 0, -6), Direction.SOUTH, p);
        BlockState mirror = ModBlocks.MIRROR_OF_ERISED.get().defaultBlockState()
                .setValue(VanishingCabinetBlock.FACING, Direction.SOUTH);
        level.setBlock(erised(), mirror.setValue(VanishingCabinetBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(erised().above(), mirror.setValue(VanishingCabinetBlock.HALF, DoubleBlockHalf.UPPER), 3);
        p.setData(at.koopro.wizardsandbeasts.registry.ModAttachments.ERISED_HISTORY.get(),
                at.koopro.wizardsandbeasts.artefact.erised.ErisedHistory.EMPTY);
        look(p, 0.5, 0.5, 180f, 8f);
    }

    private static void place(ServerLevel level, BlockPos lower, Direction facing, ServerPlayer owner) {
        BlockState state = ModBlocks.VANISHING_CABINET.get().defaultBlockState()
                .setValue(VanishingCabinetBlock.FACING, facing);
        level.setBlock(lower, state.setValue(VanishingCabinetBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(lower.above(), state.setValue(VanishingCabinetBlock.HALF, DoubleBlockHalf.UPPER), 3);
        if (level.getBlockEntity(lower) instanceof VanishingCabinetBlockEntity be) {
            be.setOwner(owner.getUUID());
        }
    }

    private static void look(ServerPlayer p, double dx, double dz, float yaw, float pitch) {
        p.teleportTo(p.level(), origin.getX() + dx, origin.getY(), origin.getZ() + dz, Set.of(), yaw, pitch, false);
    }

    private static void link(ServerPlayer p) {
        CabinetService.gesture(p, p.level(), origin.offset(-1, 0, -6));
        CabinetService.tell(p, CabinetService.gesture(p, p.level(), origin.offset(2, 0, -6)));
        look(p, 0.5, 0.5, 180f, 8f);
    }

    /** Where the Mirror of Erised stands: left of the cabinets, against the same wall. */
    private static BlockPos erised() {
        return origin.offset(-4, 0, -6);
    }

    /** Uses up the day's gazes, so the next look shows only a reflection. */
    private static void spendTheDay(ServerPlayer p) {
        for (int i = 0; i < 3; i++) {
            at.koopro.wizardsandbeasts.artefact.erised.ErisedService.gaze(p, erised());
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
                System.getenv().getOrDefault("WB_ARTEFACT_SHOWCASE_WORLD", "wb_cabinet_capture"), () -> stage = 3);
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "artefact_showcase"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Artefact showcase: cannot write {}", file, e);
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
            LOGGER.info("Artefact showcase: done");
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
