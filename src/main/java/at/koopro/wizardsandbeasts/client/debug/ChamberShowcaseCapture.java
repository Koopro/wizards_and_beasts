package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.chamber.ChamberHeartBlockEntity;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
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
 * Walks a real client through the Chamber of Secrets and photographs each room: the ring at the surface, the landing,
 * the blank wall at the end of the pipes, the seal opened by its knocker, the basin and its gate dry and full, the hall
 * with its basilisk woken (boss bar in frame), and the sword over the dais once the basilisk is dead.
 *
 * <p>The Chamber is placed from its own template (the generated structure, not a copy), below the player in the
 * capture save. Development only, inert unless {@code WB_CHAMBER_SHOWCASE=1}; {@code WB_CHAMBER_SHOWCASE_WORLD} names
 * a disposable save. Shots go to {@code screenshots/chamber_showcase}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ChamberShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED =
            System.getenv("WB_CHAMBER_SHOWCASE") != null && !FMLEnvironment.isProduction();
    private static final Identifier TEMPLATE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "chamber_of_secrets/chamber");

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = List.of(
            new Step("", 80, ChamberShowcaseCapture::stage),
            new Step("", 40, p -> look(p, 20.5, 37, 13.5, 0f, 35f)),
            new Step("surface", 10, p -> {}),
            new Step("", 30, p -> look(p, 20.5, 7, 22.5, 180f, 5f)),
            new Step("landing", 10, p -> {}),
            new Step("", 30, p -> look(p, 29.5, 7, 6.5, -90f, 5f)),
            new Step("seal_wall", 10, p -> {}),
            new Step("", 30, ChamberShowcaseCapture::knock),
            new Step("seal_open", 10, p -> {}),
            new Step("", 30, p -> look(p, 38.5, 7, 4.5, 30f, 15f)),
            new Step("basin_dry", 10, p -> {}),
            new Step("", 30, ChamberShowcaseCapture::fillBasin),
            new Step("basin_full", 10, p -> {}),
            new Step("", 60, p -> look(p, 32.5, 7, 16.5, 0f, 5f)),
            new Step("hall_awake", 10, p -> {}),
            new Step("", 40, ChamberShowcaseCapture::slay),
            new Step("sword", 10, p -> {}));

    /** Where the template's corner was placed: the Chamber's coordinates are offsets from it. */
    private static BlockPos base = BlockPos.ZERO;

    private static boolean opened;
    private static int stage; // 0 idle, 1 loading, 2 running, 3 done
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private ChamberShowcaseCapture() {}

    /** The Chamber below the player, its ring at their feet; the sword record cleared so the dais can give it. */
    private static void stage(ServerPlayer p) {
        ServerLevel level = p.level();
        base = p.blockPosition().offset(-20, -37, -20);
        level.getStructureManager().get(TEMPLATE).ifPresent(template -> template.placeInWorld(level, base, base,
                new StructurePlaceSettings(), level.getRandom(), 2));
        at.koopro.wizardsandbeasts.artefact.gryffindor.GryffindorSword.get(level).forget();
        level.setDayTime(6000);
        // A dev config written before the Chamber shipped still says "disabled"; the capture runs the shipped game.
        at.koopro.wizardsandbeasts.module.ModuleManager.overrideCached(
                at.koopro.wizardsandbeasts.module.Module.CHAMBER_OF_SECRETS,
                at.koopro.wizardsandbeasts.module.ModuleState.PREVIEW);
        at.koopro.wizardsandbeasts.module.ModuleManager.overrideCached(
                at.koopro.wizardsandbeasts.module.Module.ARTEFACTS,
                at.koopro.wizardsandbeasts.module.ModuleState.ENABLED);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    }

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.level(), base.getX() + x, base.getY() + y, base.getZ() + z, Set.of(), yaw, pitch, false);
    }

    private static BlockPos at(int x, int y, int z) {
        return base.offset(x, y, z);
    }

    private static void knock(ServerPlayer p) {
        BlockPos button = at(33, 9, 6);
        BlockState state = p.level().getBlockState(button);
        if (state.getBlock() instanceof ButtonBlock knocker) {
            knocker.press(state, p.level(), button, p);
        }
    }

    private static void fillBasin(ServerPlayer p) {
        p.level().setBlockAndUpdate(at(35, 7, 8),
                Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
    }

    /** The woken basilisk dies to the player; the shot looks at the dais from the hall. */
    private static void slay(ServerPlayer p) {
        ServerLevel level = p.level();
        if (level.getBlockEntity(at(32, 8, 37)) instanceof ChamberHeartBlockEntity heart && heart.basilisk() != null
                && level.getEntity(heart.basilisk()) instanceof LivingEntity basilisk) {
            basilisk.hurtServer(level, level.damageSources().playerAttack(p), 100_000f);
        }
        look(p, 32.5, 8, 31.5, 0f, 12f);
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
                System.getenv().getOrDefault("WB_CHAMBER_SHOWCASE_WORLD", "wb_chamber_capture"), () -> stage = 3);
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "chamber_showcase"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Chamber showcase: cannot write {}", file, e);
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
            LOGGER.info("Chamber showcase: done");
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
