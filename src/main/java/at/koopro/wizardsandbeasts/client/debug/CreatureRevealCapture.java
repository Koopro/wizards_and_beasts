package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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

/**
 * Photographs creatures that hide themselves: each is stood on a stone floor beside a villager for scale, its AI
 * stopped and its invisibility (camouflage, a Boggart's dread) stripped every tick, then shot front, three-quarter,
 * side and back by day and once at night.
 *
 * <p>Development only, inert unless {@code WB_REVEAL_CAPTURE} names a comma list of creature ids.
 * {@code WB_REVEAL_CAPTURE_WORLD} names a save that must already exist (default {@code wb_entity_showcase}).
 * Shots go to {@code screenshots/reveal/<id>__<view>.png}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class CreatureRevealCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = System.getenv("WB_REVEAL_CAPTURE");
    private static final boolean ENABLED = MODE != null && !FMLEnvironment.isProduction();
    private static final int STAGE_Y = 240;

    /** What the server does to the creature before the view is shot: nothing, or one of its one-shot moments. */
    private enum Action { NONE, ATTACK, HIT, DEATH }

    private record View(String name, float camYaw, int dayTime, Action action, int settle) {}

    /**
     * Camera bearings round the creature (degrees, 0 = from the south), the time of day, and the one-shot clips —
     * attack, hit, death — caught a few ticks after the game event that plays them (the death last).
     */
    private static final List<View> VIEWS = List.of(
            new View("front", 0, 6000, Action.NONE, 12), new View("quarter", 40, 6000, Action.NONE, 12),
            new View("side", 90, 6000, Action.NONE, 12), new View("back", 180, 6000, Action.NONE, 12),
            new View("attack", 40, 6000, Action.ATTACK, 6), new View("hit", 40, 6000, Action.HIT, 4),
            // Night after the day's moments: the client's sky lags a set time by a few ticks, and a 4-tick hit shot
            // taken just after night came out dark.
            new View("night", 30, 18000, Action.NONE, 30), new View("death", 40, 6000, Action.DEATH, 14));

    private static final List<String> IDS = new ArrayList<>();
    private static int idIndex;
    private static int viewIndex = -1;
    private static int wait;
    private static int stage; // 0 idle, 1 loading, 2 running, 3 done
    private static boolean opened;
    private static boolean capture;
    private static ArmorStand camera;
    private static double subjectHeight = 1.8;

    private CreatureRevealCapture() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        for (String raw : MODE.split(",")) {
            if (!raw.isBlank()) {
                IDS.add(raw.trim());
            }
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = 1;
        wait = 80;
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_REVEAL_CAPTURE_WORLD", "wb_entity_showcase"), () -> stage = 3);
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
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        server.execute(() -> keepVisible(server));
        if (stage == 2 && viewIndex >= 0) {
            follow(mc, VIEWS.get(viewIndex));
        }
        if (capture || --wait > 0) {
            return;
        }
        if (stage == 1) {
            stage = 2;
            server.execute(() -> buildStage(server));
            idIndex = 0;
            viewIndex = -1;
            next(mc, server);
            return;
        }
        capture = true;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!capture) {
            return;
        }
        capture = false;
        Minecraft mc = Minecraft.getInstance();
        String name = IDS.get(idIndex).replace('@', '_').replace(':', '_') + "__" + VIEWS.get(viewIndex).name() + ".png";
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "reveal")).resolve(name);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Reveal capture: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            next(mc, server);
        }
    }

    /** The next view, or the next creature, or the end. */
    private static void next(Minecraft mc, MinecraftServer server) {
        viewIndex++;
        if (viewIndex >= VIEWS.size()) {
            viewIndex = 0;
            idIndex++;
        }
        if (idIndex >= IDS.size()) {
            stage = 3;
            LOGGER.info("Reveal capture: done");
            mc.stop();
            return;
        }
        String id = IDS.get(idIndex);
        View view = VIEWS.get(viewIndex);
        boolean firstView = viewIndex == 0;
        wait = firstView ? 40 : view.settle();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = p.level();
            level.setDayTime(view.dayTime());
            if (firstView) {
                spawn(level, id);
            }
            act(level, view.action());
        });
    }

    private static void buildStage(MinecraftServer server) {
        ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = p.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        p.setGameMode(GameType.SPECTATOR);
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                level.setBlock(new BlockPos(x, STAGE_Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        // Facing away from the stage: the Hidebehind is invisible to a player looking at it, and the camera here
        // is a stand, not the player's eyes.
        p.teleportTo(level, 0.5, STAGE_Y + 4, 10.5, Set.of(), 0.0F, 0.0F, true);
    }

    private static Entity subject;
    private static Entity scaleFigure;

    /** Plays one of the creature's one-shot moments through the game event that triggers it. */
    private static void act(ServerLevel level, Action action) {
        if (subject == null || !subject.isAlive()) {
            return;
        }
        switch (action) {
            case ATTACK -> {
                if (subject instanceof Mob mob && scaleFigure != null) {
                    mob.doHurtTarget(level, scaleFigure);
                }
            }
            case HIT -> subject.hurtServer(level, level.damageSources().generic(), 1.0F);
            case DEATH -> subject.kill(level);
            default -> { }
        }
    }

    /**
     * Clears the stage and stands the creature in the middle, with a villager beside it for scale. An id may carry an
     * age, {@code basilisk@0}, for a creature that grows.
     */
    private static void spawn(ServerLevel level, String spec) {
        if (spec.startsWith("scene:")) {
            meadow(level);
            return;
        }
        String id = spec.contains("@") ? spec.substring(0, spec.indexOf('@')) : spec;
        long age = spec.contains("@") ? Long.parseLong(spec.substring(spec.indexOf('@') + 1)) : -1;
        AABB box = new AABB(-14, STAGE_Y - 2, -14, 14, STAGE_Y + 20, 14);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            e.discard();
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id));
        Entity creature = type.create(level, EntitySpawnReason.COMMAND);
        if (creature == null) {
            LOGGER.warn("Reveal capture: cannot create {}", id);
            return;
        }
        creature.snapTo(0.5, STAGE_Y, 0.5, 0.0F, 0.0F);
        if (creature instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setYHeadRot(0.0F);
            mob.yBodyRot = 0.0F;
        }
        level.addFreshEntity(creature);
        if (age >= 0 && creature instanceof at.koopro.wizardsandbeasts.entity.creature.BasiliskEntity basilisk) {
            basilisk.setAgeTicks(age);
        }
        subject = creature;
        subjectHeight = Math.max(1.0, Math.max(creature.getBbHeight(), creature.getBbWidth() * 0.6));
        Entity villager = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager != null) {
            villager.snapTo(-0.5 - Math.max(1.5, creature.getBbWidth()), STAGE_Y, 0.5, 0.0F, 0.0F);
            if (villager instanceof Mob mob) {
                mob.setNoAi(true);
            }
            level.addFreshEntity(villager);
            scaleFigure = villager;
        }
        LOGGER.info("Reveal capture: {} spawned, {}x{}", spec, creature.getBbWidth(), creature.getBbHeight());
    }

    /** {@code scene:meadow}: the stage planted with grass and flowers, for the world's own night magic. */
    private static void meadow(ServerLevel level) {
        AABB box = new AABB(-14, STAGE_Y - 2, -14, 14, STAGE_Y + 20, 14);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            e.discard();
        }
        net.minecraft.util.RandomSource random = level.random;
        net.minecraft.world.level.block.Block[] flora = {Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.FERN,
                Blocks.POPPY, Blocks.DANDELION, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.AZURE_BLUET};
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                if (random.nextFloat() < 0.45f) {
                    level.setBlock(new BlockPos(x, STAGE_Y, z), flora[random.nextInt(flora.length)].defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
        subject = null;
        subjectHeight = 2.0;
        LOGGER.info("Reveal capture: meadow planted");
    }

    /** Strips invisibility from every creature on the stage, every tick, so camouflage cannot hide it. */
    private static void keepVisible(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(-14, STAGE_Y - 2, -14, 14, STAGE_Y + 20, 14), e -> !(e instanceof Player))) {
            if (e.hasEffect(MobEffects.INVISIBILITY)) {
                e.removeEffect(MobEffects.INVISIBILITY);
            }
            e.setInvisible(false);
        }
    }

    private static void follow(Minecraft mc, View view) {
        if (camera == null || camera.level() != mc.level) {
            camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            camera.setInvisible(true);
        }
        double distance = 3.0 + subjectHeight * 1.6;
        double rad = Math.toRadians(view.camYaw());
        double lookY = STAGE_Y + subjectHeight * 0.55;
        double ex = 0.5 + Math.sin(rad) * distance;
        double ez = 0.5 + Math.cos(rad) * distance;
        double ey = lookY + subjectHeight * 0.35 + 0.4;
        double dx = 0.5 - ex;
        double dz = 0.5 - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(lookY - ey, Math.sqrt(dx * dx + dz * dz)));
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
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
        }
    }
}
