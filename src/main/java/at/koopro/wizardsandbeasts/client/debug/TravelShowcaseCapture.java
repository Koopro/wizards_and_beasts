package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.ApparitionService;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionChargeManager;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFlamesBlock;
import at.koopro.wizardsandbeasts.floo.FlooDeparture;
import at.koopro.wizardsandbeasts.floo.FlooFlamePlacement;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs magical travel as it happens: an Apparition blink (licensed, charged, released through the same
 * charge manager the key uses) and a Floo trip between two registered, lit grates.
 *
 * <p>Development only, inert unless {@code WB_TRAVEL_SHOWCASE=1}. {@code WB_TRAVEL_SHOWCASE_WORLD} names a save
 * that must already exist (default {@code wb_entity_showcase}). Shots go to {@code screenshots/travel_showcase}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class TravelShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED =
            System.getenv("WB_TRAVEL_SHOWCASE") != null && !FMLEnvironment.isProduction();

    private static final int LOAD_TICKS = 80;
    private static final int STAGE_Y = 240;
    private static final BlockPos FLOO_FROM = new BlockPos(-4, STAGE_Y, -6);
    private static final BlockPos FLOO_TO = new BlockPos(8, STAGE_Y, -6);

    /** WIDE looks over the whole stage from the south; THIRD is the traveller's own view from behind. */
    private enum Cam { WIDE, CLOSE_FROM, CLOSE_TO, THIRD }

    private record Step(String shot, int ticks, Cam cam, Consumer<ServerPlayer> server) {}

    private static final Consumer<ServerPlayer> NOTHING = p -> {};
    private static final List<Step> SCRIPT = new ArrayList<>();

    private enum Stage { IDLE, LOADING, RUNNING, DONE }

    private static Stage stage = Stage.IDLE;
    private static boolean opened;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;
    private static ArmorStand camera;
    private static Cam lastCam;
    private static float lookYaw = -90.0F;
    private static float lookPitch = 18.0F;

    private TravelShowcaseCapture() {}

    private static void buildScript() {
        SCRIPT.clear();
        // Apparition: stand at the west end, look east and down at the stone ten blocks off, charge, let go.
        SCRIPT.add(new Step("", 20, Cam.WIDE, TravelShowcaseCapture::readyApparition));
        SCRIPT.add(new Step("", 1, Cam.WIDE, p -> LOGGER.info("Travel showcase: apparition start {}",
                ApparitionService.tryStart(p))));
        SCRIPT.add(new Step("apparate_charge_early", 6, Cam.WIDE, NOTHING));
        SCRIPT.add(new Step("apparate_charge_close", 8, Cam.THIRD, NOTHING));
        SCRIPT.add(new Step("apparate_charge_wide", 4, Cam.WIDE, NOTHING));
        SCRIPT.add(new Step("", 1, Cam.WIDE, p -> {
            ApparitionChargeManager.release(p);
            LOGGER.info("Travel showcase: released, now at {} {} {}", Math.round(p.getX()), Math.round(p.getY()),
                    Math.round(p.getZ()));
        }));
        SCRIPT.add(new Step("apparate_crack_1", 1, Cam.WIDE, NOTHING));
        SCRIPT.add(new Step("apparate_crack_4", 3, Cam.WIDE, NOTHING));
        SCRIPT.add(new Step("apparate_arrived", 10, Cam.THIRD, NOTHING));
        SCRIPT.add(new Step("apparate_after", 20, Cam.WIDE, NOTHING));
        // Floo: two lit grates, the traveller in the western one, bound for the eastern.
        SCRIPT.add(new Step("", 30, Cam.CLOSE_FROM, TravelShowcaseCapture::readyFloo));
        SCRIPT.add(new Step("floo_lit_from", 2, Cam.CLOSE_FROM, NOTHING));
        SCRIPT.add(new Step("", 1, Cam.CLOSE_FROM, p -> {
            FlooDeparture.begin(p, FLOO_FROM, "Showcase East", "Showcase East");
            LOGGER.info("Travel showcase: floo departing {}", FlooDeparture.isDeparting(p.getUUID()));
        }));
        SCRIPT.add(new Step("floo_windup_1", 8, Cam.CLOSE_FROM, NOTHING));
        SCRIPT.add(new Step("floo_windup_2", 8, Cam.CLOSE_FROM, NOTHING));
        SCRIPT.add(new Step("floo_windup_wide", 10, Cam.WIDE, NOTHING));
        SCRIPT.add(new Step("floo_after_wide", 20, Cam.WIDE, NOTHING));
        SCRIPT.add(new Step("floo_arrived_to", 4, Cam.CLOSE_TO, p -> LOGGER.info(
                "Travel showcase: after floo at {} {} {}", Math.round(p.getX()), Math.round(p.getY()),
                Math.round(p.getZ()))));
        SCRIPT.add(new Step("floo_arrived_third", 4, Cam.THIRD, NOTHING));
        // Portkey: an old boot set for the east end of the stage, used where the Apparition began.
        SCRIPT.add(new Step("", 20, Cam.WIDE, p -> p.teleportTo(p.level(), -10.5, STAGE_Y, 4.5, Set.of(), -90.0F, 0.0F, true)));
        SCRIPT.add(new Step("portkey_before", 2, Cam.WIDE, NOTHING));
        SCRIPT.add(new Step("", 1, Cam.WIDE, p -> {
            var boot = new net.minecraft.world.item.ItemStack(
                    at.koopro.wizardsandbeasts.registry.TrinketItemRegistry.PORTKEY.get());
            at.koopro.wizardsandbeasts.portkey.PortkeyService.set(boot, p.level(), new BlockPos(10, STAGE_Y - 1, 4));
            LOGGER.info("Travel showcase: portkey {}", at.koopro.wizardsandbeasts.portkey.PortkeyService.travel(p, boot));
        }));
        SCRIPT.add(new Step("portkey_1", 2, Cam.WIDE, NOTHING));
        SCRIPT.add(new Step("portkey_2", 4, Cam.WIDE, NOTHING));
        SCRIPT.add(new Step("portkey_third", 4, Cam.THIRD, NOTHING));
    }

    // ------------------------------------------------------------------ server side

    private static void readyApparition(ServerPlayer p) {
        PlayerAbilityHelper.setApparitionUnlocked(p, true);
        PlayerAbilityHelper.setApparitionLicensed(p, true);
        PlayerAbilityHelper.setApparitionCooldownTicks(p, 0);
        p.teleportTo(p.level(), -10.5, STAGE_Y, 4.5, Set.of(), -90.0F, 18.0F, true);
    }

    private static void readyFloo(ServerPlayer p) {
        ServerLevel level = p.level();
        FlooNetworkManager network = FlooNetworkManager.get(level);
        for (var entry : List.of(new Object[]{FLOO_FROM, "Showcase West"}, new Object[]{FLOO_TO, "Showcase East"})) {
            BlockPos pos = (BlockPos) entry[0];
            BlockState grate = ModBlocks.FLOO_FIREPLACE.get().defaultBlockState()
                    .setValue(FlooFireplaceBlock.FACING, Direction.SOUTH)
                    .setValue(FlooFireplaceBlock.LIT, true);
            level.setBlock(pos, grate, Block.UPDATE_ALL);
            FlooFlamesBlock.light(level, pos, Direction.SOUTH);
            var result = network.register((String) entry[1], level.dimension().identifier(), pos, true,
                    Optional.of(p.getUUID()));
            LOGGER.info("Travel showcase: registered {} at {}: {}", entry[1], pos, result);
        }
        // Floo powder is what a hop spends; without it the hop is refused at the last moment.
        p.getInventory().add(new net.minecraft.world.item.ItemStack(
                at.koopro.wizardsandbeasts.registry.MiscItemRegistry.FLOO_POWDER.get(), 16));
        BlockPos stand = FlooFlamePlacement.flamePos(FLOO_FROM, Direction.SOUTH);
        p.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
        Minecraft.getInstance().execute(() -> {
            lookYaw = 0.0F;
            lookPitch = 0.0F;
        });
    }

    private static void buildStage(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        level.setDayTime(6000);
        player.setGameMode(GameType.SURVIVAL);
        for (int x = -16; x <= 16; x++) {
            for (int z = -12; z <= 12; z++) {
                level.setBlock(new BlockPos(x, STAGE_Y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y <= 8; y++) {
                    level.setBlock(new BlockPos(x, STAGE_Y + y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // A brick back wall behind the grates, so the green fire has something to light.
        for (int x = -8; x <= 12; x++) {
            for (int y = 0; y <= 4; y++) {
                level.setBlock(new BlockPos(x, STAGE_Y + y, -7), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        player.teleportTo(level, -10.5, STAGE_Y, 4.5, Set.of(), -90.0F, 18.0F, true);
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
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_TRAVEL_SHOWCASE_WORLD", "wb_entity_showcase"), () -> stage = Stage.DONE);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!ENABLED || stage != Stage.RUNNING || player == null) {
            return;
        }
        // The blink aims where the traveller looks: hold the look on the stone ahead for the whole charge.
        player.setYRot(lookYaw);
        player.setYHeadRot(lookYaw);
        player.setXRot(lookPitch);
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
            stage = Stage.RUNNING;
            step = 0;
            buildScript();
            begin(mc, server);
            return;
        }
        if (!SCRIPT.get(step).shot().isEmpty()) {
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "travel_showcase"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Travel showcase: cannot write {}", file, e);
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
            stage = Stage.DONE;
            LOGGER.info("Travel showcase: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        var uuid = mc.player != null ? mc.player.getUUID() : null;
        server.execute(() -> {
            ServerPlayer player = uuid != null ? server.getPlayerList().getPlayer(uuid) : null;
            if (player != null) {
                current.server().accept(player);
            }
        });
    }

    private static void follow(LocalPlayer player, Cam cam) {
        Minecraft mc = Minecraft.getInstance();
        if (cam == Cam.THIRD) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
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
        double y = STAGE_Y;
        double ex;
        double ey;
        double ez;
        double lx;
        double ly;
        double lz;
        switch (cam) {
            case CLOSE_FROM -> {
                ex = FLOO_FROM.getX() + 2.5; ey = y + 2.0; ez = FLOO_FROM.getZ() + 6.0;
                lx = FLOO_FROM.getX() + 0.5; ly = y + 1.0; lz = FLOO_FROM.getZ() + 1.0;
            }
            case CLOSE_TO -> {
                ex = FLOO_TO.getX() - 1.5; ey = y + 2.0; ez = FLOO_TO.getZ() + 6.0;
                lx = FLOO_TO.getX() + 0.5; ly = y + 1.0; lz = FLOO_TO.getZ() + 1.0;
            }
            default -> {
                ex = 0.0; ey = y + 4.0; ez = 14.0;
                lx = 0.0; ly = y + 1.0; lz = -1.0;
            }
        }
        double dx = lx - ex;
        double dy = ly - ey;
        double dz = lz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        camera.setPos(ex, ey - camera.getEyeHeight(), ez);
        camera.setYRot(yaw);
        camera.setXRot(Mth.clamp(pitch, -89.0F, 89.0F));
        camera.setYHeadRot(yaw);
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = yaw;
        camera.xRotO = camera.getXRot();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != camera || cam != lastCam) {
            lastCam = cam;
            mc.setCameraEntity(camera);
        }
    }
}
