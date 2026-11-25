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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs one of the small canon places (post-canon audit D3) for the owner's look verdict: the template placed from
 * its own NBT in a disposable save, seen from four sides at a walker's distance, close at its centre, from above, and at
 * dusk.
 *
 * <p>Development only, inert unless {@code WB_PLACES_LOOK=<place>} ({@code portkey_hill}, {@code gaunt_shack},
 * {@code rook_house}); {@code WB_PLACES_LOOK_WORLD} names the save. Shots go to {@code screenshots/places_look/<place>}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class PlacesLookCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PLACE = System.getenv().getOrDefault("WB_PLACES_LOOK", "");
    private static final boolean ENABLED = !PLACE.isEmpty() && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static BlockPos base = BlockPos.ZERO;
    /** Template footprint, read when it is placed; shots are framed from it. */
    private static int sizeX = 16;
    private static int sizeY = 8;
    private static int sizeZ = 16;
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private PlacesLookCapture() {}

    private static void script() {
        SCRIPT.add(new Step("", 100, PlacesLookCapture::stage));
        double reach = Math.max(sizeX, sizeZ) * 1.1 + 6;
        double eye = sizeY * 0.45 + 2;
        String[] sides = {"south", "west", "north", "east"};
        for (int i = 0; i < 4; i++) {
            double yaw = 180 + i * 90;                       // south first, looking north
            double rad = Math.toRadians(yaw);
            double x = sizeX / 2.0 - Math.sin(rad) * -reach;
            double z = sizeZ / 2.0 + Math.cos(rad) * -reach;
            float pitch = (float) Math.toDegrees(Math.atan2(eye - sizeY * 0.35, reach)) ;
            SCRIPT.add(new Step("", 50, p -> look(p, x, eye, z, (float) yaw, pitch)));
            SCRIPT.add(new Step(sides[i], 10, p -> {}));
        }
        SCRIPT.add(new Step("", 50, p -> look(p, sizeX / 2.0 + 2.5, 3.2, sizeZ / 2.0 + 3.5, 145f, 30f)));
        SCRIPT.add(new Step("close", 10, p -> {}));
        SCRIPT.add(new Step("", 50, p -> look(p, sizeX / 2.0, sizeY + reach * 0.9, sizeZ / 2.0 + reach * 0.6,
                180f, 52f)));
        SCRIPT.add(new Step("above", 10, p -> {}));
        SCRIPT.add(new Step("", 60, p -> {
            p.level().setDayTime(12900);
            look(p, sizeX / 2.0 - reach * 0.75, eye, sizeZ / 2.0 + reach * 0.75, 225f, 8f);
        }));
        SCRIPT.add(new Step("dusk", 10, p -> {}));
    }

    private static void stage(ServerPlayer p) {
        ServerLevel level = p.level();
        // Well clear of whatever the save already holds, on a field of its own: grass over dirt, open sky above, so
        // the place is judged at true scale in plain country rather than against another build.
        base = p.blockPosition().offset(160, -1, 0);
        Identifier id = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "places/" + PLACE + "/a");
        StructureTemplate template = level.getStructureManager().get(id).orElse(null);
        if (template == null) {
            LOGGER.error("Places look: template {} missing", id);
            return;
        }
        int margin = 40;
        var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        // A summit place (Stoatshead Hill) is a crown worldgen sets on a natural hilltop: give it a hill to stand on.
        int hill = "portkey_hill".equals(PLACE) ? 10 : 0;
        double cx = template.getSize().getX() / 2.0;
        double cz = template.getSize().getZ() / 2.0;
        for (int x = -margin; x < template.getSize().getX() + margin; x++) {
            for (int z = -margin; z < template.getSize().getZ() + margin; z++) {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz) / (margin * 0.85);
                int top = (int) Math.round(hill * Math.max(0, 1 - d * d));
                for (int y = -3; y <= 48 + hill; y++) {
                    at.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    level.setBlock(at, y < top ? dirt : y == top ? grass : air, 2);
                }
            }
        }
        base = base.above(hill);
        template.placeInWorld(level, base, base, new StructurePlaceSettings(), level.getRandom(), 2);
        level.setDayTime(1000);
        level.setWeatherParameters(6000, 0, false, false);
        p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        LOGGER.info("Places look: {} placed at {}, size {}", PLACE, base, template.getSize());
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
                System.getenv().getOrDefault("WB_PLACES_LOOK_WORLD", "wb_entity_showcase"), () -> stage = 3);
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
            readSize(server);
            script();
            begin(mc, server);
            return;
        }
        if (!SCRIPT.get(step).shot().isEmpty()) {
            captureThisFrame = true;
        } else {
            advance(mc, server);
        }
    }

    private static void readSize(MinecraftServer server) {
        Identifier id = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "places/" + PLACE + "/a");
        server.overworld().getStructureManager().get(id).ifPresent(t -> {
            sizeX = t.getSize().getX();
            sizeY = t.getSize().getY();
            sizeZ = t.getSize().getZ();
        });
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!captureThisFrame) {
            return;
        }
        captureThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "places_look", PLACE))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Places look: cannot write {}", file, e);
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
            LOGGER.info("Places look: done");
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
