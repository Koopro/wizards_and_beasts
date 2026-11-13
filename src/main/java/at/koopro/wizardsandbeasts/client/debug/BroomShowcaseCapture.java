package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Flies brooms for real and photographs them: mounted, taking off, cruising, banking through a half turn, boosting
 * and landing, from a chase camera, from the side, through the rider's own third-person view (camera roll and
 * pull-back included) and in first person (speed FOV).
 *
 * <p>Every manoeuvre goes through the rider's keys (forward, jump, sprint) and look, so the client input handler,
 * the server's movement, the rider pose, the camera handler and the speed effects all run as they do for a player.
 * The stage is a large stone floor in open sky, so the grid gives speed a reference.
 *
 * <p>Development only, inert unless {@code WB_BROOM_SHOWCASE} is set: {@code 1} for the default line-up, or a comma
 * list of broom definition ids. {@code WB_BROOM_SHOWCASE_WORLD} names a save that must already exist (default
 * {@code wb_entity_showcase}). Shots go to {@code screenshots/broom_showcase/<broom>__<shot>.png}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class BroomShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = System.getenv("WB_BROOM_SHOWCASE");
    private static final boolean ENABLED = MODE != null && !FMLEnvironment.isProduction();

    private static final int LOAD_TICKS = 80;
    private static final int STAGE_Y = 150;
    private static final int HALF = 64;
    private static final double START_X = -30.5;
    /** One of each speed class: the starter, a mid broom, a racer and the top of the line. */
    private static final List<String> LINE_UP = List.of("broom", "cleansweep_seven", "nimbus_2000", "firebolt");

    /** CHASE behind and right of the broom, SIDE level and well off, THIRD and FIRST the rider's own views. */
    private enum Cam { CHASE, SIDE, THIRD, FIRST }

    private record Step(String shot, int ticks, Cam cam, Consumer<LocalPlayer> client, Consumer<ServerPlayer> server) {
        boolean shoots() {
            return !shot.isEmpty();
        }
    }

    private static final Consumer<LocalPlayer> NONE = p -> {};
    private static final Consumer<ServerPlayer> NOTHING = p -> {};

    private enum Stage { IDLE, LOADING, RUNNING, DONE }

    private static Stage stage = Stage.IDLE;
    private static boolean opened;
    private static int wait;
    private static int step;
    private static int broomIndex;
    private static boolean captureThisFrame;
    private static List<String> brooms = List.of();
    private static final List<Step> SCRIPT = new ArrayList<>();
    private static ArmorStand camera;
    private static Cam lastCam;

    /** The rider's look, driven by the script; turn is added to the yaw every tick while banking. */
    private static float yaw = -90.0F;
    private static float pitch;
    private static float turn;
    private static boolean forward;
    private static boolean up;
    private static boolean boost;

    private BroomShowcaseCapture() {}

    private static List<String> chooseBrooms() {
        if ("1".equals(MODE) || "all".equalsIgnoreCase(MODE)) {
            return LINE_UP;
        }
        List<String> ids = new ArrayList<>();
        for (String raw : MODE.split(",")) {
            if (!raw.isBlank()) {
                ids.add(raw.trim());
            }
        }
        return ids;
    }

    private static Consumer<LocalPlayer> keys(boolean f, boolean u, boolean b, float p, float t) {
        return player -> {
            forward = f;
            up = u;
            boost = b;
            pitch = p;
            turn = t;
        };
    }

    private static void buildScript(String broomId) {
        SCRIPT.clear();
        SCRIPT.add(new Step("", 30, Cam.CHASE, keys(false, false, false, 0F, 0F), p -> mount(p, broomId)));
        SCRIPT.add(new Step("mounted", 2, Cam.CHASE, NONE, NOTHING));
        // Up off the pad.
        SCRIPT.add(new Step("", 8, Cam.SIDE, keys(false, true, false, 0F, 0F), NOTHING));
        SCRIPT.add(new Step("takeoff_side", 6, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("takeoff_chase", 4, Cam.CHASE, NONE, NOTHING));
        // Away, level.
        SCRIPT.add(new Step("", 22, Cam.CHASE, keys(true, false, false, 0F, 0F), NOTHING));
        SCRIPT.add(new Step("cruise_chase", 1, Cam.CHASE, NONE, p -> report(p, "cruise")));
        SCRIPT.add(new Step("cruise_side", 1, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("cruise_third", 1, Cam.THIRD, NONE, NOTHING));
        SCRIPT.add(new Step("cruise_first", 1, Cam.FIRST, NONE, NOTHING));
        // A half turn to the left, five degrees a tick.
        SCRIPT.add(new Step("", 14, Cam.CHASE, keys(true, false, false, 0F, -5F), NOTHING));
        SCRIPT.add(new Step("bank_chase", 1, Cam.CHASE, NONE, NOTHING));
        SCRIPT.add(new Step("bank_third", 1, Cam.THIRD, NONE, NOTHING));
        SCRIPT.add(new Step("", 20, Cam.CHASE, NONE, NOTHING));
        // Straight back, boosting.
        SCRIPT.add(new Step("", 16, Cam.CHASE, keys(true, false, true, 0F, 0F), NOTHING));
        SCRIPT.add(new Step("boost_chase", 1, Cam.CHASE, NONE, p -> report(p, "boost")));
        SCRIPT.add(new Step("boost_side", 1, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("boost_third", 1, Cam.THIRD, NONE, NOTHING));
        SCRIPT.add(new Step("boost_first", 1, Cam.FIRST, NONE, NOTHING));
        // Ease off and nose down onto the floor.
        SCRIPT.add(new Step("", 10, Cam.SIDE, keys(true, false, false, 30F, 0F), NOTHING));
        SCRIPT.add(new Step("descent_side", 1, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("", 40, Cam.SIDE, keys(false, false, false, 30F, 0F), NOTHING));
        SCRIPT.add(new Step("landed_side", 1, Cam.SIDE, NONE, p -> report(p, "landed")));
        SCRIPT.add(new Step("landed_chase", 1, Cam.CHASE, keys(false, false, false, 0F, 0F), NOTHING));
        SCRIPT.add(new Step("", 4, Cam.CHASE, NONE, BroomShowcaseCapture::unmount));
    }

    // ------------------------------------------------------------------ server side

    private static void mount(ServerPlayer p, String broomId) {
        ServerLevel level = p.level();
        p.stopRiding();
        for (Entity e : level.getEntitiesOfClass(BroomEntity.class, p.getBoundingBox().inflate(200))) {
            e.discard();
        }
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.teleportTo(level, START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
        BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), level);
        broom.setPos(START_X, STAGE_Y + 0.2, 0.5);
        broom.setYRot(-90.0F);
        broom.setDefinitionId(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, broomId));
        level.addFreshEntity(broom);
        p.startRiding(broom, true, true);
        Minecraft.getInstance().execute(() -> {
            yaw = -90.0F;
            pitch = 0F;
        });
        LOGGER.info("Broom showcase: {} mounted {}", broomId, p.getVehicle() == broom);
    }

    private static void unmount(ServerPlayer p) {
        Entity vehicle = p.getVehicle();
        p.stopRiding();
        if (vehicle != null) {
            vehicle.discard();
        }
    }

    private static void report(ServerPlayer p, String moment) {
        Entity vehicle = p.getVehicle();
        if (vehicle == null) {
            LOGGER.info("Broom showcase: {}: not riding", moment);
            return;
        }
        Vec3 v = vehicle.getDeltaMovement();
        LOGGER.info("Broom showcase: {}: at {} {} {}, speed {} b/t, on ground {}", moment,
                Math.round(vehicle.getX()), Math.round(vehicle.getY()), Math.round(vehicle.getZ()),
                String.format("%.2f", v.length()), vehicle.onGround());
    }

    private static void buildStage(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        level.setDayTime(6000);
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                // A grid of darker lines every eight blocks, so speed and distance read in the shots.
                boolean line = Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0;
                level.setBlock(new BlockPos(x, STAGE_Y - 1, z),
                        (line ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE).defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        player.teleportTo(level, START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
        at.koopro.wizardsandbeasts.gametest.WizardTestSupport.makeWandkind(player);
    }

    // ------------------------------------------------------------------ client side

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.bobView().set(false);
        mc.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = Stage.LOADING;
        wait = -1;
        String world = System.getenv().getOrDefault("WB_BROOM_SHOWCASE_WORLD", "wb_entity_showcase");
        mc.createWorldOpenFlows().openWorld(world, () -> stage = Stage.DONE);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!ENABLED || stage != Stage.RUNNING || player == null) {
            return;
        }
        yaw += turn;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
        mc.options.keyUp.setDown(forward);
        mc.options.keyJump.setDown(up);
        mc.options.keySprint.setDown(boost);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || stage == Stage.IDLE || stage == Stage.DONE) {
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
        if (stage == Stage.LOADING && wait < 0) {
            wait = LOAD_TICKS;
            server.execute(() -> buildStage(server));
            return;
        }
        if (stage == Stage.RUNNING) {
            follow(mc.player, SCRIPT.get(step).cam());
        }
        if (captureThisFrame || --wait > 0) {
            return;
        }
        if (stage == Stage.LOADING) {
            brooms = chooseBrooms();
            LOGGER.info("Broom showcase: {} brooms into {}", brooms.size(), out());
            stage = Stage.RUNNING;
            broomIndex = 0;
            step = 0;
            buildScript(brooms.getFirst());
            begin(mc, server);
            return;
        }
        if (SCRIPT.get(step).shoots()) {
            mc.gui.getChat().clearMessages(false);
            mc.getToastManager().clear();
            mc.options.hideGui = true;
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
        String name = brooms.get(broomIndex) + "__" + SCRIPT.get(step).shot() + ".png";
        Path file = mc.gameDirectory.toPath().resolve(out()).resolve(name);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Broom showcase: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            advance(mc, server);
        }
    }

    private static Path out() {
        return Path.of(Screenshot.SCREENSHOT_DIR, "broom_showcase");
    }

    private static void advance(Minecraft mc, MinecraftServer server) {
        step++;
        if (step >= SCRIPT.size()) {
            step = 0;
            broomIndex++;
            if (broomIndex >= brooms.size()) {
                stage = Stage.DONE;
                forward = up = boost = false;
                LOGGER.info("Broom showcase: done");
                mc.stop();
                return;
            }
            buildScript(brooms.get(broomIndex));
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        if (mc.player != null) {
            current.client().accept(mc.player);
        }
        var uuid = mc.player != null ? mc.player.getUUID() : null;
        server.execute(() -> {
            ServerPlayer player = uuid != null ? server.getPlayerList().getPlayer(uuid) : null;
            if (player != null) {
                current.server().accept(player);
            }
        });
    }

    /** Puts the view where the step wants it; the chase and side cameras ride along with the broom. */
    private static void follow(LocalPlayer player, Cam cam) {
        Minecraft mc = Minecraft.getInstance();
        if (cam == Cam.THIRD || cam == Cam.FIRST) {
            mc.options.setCameraType(cam == Cam.THIRD ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
            if (mc.getCameraEntity() != player) {
                mc.setCameraEntity(player);
            }
            lastCam = cam;
            return;
        }
        if (camera == null || camera.level() != mc.level) {
            camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            camera.setInvisible(true);
        }
        Entity subject = player.getVehicle() != null ? player.getVehicle() : player;
        Vec3 at = subject.position().add(0, 1.0, 0);
        double rad = Math.toRadians(yaw);
        // Facing vector of the rider (Minecraft yaw: -sin, cos), and the rider's right hand.
        Vec3 ahead = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        Vec3 right = new Vec3(-ahead.z, 0, ahead.x);
        Vec3 eye = cam == Cam.SIDE
                ? at.add(right.scale(-9.0)).add(0, 0.8, 0)
                : at.add(ahead.scale(-4.5)).add(right.scale(-2.0)).add(0, 1.6, 0);
        double dx = at.x - eye.x;
        double dy = at.y - eye.y;
        double dz = at.z - eye.z;
        float camYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float camPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
        camera.setPos(eye.x, eye.y - camera.getEyeHeight(), eye.z);
        camera.setYRot(camYaw);
        camera.setXRot(Mth.clamp(camPitch, -89.0F, 89.0F));
        camera.setYHeadRot(camYaw);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != camera || cam != lastCam) {
            lastCam = cam;
            mc.setCameraEntity(camera);
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            camera.yRotO = camYaw;
            camera.xRotO = camera.getXRot();
        }
    }
}
