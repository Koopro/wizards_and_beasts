package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.AnimagusTransformService;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.animagus.AnimagusFormBinding;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.form.SizeProfileRegistry;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
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
 * Photographs an Animagus form through the real transformation, the way {@link EntityShowcaseCapture}
 * photographs a mob: transform, stand, walk, sprint, jump, take a hit, turn back, and die.
 *
 * <p>A form cannot be summoned onto a stage like a creature. It is the player, and what it draws
 * depends on the synced form state, the movement vanilla computes from real input, and the damage
 * and death the server applies. So this drives the local player: the transformation goes through
 * {@code AnimagusTransformService}, movement through the key bindings, damage through the server.
 * A client-only camera follows alongside, since a third-person camera can only look along the
 * player's own facing.
 *
 * <p>Development only, and inert unless asked for. {@code WB_FORM_SHOWCASE} lists Animagus form ids,
 * comma-separated ({@code animagus_stag,animagus_hawk}); {@code WB_FORM_SHOWCASE_WORLD} names a
 * disposable save that must already exist (default: the entity showcase's {@code wb_entity_showcase})
 * and {@code WB_FORM_SHOWCASE_OUT} the folder under {@code screenshots/}. Shots are
 * {@code <form>__<step>.png}. The client quits after the last one.
 *
 * <p>The {@code death_side} shot shows the human: the transformation ends at the moment of death
 * ({@code AnimagusEvents.onDeath}), so no form is ever drawn dying.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class FormShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int LOAD_TICKS = 80;
    private static final int STAGE_Y = 200;
    /** Walks run east along the stage, from here. */
    private static final double START_X = -24.5;

    private enum Stage { IDLE, OPENING, LOADING, RUNNING, DONE }

    /** Where the follow camera sits relative to the player's facing east (+X). */
    private enum Cam { SIDE, FRONT, QUARTER }

    /**
     * One beat of the script: what to do, how long to let it play, and whether to shoot at the end.
     *
     * @param client run on the client thread when the step starts
     * @param server run on the server thread when the step starts
     */
    private record Step(String name, int ticks, boolean shoot, Cam cam,
                        Consumer<LocalPlayer> client, Consumer<ServerPlayer> server) {}

    private static final List<String> FORMS = forms();
    private static final List<Step> SCRIPT = new ArrayList<>();
    private static Stage stage = Stage.IDLE;
    private static int wait;
    private static int step;
    private static int form;
    private static boolean opened;
    private static boolean captureThisFrame;
    private static ArmorStand camera;

    private FormShowcaseCapture() {}

    private static List<String> forms() {
        String list = System.getenv("WB_FORM_SHOWCASE");
        if (list == null || FMLEnvironment.isProduction()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : list.split(",")) {
            if (!id.isBlank()) {
                out.add(id.strip());
            }
        }
        return out;
    }

    private static Path out() {
        return Path.of(Screenshot.SCREENSHOT_DIR,
                System.getenv().getOrDefault("WB_FORM_SHOWCASE_OUT", "form_showcase"));
    }

    private static boolean forward;
    private static boolean sprint;
    private static boolean jump;

    /**
     * What the player is doing, applied every tick in {@link #onClientTickPre}.
     *
     * <p>Key bindings alone do nothing here: {@code LocalPlayer} only reads its input while it is the
     * camera entity, and the follow camera is not. So the impulses are set on the player directly,
     * the same fields its own input would have written.
     */
    private static void keys(boolean forward, boolean sprint, boolean jump) {
        FormShowcaseCapture.forward = forward;
        FormShowcaseCapture.sprint = sprint;
        FormShowcaseCapture.jump = jump;
        // Held as well, so vanilla's own "still pressing forward?" sprint check agrees.
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(forward);
        mc.options.keySprint.setDown(sprint);
        mc.options.keyJump.setDown(jump);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (FORMS.isEmpty() || stage != Stage.RUNNING || player == null || !player.isAlive()) {
            return;
        }
        // Facing east throughout, head and body, so the side camera sees a flank.
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F);
        player.setYBodyRot(-90.0F);
        player.setXRot(0.0F);
        player.zza = forward ? 1.0F : 0.0F;
        player.xxa = 0.0F;
        player.setSprinting(forward && sprint);
        player.setJumping(jump);
    }

    private static final Consumer<LocalPlayer> NONE = p -> {};
    private static final Consumer<ServerPlayer> NOTHING = p -> {};

    /** The script, the same for every form. */
    private static void buildScript(String formId) {
        SCRIPT.clear();
        // Human first, reset to the start line: the before of the transformation.
        SCRIPT.add(new Step("human", 30, true, Cam.QUARTER, p -> keys(false, false, false), p -> {
            reset(p);
            if (PlayerAbilityHelper.isCurrentlyTransformed(p)) {
                AnimagusTransformService.revert(p);
            }
            Identifier key = AnimagusFormBinding.toFormKey(formId).orElseThrow();
            PlayerAbilityHelper.setAnimagusUnlocked(p, true);
            PlayerAbilityHelper.setAnimagusFormId(p, AnimagusFormBinding.toStoredId(key));
        }));
        SCRIPT.add(new Step("transform", 50, false, Cam.QUARTER, NONE, AnimagusTransformService::toggleTransform));
        SCRIPT.add(new Step("idle_quarter", 30, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("idle_side", 4, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("idle_front", 4, true, Cam.FRONT, NONE, NOTHING));
        SCRIPT.add(new Step("walk_side", 22, true, Cam.SIDE, p -> keys(true, false, false), NOTHING));
        SCRIPT.add(new Step("walk_quarter", 7, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("sprint_side", 16, true, Cam.SIDE, p -> keys(true, true, false), NOTHING));
        SCRIPT.add(new Step("sprint_quarter", 5, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("jump_side", 5, true, Cam.SIDE, p -> keys(true, true, true), NOTHING));
        SCRIPT.add(new Step("land", 20, false, Cam.SIDE, p -> keys(false, false, false), NOTHING));
        SCRIPT.add(new Step("hurt_side", 3, true, Cam.SIDE, NONE,
                p -> p.hurtServer(p.level(), p.damageSources().generic(), 1.0F)));
        SCRIPT.add(new Step("recover", 20, false, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("transform_back", 50, true, Cam.QUARTER, NONE, AnimagusTransformService::toggleTransform));
        SCRIPT.add(new Step("retransform", 50, true, Cam.QUARTER, NONE, AnimagusTransformService::toggleTransform));
        SCRIPT.add(new Step("death_side", 8, true, Cam.SIDE, NONE,
                p -> p.hurtServer(p.level(), p.damageSources().generic(), 1000.0F)));
        SCRIPT.add(new Step("respawn", 30, false, Cam.QUARTER, p -> {
            Minecraft.getInstance().setScreen(null);
            p.respawn();
        }, NOTHING));
        SCRIPT.add(new Step("after_respawn", 20, true, Cam.QUARTER, NONE, NOTHING));
    }

    private static void reset(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.teleportTo(player.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (FORMS.isEmpty() || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.bobView().set(false);
        mc.options.hideGui = true;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1600, 900);
        stage = Stage.OPENING;
        String world = System.getenv().getOrDefault("WB_FORM_SHOWCASE_WORLD", "wb_entity_showcase");
        LOGGER.info("Form showcase: {} into {}", FORMS, out());
        mc.createWorldOpenFlows().openWorld(world, () -> stage = Stage.DONE);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (FORMS.isEmpty() || stage == Stage.IDLE || stage == Stage.DONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            return;
        }
        if (stage == Stage.OPENING) {
            stage = Stage.LOADING;
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
            form = 0;
            step = 0;
            buildScript(FORMS.getFirst());
            begin(mc, server);
            return;
        }
        if (SCRIPT.get(step).shoot()) {
            mc.gui.getChat().clearMessages(false);
            mc.getToastManager().clear();
            if (mc.screen != null) {
                mc.setScreen(null);
            }
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
        String name = FORMS.get(form) + "__" + SCRIPT.get(step).name() + ".png";
        Path file = mc.gameDirectory.toPath().resolve(out()).resolve(name);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Form showcase: cannot write {}", file, e);
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
            step = 0;
            form++;
            if (form >= FORMS.size()) {
                stage = Stage.DONE;
                keys(false, false, false);
                LOGGER.info("Form showcase: done");
                mc.stop();
                return;
            }
            buildScript(FORMS.get(form));
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

    /**
     * Keeps the client-only camera beside the player. Both are interpolated by the same partial tick,
     * so the old position is carried into {@code xo} before the new one is set.
     */
    private static void follow(LocalPlayer player, Cam cam) {
        Minecraft mc = Minecraft.getInstance();
        if (camera == null || camera.level() != mc.level) {
            camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            camera.setInvisible(true);
        }
        SizeProfile size = SizeProfileRegistry.get(FORMS.get(form));
        float height = size != null ? size.hitboxHeight() : 1.8F;
        double distance = height * 2.2 + 1.6;
        double angle = switch (cam) {
            case SIDE -> 0.0;       // south of an east-facing player: its left flank
            case FRONT -> 90.0;     // east, ahead of it
            case QUARTER -> 40.0;
        };
        double rad = Math.toRadians(angle);
        double camX = player.getX() + Math.sin(rad) * distance;
        double camZ = player.getZ() + Math.cos(rad) * distance;
        double lookY = player.getY() + height * 0.5;
        double camY = lookY + height * 0.25 + 0.3;
        double dx = player.getX() - camX;
        double dz = player.getZ() - camZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(lookY - camY, Math.sqrt(dx * dx + dz * dz)));
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
        camera.setPos(camX, camY - camera.getEyeHeight(), camZ);
        camera.setYRot(yaw);
        camera.setXRot(Mth.clamp(pitch, -89.0F, 89.0F));
        camera.setYHeadRot(yaw);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
            camera.xo = camX;
            camera.yo = camY - camera.getEyeHeight();
            camera.zo = camZ;
        }
    }

    /** A long stone runway in clear sky, day, survival, full health. */
    private static void buildStage(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        level.setDayTime(6000);
        player.setGameMode(GameType.SURVIVAL);
        for (int x = -30; x <= 30; x++) {
            for (int z = -6; z <= 6; z++) {
                level.setBlockAndUpdate(new BlockPos(x, STAGE_Y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 0; y < 6; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, STAGE_Y + y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        reset(player);
    }
}
